package com.forestrytracker;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the chat-handling and entity-spawn glue in {@link ForestryTrackerPlugin}: {@link
 * ForestryTrackerPlugin#handleChatMessage} (the body of {@code onChatMessage}) and {@link
 * ForestryTrackerPlugin#startOrResumeEvent} (the start-vs-resume decision in {@code
 * entitySpawned}) were extracted as package-private, RuneLite-{@code Client}-free helpers
 * specifically so this glue can be exercised directly, the way {@link ForestrySessionTest} and
 * {@link ChatParserTest} already exercise the pure model and pure parser layers.
 *
 * <p>{@link ForestryTrackerPluginTest} (despite its name) is a manual dev-mode launcher used by the
 * {@code run}/{@code shadowJar} Gradle tasks, not a JUnit test; this class is the actual coverage
 * for the plugin-level wiring.
 */
public class ForestryTrackerPluginChatTest
{
	private static final String DAY = "2026-09-17";

	private Instant now;
	private ForestrySession session;
	private LifetimeStats lifetime;
	private Map<ForestryEvent, Set<Object>> liveEntities;

	@Before
	public void setUp()
	{
		now = Instant.parse("2026-09-17T08:18:17Z");
		session = new ForestrySession(() -> now);
		lifetime = new LifetimeStats();
		liveEntities = new EnumMap<>(ForestryEvent.class);
		for (ForestryEvent event : ForestryEvent.values())
		{
			liveEntities.put(event, new HashSet<>());
		}
	}

	private boolean handle(ChatMessageType type, String message)
	{
		return ForestryTrackerPlugin.handleChatMessage(type, message, () -> session, liveEntities, lifetime, DAY);
	}

	// --- real captured messages, end-to-end through the plugin glue (not just ChatParser in isolation) ----------

