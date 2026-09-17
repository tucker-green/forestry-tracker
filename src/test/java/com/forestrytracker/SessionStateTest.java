package com.forestrytracker;

import com.google.gson.Gson;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionStateTest
{
	private static final Gson GSON = new Gson();
	private static final String DAY = "2026-09-17";

	@Test
	public void sessionRoundTripsThroughJson()
	{
		Instant[] now = {Instant.parse("2026-09-16T12:00:00Z")};
		ForestrySession original = new ForestrySession(() -> now[0]);
		original.startEvent(ForestryEvent.RISING_ROOTS);
		original.addBark(5);
		now[0] = now[0].plus(Duration.ofMinutes(1));
		original.endEvent();
		original.addLeaves(LeafType.OAK, 4);
		original.addLeaves(LeafType.MAGIC, 1);
		original.startEvent(ForestryEvent.BEE_HIVE);
		original.addBark(7);
		original.setActive(false);

		String json = GSON.toJson(original.toState());
		SessionState state = GSON.fromJson(json, SessionState.class);
		ForestrySession restored = new ForestrySession(state, () -> now[0]);

		assertEquals(original.getStart(), restored.getStart());
		assertEquals(original.getLastActivity(), restored.getLastActivity());
		assertFalse(restored.isActive());
		assertEquals(12, restored.getTotalBark());
		assertEquals(7, restored.getLastEventBark());
		assertEquals(ForestryEvent.BEE_HIVE, restored.getLastEvent());
		assertEquals(2, restored.getEventsSeen());
		assertEquals(1, restored.getEventCount(ForestryEvent.RISING_ROOTS));
		assertEquals(1, restored.getEventCount(ForestryEvent.BEE_HIVE));
		assertEquals(5, restored.getBarkForEvent(ForestryEvent.RISING_ROOTS));
		assertEquals(7, restored.getBarkForEvent(ForestryEvent.BEE_HIVE));
		assertEquals(4, restored.getLeaves(LeafType.OAK));
		assertEquals(5, restored.getTotalLeaves());

		// The in-progress bee hive event is persisted as a finished history entry.
		assertNull(restored.getCurrentEvent());
		assertEquals(2, restored.getHistory().size());
		assertEquals(ForestryEvent.BEE_HIVE, restored.getHistory().get(0).getEvent());
		assertEquals(7, restored.getHistory().get(0).getBark());
		assertEquals(ForestryEvent.RISING_ROOTS, restored.getHistory().get(1).getEvent());
	}

	@Test
	public void sessionRoundTripCoversLogsXpAndTimeline()
	{
		Instant[] now = {Instant.parse("2026-09-16T12:00:00Z")};
		ForestrySession original = new ForestrySession(() -> now[0]);
		original.addLog("Oak logs");
		original.addLog("Oak logs");
		original.addLog("Logs");
		original.addXp(150L);
		now[0] = now[0].plus(Duration.ofMinutes(6));
		original.addLog("Willow logs");
		original.addXp(50L);

		String json = GSON.toJson(original.toState());
		SessionState state = GSON.fromJson(json, SessionState.class);
		ForestrySession restored = new ForestrySession(state, () -> now[0]);

		assertEquals(4, restored.getLogsCut());
		assertEquals(200L, restored.getXpGained());
		Map<String, Integer> byType = restored.getLogsByType();
		assertEquals(Integer.valueOf(2), byType.get("Oak logs"));
		assertEquals(Integer.valueOf(1), byType.get("Logs"));
		assertEquals(Integer.valueOf(1), byType.get("Willow logs"));

		List<TimeBucket> timeline = restored.getTimeline();
		assertEquals(2, timeline.size());
		assertEquals(3, timeline.get(0).logs);
		assertEquals(150L, timeline.get(0).xp);
		assertEquals(1, timeline.get(1).logs);
		assertEquals(50L, timeline.get(1).xp);
		assertEquals(original.getStart().toEpochMilli(), timeline.get(0).start);
		assertEquals(original.getStart().toEpochMilli() + ForestrySession.BUCKET.toMillis(), timeline.get(1).start);
	}

	@Test
	public void lifetimeStatsRoundTripThroughJson()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addEvent(ForestryEvent.POACHERS, DAY);
		stats.addEvent(ForestryEvent.POACHERS, DAY);
		stats.addBark(11, ForestryEvent.POACHERS, DAY);
		stats.addBark(3, null, DAY);
		stats.addLeaves(LeafType.YEW, 9, DAY);
		stats.addLog("Oak logs", DAY);
		stats.addLog("Oak logs", DAY);
		stats.addXp(500L, DAY);

		LifetimeStats restored = GSON.fromJson(GSON.toJson(stats), LifetimeStats.class).normalize();
		assertEquals(14, restored.getBark());
		assertEquals(2, restored.getEventsSeen());
		assertEquals(2, restored.getEventCount(ForestryEvent.POACHERS));
		assertEquals(11, restored.getBarkForEvent(ForestryEvent.POACHERS));
		assertEquals(9, restored.getLeaves(LeafType.YEW));
		assertEquals(9, restored.getTotalLeaves());
		assertEquals(2, restored.getLogs());
		assertEquals(Integer.valueOf(2), restored.getLogsByType().get("Oak logs"));
		assertEquals(500L, restored.getXp());

		Map<String, DayStats> days = restored.getDays();
		assertEquals(1, days.size());
		DayStats day = days.get(DAY);
		assertEquals(2, day.events);
		assertEquals(14, day.bark);
		assertEquals(9, day.leaves);
		assertEquals(2, day.logs);
		assertEquals(500L, day.xp);
	}

	@Test
	public void lifetimeStatsAccumulateAcrossMultipleDays()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addLog("Logs", "2026-09-15");
		stats.addLog("Logs", "2026-09-16");
		stats.addLog("Logs", "2026-09-16");
		stats.addXp(10L, "2026-09-15");
		stats.addXp(20L, "2026-09-16");

		assertEquals(3, stats.getLogs());
		assertEquals(30L, stats.getXp());

		Map<String, DayStats> days = stats.getDays();
		assertEquals(2, days.size());
		// Oldest day first.
		assertEquals("2026-09-15", days.keySet().iterator().next());
		assertEquals(1, days.get("2026-09-15").logs);
		assertEquals(2, days.get("2026-09-16").logs);
		assertEquals(10L, days.get("2026-09-15").xp);
		assertEquals(20L, days.get("2026-09-16").xp);
	}

	@Test
	public void emptyLifetimeJsonIsSafe()
	{
		LifetimeStats restored = GSON.fromJson("{}", LifetimeStats.class).normalize();
		assertEquals(0, restored.getBark());
		assertEquals(0, restored.getEventCount(ForestryEvent.LEPRECHAUN));
		assertEquals(0, restored.getLogs());
		assertEquals(0L, restored.getXp());
		assertTrue(restored.getDays().isEmpty());
	}
}
