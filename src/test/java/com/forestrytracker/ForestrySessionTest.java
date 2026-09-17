package com.forestrytracker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ForestrySessionTest
{
	private Instant now;
	private ForestrySession session;

	@Before
	public void setUp()
	{
		now = Instant.parse("2026-09-16T12:00:00Z");
		session = new ForestrySession(() -> now);
	}

	private void advance(Duration d)
	{
		now = now.plus(d);
	}

	@Test
	public void barkDuringEventAccumulatesIntoThatEvent()
	{
		session.startEvent(ForestryEvent.FLOWERING_TREE);
		session.addBark(2);
		session.addBark(1);
		session.addBark(2);

		assertEquals(5, session.getTotalBark());
		assertEquals(5, session.getLastEventBark());
		assertEquals(ForestryEvent.FLOWERING_TREE, session.getCurrentEvent());
		assertEquals(5, session.getBarkForEvent(ForestryEvent.FLOWERING_TREE));
		assertEquals(1, session.getEventCount(ForestryEvent.FLOWERING_TREE));

		session.endEvent();
		assertNull(session.getCurrentEvent());
		assertEquals(5, session.getLastEventBark());
		assertEquals(ForestryEvent.FLOWERING_TREE, session.getLastEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(5, session.getHistory().get(0).getBark());
	}

	@Test
	public void barkShortlyAfterEventEndsIsAttributedToIt()
	{
		session.startEvent(ForestryEvent.STRUGGLING_SAPLING);
		advance(Duration.ofSeconds(90));
		session.endEvent();
		advance(Duration.ofSeconds(2));
		session.addBark(12);

		assertEquals(12, session.getLastEventBark());
		assertEquals(ForestryEvent.STRUGGLING_SAPLING, session.getLastEvent());
		assertEquals(1, session.getHistory().size());
		assertEquals(12, session.getHistory().get(0).getBark());
		assertEquals(12, session.getBarkForEvent(ForestryEvent.STRUGGLING_SAPLING));
	}

	@Test
	public void barkWithNoEventIsRecordedStandalone()
	{
		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(4);
		session.endEvent();
		advance(ForestrySession.BARK_ATTRIBUTION_GRACE.plusSeconds(1));
		session.addBark(7);

		assertEquals(11, session.getTotalBark());
		assertEquals(7, session.getLastEventBark());
		assertNull(session.getLastEvent());
		assertEquals(2, session.getHistory().size());
		assertNull(session.getHistory().get(0).getEvent());
		assertEquals(7, session.getHistory().get(0).getBark());
		assertEquals("Unknown event", session.getHistory().get(0).getEventName());
	}

	@Test
	public void startingAnEventEndsThePreviousOne()
	{
		session.startEvent(ForestryEvent.BEE_HIVE);
		session.addBark(10);
		session.startEvent(ForestryEvent.POACHERS);

		assertEquals(ForestryEvent.POACHERS, session.getCurrentEvent());
		assertEquals(0, session.getCurrentEventBark());
		assertEquals(2, session.getEventsSeen());
		assertEquals(1, session.getHistory().size());
		assertEquals(ForestryEvent.BEE_HIVE, session.getHistory().get(0).getEvent());
		assertEquals(10, session.getHistory().get(0).getBark());
	}

	@Test
	public void barkPerHourUsesSessionElapsedTime()
	{
		assertEquals(0, session.getBarkPerHour());
		advance(Duration.ofMinutes(30));
		session.addBark(50);
		assertEquals(100, session.getBarkPerHour());
		advance(Duration.ofMinutes(30));
		assertEquals(50, session.getBarkPerHour());
	}

	@Test
	public void timeSinceLastEventPrefersEventStartOverBark()
	{
		assertNull(session.getTimeSinceLastEvent());

		session.addBark(3);
		advance(Duration.ofSeconds(20));
		assertEquals(Duration.ofSeconds(20), session.getTimeSinceLastEvent());

		session.startEvent(ForestryEvent.LEPRECHAUN);
		advance(Duration.ofSeconds(5));
		session.addBark(1);
		advance(Duration.ofSeconds(5));
		assertEquals(Duration.ofSeconds(10), session.getTimeSinceLastEvent());
	}

	@Test
	public void leavesAreSummedPerType()
	{
		session.addLeaves(LeafType.OAK, 3);
		session.addLeaves(LeafType.OAK, 2);
		session.addLeaves(LeafType.MAGIC, 1);
		session.addLeaves(LeafType.YEW, 0);
		session.addLeaves(LeafType.YEW, -4);

		assertEquals(5, session.getLeaves(LeafType.OAK));
		assertEquals(1, session.getLeaves(LeafType.MAGIC));
		assertEquals(0, session.getLeaves(LeafType.YEW));
		assertEquals(6, session.getTotalLeaves());
	}

	@Test
	public void activityTracking()
	{
		assertTrue(session.isActive());
		session.setActive(false);
		assertFalse(session.isActive());
		advance(Duration.ofMinutes(3));
		session.addBark(1);
		assertTrue(session.isActive());
		assertEquals(Duration.ZERO, session.getTimeSinceActivity());
	}

	@Test
	public void historyIsCapped()
	{
		for (int i = 0; i < 150; i++)
		{
			session.startEvent(ForestryEvent.RISING_ROOTS);
			session.endEvent();
		}
		assertEquals(100, session.getHistory().size());
		assertEquals(150, session.getEventsSeen());
	}

	@Test
	public void logsAreTrackedPerTypeAndTotal()
	{
		session.addLog("Oak logs");
		session.addLog("Oak logs");
		session.addLog("Logs");

		assertEquals(3, session.getLogsCut());
		assertEquals(Integer.valueOf(2), session.getLogsByType().get("Oak logs"));
		assertEquals(Integer.valueOf(1), session.getLogsByType().get("Logs"));

		advance(Duration.ofMinutes(30));
		assertEquals(6, session.getLogsPerHour());
	}

	@Test
	public void xpIsTrackedAndPerHourIsComputed()
	{
		advance(Duration.ofMinutes(30));
		session.addXp(500);
		assertEquals(500, session.getXpGained());
		assertEquals(1000, session.getXpPerHour());

		// Non-positive deltas (e.g. a stat recalculation) are ignored.
		session.addXp(0);
		session.addXp(-10);
		assertEquals(500, session.getXpGained());
	}

	@Test
	public void timelineBucketsActivityInFiveMinuteSlices()
	{
		session.addLog("Logs");
		advance(Duration.ofMinutes(5));
		session.addBark(3);
		advance(Duration.ofMinutes(5));
		session.addLeaves(LeafType.OAK, 2);
		advance(Duration.ofMinutes(1));
		session.addXp(20);

		List<TimeBucket> timeline = session.getTimeline();
		assertEquals(3, timeline.size());
		assertEquals(1, timeline.get(0).logs);
		assertEquals(0, timeline.get(1).logs);
		assertEquals(3, timeline.get(1).bark);
		assertEquals(2, timeline.get(2).leaves);
		assertEquals(20, timeline.get(2).xp);
		assertEquals(session.getStart().toEpochMilli(), timeline.get(0).start);
		assertEquals(session.getStart().toEpochMilli() + ForestrySession.BUCKET.toMillis(), timeline.get(1).start);
		assertEquals(session.getStart().toEpochMilli() + 2 * ForestrySession.BUCKET.toMillis(), timeline.get(2).start);
	}

	@Test
	public void durationFreezesAtLastActivityWhenInactive()
	{
		advance(Duration.ofMinutes(10));
		session.addBark(1);
		session.setActive(false);
		advance(Duration.ofMinutes(50));

		assertEquals(Duration.ofMinutes(10), session.getDuration());
	}

	@Test
	public void averageEventGapIsMeanTimeBetweenEventStarts()
	{
		assertNull(session.getAverageEventGap());

		session.startEvent(ForestryEvent.RISING_ROOTS);
		assertNull(session.getAverageEventGap());
		advance(Duration.ofMinutes(4));
		session.endEvent();

		advance(Duration.ofMinutes(6));
		session.startEvent(ForestryEvent.BEE_HIVE);
		assertEquals(Duration.ofMinutes(10), session.getAverageEventGap());

		advance(Duration.ofMinutes(5));
		session.endEvent();
		advance(Duration.ofMinutes(5));
		session.startEvent(ForestryEvent.POACHERS);
		assertEquals(Duration.ofMinutes(10), session.getAverageEventGap());
	}

	@Test
	public void bestEventIsTheHighestBarkCompletedEvent()
	{
		assertNull(session.getBestEvent());

		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.addBark(5);
		session.endEvent();
		session.startEvent(ForestryEvent.BEE_HIVE);
		session.addBark(20);
		session.endEvent();
		session.startEvent(ForestryEvent.POACHERS);
		session.addBark(9);
		session.endEvent();

		EventRecord best = session.getBestEvent();
		assertNotNull(best);
		assertEquals(ForestryEvent.BEE_HIVE, best.getEvent());
		assertEquals(20, best.getBark());
	}

	@Test
	public void reactivatingAfterTimeoutExcludesTheIdleGapFromDuration()
	{
		advance(Duration.ofMinutes(30));
		session.addLog("Oak logs");
		session.setActive(false);
		advance(Duration.ofHours(3));

		// Resuming (e.g. resetOnTimeout=false) must not let the 3-hour idle gap flood back in.
		session.touch();
		assertTrue(session.isActive());
		assertEquals(Duration.ofMinutes(30), session.getDuration());

		advance(Duration.ofMinutes(10));
		session.addLog("Oak logs");
		assertEquals(Duration.ofMinutes(40), session.getDuration());
		assertEquals(2, session.getLogsCut());
		// 2 logs over 40 real minutes -> 3/hr, not deflated by the idle gap.
		assertEquals(3, session.getLogsPerHour());
	}

	@Test
	public void repeatedTimeoutsAccumulateExcludedIdleTime()
	{
		session.addLog("Oak logs");
		session.setActive(false);
		advance(Duration.ofHours(1));
		session.touch();

		session.addLog("Oak logs");
		session.setActive(false);
		advance(Duration.ofHours(2));
		session.touch();

		assertEquals(Duration.ZERO, session.getDuration());
	}

	@Test
	public void timelineIsCappedWhenActivityResumesAfterAVeryLongGap()
	{
		session.addLog("Oak logs");
		// Simulate a stale session (e.g. restored after several real days) suddenly active again.
		advance(Duration.ofDays(5));
		session.addLog("Oak logs");

		List<TimeBucket> timeline = session.getTimeline();
		assertTrue("timeline must be bounded, was " + timeline.size(), timeline.size() <= 288);
		// The most recent slice (containing the log just cut) must still be present and correct.
		TimeBucket last = timeline.get(timeline.size() - 1);
		assertEquals(1, last.logs);
	}

	@Test
	public void eventsPerHourComputesRatePerHourWithOneDecimal()
	{
		assertEquals(0.0, session.getEventsPerHour(), 0.0001);

		session.startEvent(ForestryEvent.RISING_ROOTS);
		session.endEvent();
		session.startEvent(ForestryEvent.BEE_HIVE);
		session.endEvent();
		session.startEvent(ForestryEvent.POACHERS);
		session.endEvent();

		// 3 events within 20 minutes -> 9.0 per hour.
		advance(Duration.ofMinutes(20));
		assertEquals(9.0, session.getEventsPerHour(), 0.0001);
	}
}
