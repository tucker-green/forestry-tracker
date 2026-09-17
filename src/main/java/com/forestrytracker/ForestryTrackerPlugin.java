package com.forestrytracker;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Forestry Tracker",
	description = "Tracks Forestry events, anima-infused bark per hour, bark per event, and leaves",
	tags = {"forestry", "woodcutting", "bark", "leaves", "skilling", "overlay"}
)
public class ForestryTrackerPlugin extends Plugin
{
	/**
	 * Bark award message. The core Woodcutting plugin matches
	 * "You've been awarded <col=..>N Anima-infused bark</col>."; this is slightly looser about the colour tags.
	 */
	private static final Pattern ANIMA_BARK_PATTERN = Pattern.compile(
		"You(?:'ve| have) been awarded (?:<col=[0-9a-f]+>)?(\\d+) Anima-infused bark(?:</col>)?\\.?",
		Pattern.CASE_INSENSITIVE);

	private static final int PANEL_REFRESH_MS = 1000;

	/** Profile-config keys (per RuneScape character). */
	private static final String SESSION_KEY = "session";
	private static final String LIFETIME_KEY = "lifetime";
	/** Key used by 1.0.0 before per-event lifetime stats existed; migrated on load. */
	private static final String LEGACY_LIFETIME_BARK_KEY = "lifetimeBark";

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ForestryTrackerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ForestryTrackerOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Getter
	private ForestrySession session;

	@Getter
	private LifetimeStats lifetime = new LifetimeStats();

	private ForestryTrackerPanel panel;
	private NavigationButton navButton;
	private Timer panelTimer;

	/** Live NPCs / GameObjects per event; an event is over when its set stays empty for a tick. */
	private final Map<ForestryEvent, Set<Object>> liveEntities = new EnumMap<>(ForestryEvent.class);
	private final Set<ForestryEvent> pendingEnd = EnumSet.noneOf(ForestryEvent.class);

	/** Last known leaf counts per tracked container (inventory, forestry kit). */
	private final Map<Integer, Map<LeafType, Integer>> leafSnapshots = new HashMap<>();
	private final Map<LeafType, Integer> pendingLeaves = new EnumMap<>(LeafType.class);
	private boolean bankChangedThisTick;

