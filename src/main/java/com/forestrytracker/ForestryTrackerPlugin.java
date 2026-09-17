package com.forestrytracker;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
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

	@Inject
	@Getter
	private ItemManager itemManager;

	@Getter
	private ForestrySession session;

	@Getter
	private LifetimeStats lifetime = new LifetimeStats();

	private ForestryTrackerPanel panel;
	private NavigationButton navButton;
	private Timer panelTimer;

	/** Woodcutting XP at the last {@link StatChanged}; -1 means "not yet known" (just logged in). */
	private long lastWcXp = -1;

	/** Set on any change since the last {@link #savePersisted()}; flushed at most once per game tick. */
	private boolean dirty;

	/** Live NPCs / GameObjects per event; an event is over when its set stays empty for a tick. */
	private final Map<ForestryEvent, Set<Object>> liveEntities = new EnumMap<>(ForestryEvent.class);
	private final Set<ForestryEvent> pendingEnd = EnumSet.noneOf(ForestryEvent.class);

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
		lastWcXp = -1;
		dirty = false;
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

	public void resetLifetime()
	{
		lifetime = new LifetimeStats();
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
			resetLifetime();
			configManager.setConfiguration(ForestryTrackerConfig.GROUP, ForestryTrackerConfig.RESET_LIFETIME_KEY, false);
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
				// The next StatChanged after login is only a baseline, not a gain.
				lastWcXp = -1;
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
		if (handleChatMessage(event.getType(), event.getMessage(), this::activeSession, liveEntities, lifetime, today()))
		{
			markDirty();
			refreshPanel();
		}
	}

	/**
	 * The chat-parsing/session-update glue behind {@link #onChatMessage(ChatMessage)}, extracted so
	 * it can be unit tested without a full RuneLite {@code Client}/Guice context: only pure
	 * collaborators ({@link ForestrySession}, {@link LifetimeStats}, a plain live-entities map) are
	 * needed. {@code sessionSupplier} is only invoked once a message actually needs a session (bark,
	 * leaves, or a log), matching {@link #activeSession()}'s original call sites so an unrelated chat
	 * line never creates or touches a session.
	 *
	 * @return true if session or lifetime state changed and the caller should persist/refresh
	 */
	static boolean handleChatMessage(ChatMessageType type, String message, Supplier<ForestrySession> sessionSupplier,
		Map<ForestryEvent, Set<Object>> liveEntities, LifetimeStats lifetime, String today)
	{
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX)
		{
			return false;
		}

		int barkAmount = ChatParser.parseBark(message);
		if (barkAmount >= 0)
		{
			ForestrySession s = sessionSupplier.get();
			if (s.getCurrentEvent() == null)
			{
				// The player may have stopped participating (rule 1 below) but the event's entities
				// (and thus the event itself, from the player's point of view) are still around;
				// reopen it so this bark attaches to it instead of creating an "Unknown event" or
				// relying on the 10-second grace window.
				ForestryEvent lastEvent = s.getLastEvent();
				if (lastEvent != null && !liveEntities.get(lastEvent).isEmpty() && s.isLastRecord(lastEvent))
				{
					s.resumeLastEvent();
					log.debug("Forestry event resumed: {}", lastEvent);
				}
			}
			s.addBark(barkAmount);
			lifetime.addBark(barkAmount, s.getCurrentEvent() != null ? s.getCurrentEvent() : s.getLastEvent(), today);
			log.debug("Bark awarded: {} (event {}, session total {})", barkAmount, s.getLastEvent(), s.getTotalBark());
			return true;
		}

		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE)
		{
			return false;
		}

		LeafType leafType = ChatParser.parseLeaves(message);
		if (leafType != null)
		{
			sessionSupplier.get().addLeaves(leafType, 1);
			lifetime.addLeaves(leafType, 1, today);
			log.debug("Leaf collected: {}", leafType);
			return true;
		}

		String logType = ChatParser.parseLog(message);
		if (logType != null)
		{
			ForestrySession s = sessionSupplier.get();
			ForestryEvent current = s.getCurrentEvent();
			if (current != null && s.getCurrentEventBark() > 0)
			{
				// The player went back to chopping after collecting bark from this event: end their
				// participation now rather than waiting for the (possibly much later) despawn of its
				// entities. Events with no bark yet (e.g. a Leprechaun, or one the player never
				// engaged with) are left running.
				s.endEvent();
				log.debug("Forestry event ended: {} (player resumed chopping)", current);
			}
			s.addLog(logType);
			lifetime.addLog(logType, today);
			log.debug("Log cut: {}", logType);
			return true;
		}

		return false;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.WOODCUTTING)
		{
			return;
		}

		long xp = event.getXp();
		if (lastWcXp < 0)
		{
			// First reading after login (or plugin start): establish the baseline only.
			lastWcXp = xp;
			return;
		}

		long delta = xp - lastWcXp;
		lastWcXp = xp;
		if (delta > 0)
		{
			activeSession().addXp(delta);
			lifetime.addXp(delta, today());
			markDirty();
			refreshPanel();
		}
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
	public void onGameTick(GameTick tick)
	{
		// Events whose entities all despawned last tick and did not come back are over.
		if (!pendingEnd.isEmpty())
		{
			for (ForestryEvent event : pendingEnd)
			{
				if (liveEntities.get(event).isEmpty() && session != null && session.getCurrentEvent() == event)
				{
					session.endEvent();
					log.debug("Forestry event ended: {}", event);
					markDirty();
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
				markDirty();
				refreshPanel();
			}
		}

		// Coalesce persistence: at most one profile-config write per tick, regardless of how many
		// of the handlers above fired this tick.
		if (dirty)
		{
			savePersisted();
		}
	}

	private void entitySpawned(ForestryEvent event, Object entity)
	{
		Set<Object> set = liveEntities.get(event);
		boolean wasEmpty = set.isEmpty();
		set.add(entity);
		pendingEnd.remove(event);

		if (!wasEmpty)
		{
			// Another entity of an event that's already ongoing (e.g. a new glowing root while
			// others are still up). This is not a new occurrence, even if rule 1 already ended the
			// player's participation in it (leaving currentEvent null while the set stays non-empty):
			// only the transition from no live entities to one starts a new event.
			return;
		}

		ForestrySession s = activeSession();
		if (s.getCurrentEvent() != event)
		{
			if (startOrResumeEvent(s, event))
			{
				lifetime.addEvent(event, today());
				log.debug("Forestry event started: {}", event);
			}
			else
			{
				log.debug("Forestry event resumed: {}", event);
			}
			markDirty();
			refreshPanel();
		}
	}

	/**
	 * Decides whether a newly-live entity for {@code event} (the transition from no live entities to
	 * one, per {@link #entitySpawned(ForestryEvent, Object)}) is a genuinely new occurrence or the
	 * continuation of one whose participation had already ended - by the log-cut rule in {@link
	 * #handleChatMessage}, or a despawn/respawn that happened to straddle a tick boundary rather than
	 * landing within the same tick (only the latter is covered by the {@code pendingEnd} add/remove
	 * dance in {@link #entitySpawned}/{@link #entityDespawned}). Mirrors the {@code
	 * resumeLastEvent()}/{@code isLastRecord()} check {@link #handleChatMessage} already does for
	 * bark awards, so an entity-spawn reopen merges back into the same history record instead of
	 * bumping {@code eventsSeen} and splitting the occurrence across two records.
	 *
	 * <p>Extracted (package-private, static) so this decision is unit testable directly against a
	 * {@link ForestrySession} without a RuneLite {@code Client}/Guice context.
	 *
	 * @return true if a new event was started; false if the last completed record was reopened instead
	 */
	static boolean startOrResumeEvent(ForestrySession s, ForestryEvent event)
	{
		if (s.getCurrentEvent() == null && event.equals(s.getLastEvent()) && s.isLastRecord(event))
		{
			s.resumeLastEvent();
			return false;
		}

		s.startEvent(event);
		return true;
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

	private void markDirty()
	{
		dirty = true;
	}

	private static String today()
	{
		return LocalDate.now().toString();
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
				loaded.addBark(legacy, null, null);
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
		if (restored != null && restored.isActive())
		{
			// A session persisted as active is never re-touched while the client is closed, so a
			// stale lastActivity here means it actually timed out (e.g. the player logged out, or
			// the client crashed, mid-session) and must not silently resume with a days-old start.
			Duration timeout = Duration.ofMinutes(config.statTimeout());
			if (Duration.between(restored.getLastActivity(), Instant.now()).compareTo(timeout) >= 0)
			{
				restored.setActive(false);
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
		dirty = false;
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
