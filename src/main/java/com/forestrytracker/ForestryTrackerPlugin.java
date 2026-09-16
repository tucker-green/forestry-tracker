package com.forestrytracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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
	/** Same message the core Woodcutting plugin parses. */
	private static final Pattern ANIMA_BARK_PATTERN = Pattern.compile("You've been awarded <col=[0-9a-f]+>(\\d+) Anima-infused bark</col>\\.");

	private static final int PANEL_REFRESH_MS = 1000;

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

	@Getter
	private ForestrySession session;

	@Getter
	private int lifetimeBark;

	private ForestryTrackerPanel panel;
	private NavigationButton navButton;
	private Timer panelTimer;

	/** Live NPCs / GameObjects per event; an event is over when its set stays empty for a tick. */
	private final Map<ForestryEvent, Set<Object>> liveEntities = new EnumMap<>(ForestryEvent.class);
	private final Set<ForestryEvent> pendingEnd = EnumSet.noneOf(ForestryEvent.class);

	/** Last known leaf counts per tracked container (inventory, forestry kit). */
	private final Map<Integer, Map<LeafType, Integer>> leafSnapshots = new java.util.HashMap<>();
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
			loadLifetimeBark();
		}
	}

	@Override
	protected void shutDown()
	{
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
		clearLiveEntities();
		leafSnapshots.clear();
		pendingLeaves.clear();
	}

	@Provides
	ForestryTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ForestryTrackerConfig.class);
	}

	public void resetSession()
	{
		session = null;
		clearLiveEntities();
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
			lifetimeBark = 0;
			saveLifetimeBark();
			configManager.setConfiguration(ForestryTrackerConfig.GROUP, ForestryTrackerConfig.RESET_LIFETIME_KEY, false);
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				loadLifetimeBark();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
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
		if (!matcher.matches())
		{
			return;
		}

		int amount = Integer.parseInt(matcher.group(1));
		activeSession().addBark(amount);
		lifetimeBark += amount;
		saveLifetimeBark();
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
				for (Map.Entry<LeafType, Integer> e : pendingLeaves.entrySet())
				{
					if (e.getValue() > 0)
					{
						activeSession().addLeaves(e.getKey(), e.getValue());
					}
				}
				refreshPanel();
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
			log.debug("Forestry event started: {}", event);
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

	private void loadLifetimeBark()
	{
		Integer stored = configManager.getRSProfileConfiguration(ForestryTrackerConfig.GROUP, ForestryTrackerConfig.LIFETIME_BARK_KEY, Integer.class);
		lifetimeBark = stored == null ? 0 : stored;
		refreshPanel();
	}

	private void saveLifetimeBark()
	{
		configManager.setRSProfileConfiguration(ForestryTrackerConfig.GROUP, ForestryTrackerConfig.LIFETIME_BARK_KEY, lifetimeBark);
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