	@Override
	protected void startUp()
	{
		for (ForestryEvent event : ForestryEvent.values())
		{
			liveEntities.put(event, Collections.newSetFromMap(new IdentityHashMap<>()));
		}

		overlayManager.add(overlay);

		panel = new ForestryTrackerPanel(this);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Forestry Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		panelTimer = new Timer(PANEL_REFRESH_MS, e -> panel.refresh());
		panelTimer.start();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadPersisted();
		}
	}

	@Override
	protected void shutDown()
	{
		savePersisted();

		overlayManager.remove(overlay);
		if (panelTimer != null)
		{
			panelTimer.stop();
			panelTimer = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;

		session = null;
		lifetime = new LifetimeStats();
		clearLiveEntities();
		leafSnapshots.clear();
		pendingLeaves.clear();
	}

	@Provides
	ForestryTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ForestryTrackerConfig.class);
	}

	public int getLifetimeBark()
	{
		return lifetime.getBark();
	}

	public void resetSession()
	{
		session = null;
		clearLiveEntities();
		savePersisted();
		refreshPanel();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ForestryTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (ForestryTrackerConfig.RESET_LIFETIME_KEY.equals(event.getKey()) && config.resetLifetimeBark())
		{
			lifetime = new LifetimeStats();
			savePersisted();
			configManager.setConfiguration(ForestryTrackerConfig.GROUP, ForestryTrackerConfig.RESET_LIFETIME_KEY, false);
			refreshPanel();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Fired once the logged-in character is known; this is when per-character config becomes readable.
		loadPersisted();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				savePersisted();
				// Another character may log in next: forget container contents so we don't count their leaves.
				leafSnapshots.clear();
				pendingLeaves.clear();
				// fallthrough
			case LOADING:
				// Entities are re-spawned after a scene load; if the current event's entities don't come back
				// within a tick it is over.
				if (session != null && session.getCurrentEvent() != null)
				{
					pendingEnd.add(session.getCurrentEvent());
				}
				for (Set<Object> set : liveEntities.values())
				{
					set.clear();
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.MESBOX)
		{
			return;
		}

		Matcher matcher = ANIMA_BARK_PATTERN.matcher(event.getMessage());
		if (!matcher.find())
		{
			return;
		}

		int amount = Integer.parseInt(matcher.group(1));
		ForestrySession s = activeSession();
		s.addBark(amount);
		lifetime.addBark(amount, s.getCurrentEvent() != null ? s.getCurrentEvent() : s.getLastEvent());
		log.debug("Bark awarded: {} (event {}, session total {})", amount, s.getLastEvent(), s.getTotalBark());
		savePersisted();
		refreshPanel();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		ForestryEvent forestryEvent = ForestryEvent.fromNpcId(event.getNpc().getId());
		if (forestryEvent != null)
		{
			entitySpawned(forestryEvent, event.getNpc());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		ForestryEvent forestryEvent = ForestryEvent.fromNpcId(event.getNpc().getId());
		if (forestryEvent != null)
		{
			entityDespawned(forestryEvent, event.getNpc());
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		ForestryEvent forestryEvent = ForestryEvent.fromObjectId(event.getGameObject().getId());
		if (forestryEvent != null)
		{
			entitySpawned(forestryEvent, event.getGameObject());
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		ForestryEvent forestryEvent = ForestryEvent.fromObjectId(event.getGameObject().getId());
		if (forestryEvent != null)
		{
			entityDespawned(forestryEvent, event.getGameObject());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			bankChangedThisTick = true;
			return;
		}
		if (id != InventoryID.INV && id != InventoryID.FORESTRY_KIT)
		{
			return;
		}

		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}

		Item[] items = container.getItems();
		int[] ids = new int[items.length];
		int[] quantities = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i].getId();
			quantities[i] = items[i].getQuantity();
		}

		Map<LeafType, Integer> counts = LeafDiff.count(ids, quantities);
		Map<LeafType, Integer> previous = leafSnapshots.put(id, counts);
		if (previous == null)
		{
			// First sight of this container only seeds the snapshot.
			return;
		}

		LeafDiff.accumulate(pendingLeaves, LeafDiff.delta(previous, counts));
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Leaves: apply the net change across inventory + kit for this tick.
		if (!pendingLeaves.isEmpty())
		{
			if (!bankChangedThisTick)
			{
				boolean gained = false;
				for (Map.Entry<LeafType, Integer> e : pendingLeaves.entrySet())
				{
					if (e.getValue() > 0)
					{
						activeSession().addLeaves(e.getKey(), e.getValue());
						lifetime.addLeaves(e.getKey(), e.getValue());
						gained = true;
					}
				}
				if (gained)
				{
					log.debug("Leaves gained: {}", pendingLeaves);
					savePersisted();
					refreshPanel();
				}
			}
			pendingLeaves.clear();
		}
		bankChangedThisTick = false;

		// Events whose entities all despawned last tick and did not come back are over.
		if (!pendingEnd.isEmpty())
		{
			for (ForestryEvent event : pendingEnd)
			{
				if (liveEntities.get(event).isEmpty() && session != null && session.getCurrentEvent() == event)
				{
					session.endEvent();
					log.debug("Forestry event ended: {}", event);
					savePersisted();
					refreshPanel();
				}
			}
			pendingEnd.clear();
		}

		// Inactivity timeout.
		if (session != null && session.isActive())
		{
			Duration timeout = Duration.ofMinutes(config.statTimeout());
			if (session.getCurrentEvent() == null && session.getTimeSinceActivity().compareTo(timeout) >= 0)
			{
				session.setActive(false);
				savePersisted();
				refreshPanel();
			}
		}
	}

	private void entitySpawned(ForestryEvent event, Object entity)
	{
		Set<Object> set = liveEntities.get(event);
		set.add(entity);
		pendingEnd.remove(event);

		ForestrySession s = activeSession();
		if (s.getCurrentEvent() != event)
		{
			s.startEvent(event);
			lifetime.addEvent(event);
			log.debug("Forestry event started: {}", event);
			savePersisted();
			refreshPanel();
		}
	}

	private void entityDespawned(ForestryEvent event, Object entity)
	{
		Set<Object> set = liveEntities.get(event);
		if (set.remove(entity) && set.isEmpty())
		{
			// Defer: state changes (e.g. sapling -> withering sapling) despawn and respawn within the same tick.
			pendingEnd.add(event);
		}
	}

	/** Returns the current session, creating (or replacing a timed-out) one as configured. */
	private ForestrySession activeSession()
	{
		if (session == null || (!session.isActive() && config.resetOnTimeout()))
		{
			session = new ForestrySession();
			pendingEnd.clear();
		}
		else if (!session.isActive())
		{
			session.touch();
		}
		return session;
	}

	private void clearLiveEntities()
	{
		for (Set<Object> set : liveEntities.values())
		{
			set.clear();
		}
		pendingEnd.clear();
	}

	// --- persistence -------------------------------------------------------------------------------------------

	private boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	private void loadPersisted()
	{
		if (!hasProfile())
		{
			return;
		}

		LifetimeStats loaded = null;
		String lifetimeJson = configManager.getRSProfileConfiguration(ForestryTrackerConfig.GROUP, LIFETIME_KEY);
		if (lifetimeJson != null)
		{
			try
			{
				loaded = gson.fromJson(lifetimeJson, LifetimeStats.class);
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Discarding unreadable lifetime stats", e);
			}
		}
		if (loaded == null)
		{
			loaded = new LifetimeStats();
			// Migrate the 1.0.0 bark-only total.
			Integer legacy = configManager.getRSProfileConfiguration(ForestryTrackerConfig.GROUP, LEGACY_LIFETIME_BARK_KEY, Integer.class);
			if (legacy != null && legacy > 0)
			{
				loaded.addBark(legacy, null);
				configManager.unsetRSProfileConfiguration(ForestryTrackerConfig.GROUP, LEGACY_LIFETIME_BARK_KEY);
			}
		}
		lifetime = loaded.normalize();

		ForestrySession restored = null;
		String sessionJson = configManager.getRSProfileConfiguration(ForestryTrackerConfig.GROUP, SESSION_KEY);
		if (sessionJson != null)
		{
			try
			{
				SessionState state = gson.fromJson(sessionJson, SessionState.class);
				if (state != null)
				{
					restored = new ForestrySession(state);
				}
			}
			catch (RuntimeException e)
			{
				log.warn("Discarding unreadable session", e);
			}
		}
		session = restored;
		clearLiveEntities();

		log.debug("Loaded forestry stats: lifetime bark {}, session {}", lifetime.getBark(),
			session == null ? "none" : session.getTotalBark() + " bark");
		refreshPanel();
	}

	private void savePersisted()
	{
		if (!hasProfile())
		{
			return;
		}
		configManager.setRSProfileConfiguration(ForestryTrackerConfig.GROUP, LIFETIME_KEY, gson.toJson(lifetime));
		if (session == null)
		{
			configManager.unsetRSProfileConfiguration(ForestryTrackerConfig.GROUP, SESSION_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(ForestryTrackerConfig.GROUP, SESSION_KEY, gson.toJson(session.toState()));
		}
	}

	private void refreshPanel()
	{
		ForestryTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}
}