	@Test
	public void spamBarkAwardsAccumulateIntoSessionAndLifetime()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);

		assertTrue(handle(ChatMessageType.SPAM, "You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));
		assertTrue(handle(ChatMessageType.SPAM, "You've been awarded @mes_hl_blu@21 Anima-infused bark</col>."));
		assertTrue(handle(ChatMessageType.GAMEMESSAGE, "You've been awarded @mes_hl_blu@143 Anima-infused bark</col>."));

		assertEquals(175, session.getTotalBark());
		assertEquals(175, session.getBarkForEvent(ForestryEvent.RISING_ROOTS));
		assertEquals(175, lifetime.getBark());
		assertEquals(175, lifetime.getBarkForEvent(ForestryEvent.RISING_ROOTS));
	}

	@Test
	public void legacyColTagBarkFormatStillWorks()
	{
		assertTrue(handle(ChatMessageType.GAMEMESSAGE, "You've been awarded <col=0000ff>11 Anima-infused bark</col>."));

		assertEquals(11, session.getTotalBark());
	}

	@Test
	public void spamWillowLeafPickupIsCounted()
	{
		assertTrue(handle(ChatMessageType.SPAM,
			"Some willow leaves fall to the ground and you place them into your Forestry kit."));

		assertEquals(1, session.getLeaves(LeafType.WILLOW));
		assertEquals(1, lifetime.getLeaves(LeafType.WILLOW));
	}

	@Test
	public void spamWillowLogMessageIsCountedAsALog()
	{
		assertTrue(handle(ChatMessageType.SPAM, "You get some willow logs."));

		assertEquals(1, session.getLogsCut());
		assertEquals(1, lifetime.getLogs());
	}

	@Test
	public void ritualCompletionGameMessageIsIgnored()
	{
		assertFalse(handle(ChatMessageType.GAMEMESSAGE,
			"@mes_hl_blu@Well done, you completed 8 steps of the ritual! The Dryad has left you a gift...</col>"));

		assertEquals(0, session.getTotalBark());
		assertEquals(0, session.getTotalLeaves());
		assertEquals(0, session.getLogsCut());
	}

	@Test
	public void nonForestryChatTypeIsIgnoredEvenForABarkShapedMessage()
	{
		assertFalse(handle(ChatMessageType.PUBLICCHAT, "You've been awarded 11 Anima-infused bark."));

		assertEquals(0, session.getTotalBark());
	}

	@Test
	public void mesboxLeafMessageIsIgnored()
	{
		// Leaf/log messages are only ever SPAM/GAMEMESSAGE in game, unlike bark awards which can also
		// arrive as MESBOX; onChatMessage's second type filter (mirrored in handleChatMessage) reflects that.
		assertFalse(handle(ChatMessageType.MESBOX,
			"Some willow leaves fall to the ground and you place them into your Forestry kit."));

		assertEquals(0, session.getTotalLeaves());
	}

	// --- bark reopens an event ended early (rule 1 / resumeLastEvent), per handleChatMessage --------------------

	@Test
	public void barkAfterLogCutEndedEventReopensItInsteadOfStartingUnknownEvent()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(5);
		// The root object is still alive in the world even though the plugin ended participation below.
		liveEntities.get(ForestryEvent.RISING_ROOTS).add(new Object());

		session.endEvent(); // what onChatMessage's log-cut rule already did
		assertNull(session.getCurrentEvent());
		assertEquals(1, session.getHistory().size());

		assertTrue(handle(ChatMessageType.SPAM, "You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));

		assertEquals(ForestryEvent.RISING_ROOTS, session.getCurrentEvent());
		assertEquals(16, session.getTotalBark());
		assertEquals(1, session.getEventsSeen());
		assertEquals(1, session.getEventCount(ForestryEvent.RISING_ROOTS));
		assertTrue("bark should merge back into the reopened event, not sit in history", session.getHistory().isEmpty());
	}

	@Test
	public void barkWithNoLiveEntitiesForLastEventFallsBackToGraceWindowAttributionInstead()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(5);
		session.endEvent();
		// liveEntities never populated for RISING_ROOTS (it fully despawned) -> nothing to reopen.

		assertTrue(handle(ChatMessageType.SPAM, "You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));

		assertNull(session.getCurrentEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(16, session.getHistory().get(0).getBark());
	}

	// --- log-cut ends participation, but only once bark has been awarded (rule 1) -------------------------------

	@Test
	public void logCutEndsEventParticipationOnceBarkAwarded()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(5);

		assertTrue(handle(ChatMessageType.SPAM, "You get some willow logs."));

		assertNull(session.getCurrentEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(5, session.getHistory().get(0).getBark());
		assertEquals(1, session.getLogsCut());
	}

	@Test
	public void logCutDoesNotEndEventWithNoBarkYet()
	{
		session.startEvent(ForestryEvent.LEPRECHAUN);

		assertTrue(handle(ChatMessageType.SPAM, "You get some willow logs."));

		assertEquals(ForestryEvent.LEPRECHAUN, session.getCurrentEvent());
		assertTrue(session.getHistory().isEmpty());
		assertEquals(1, session.getLogsCut());
	}

	@Test
	public void logCutWithNoCurrentEventJustCountsTheLog()
	{
		assertTrue(handle(ChatMessageType.SPAM, "You get some logs."));

		assertNull(session.getCurrentEvent());
		assertEquals(1, session.getLogsCut());
		assertEquals(1, lifetime.getLogs());
	}

	// --- entitySpawned's start-vs-resume decision (startOrResumeEvent) -------------------------------------------

	@Test
	public void newGlowingRootAfterEarlyEndResumesInsteadOfStartingASecondEvent()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(5);
		session.endEvent();
		assertEquals(1, session.getEventsSeen());
		assertEquals(1, session.getHistory().size());

		boolean startedNew = ForestryTrackerPlugin.startOrResumeEvent(session, ForestryEvent.RISING_ROOTS);

		assertFalse(startedNew);
		assertEquals(ForestryEvent.RISING_ROOTS, session.getCurrentEvent());
		assertEquals(1, session.getEventsSeen());
		assertEquals(5, session.getCurrentEventBark());
		assertTrue(session.getHistory().isEmpty());
	}

	@Test
	public void spawnWithNoPriorHistoryStartsANewEvent()
	{
		boolean startedNew = ForestryTrackerPlugin.startOrResumeEvent(session, ForestryEvent.RISING_ROOTS);

		assertTrue(startedNew);
		assertEquals(1, session.getEventsSeen());
		assertEquals(ForestryEvent.RISING_ROOTS, session.getCurrentEvent());
	}

	@Test
	public void spawnOfADifferentEventThanTheLastHistoryRecordStartsANewEvent()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.endEvent();

		boolean startedNew = ForestryTrackerPlugin.startOrResumeEvent(session, ForestryEvent.LEPRECHAUN);

		assertTrue(startedNew);
		assertEquals(2, session.getEventsSeen());
		assertEquals(ForestryEvent.LEPRECHAUN, session.getCurrentEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(ForestryEvent.RISING_ROOTS, session.getHistory().get(0).getEvent());
	}

	@Test
	public void spawnWhileAnotherEventIsCurrentEndsItAndStartsTheNewOne()
	{
		session.startEvent(ForestryEvent.LEPRECHAUN);

		boolean startedNew = ForestryTrackerPlugin.startOrResumeEvent(session, ForestryEvent.RISING_ROOTS);

		assertTrue(startedNew);
		assertEquals(2, session.getEventsSeen());
		assertEquals(ForestryEvent.RISING_ROOTS, session.getCurrentEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(ForestryEvent.LEPRECHAUN, session.getHistory().get(0).getEvent());
	}
}
