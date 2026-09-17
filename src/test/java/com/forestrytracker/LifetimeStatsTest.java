package com.forestrytracker;

import java.time.LocalDate;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LifetimeStatsTest
{
	@Test
	public void perDayFiguresAccumulateIndependently()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addBark(5, ForestryEvent.RISING_ROOTS, "2026-09-01");
		stats.addBark(3, ForestryEvent.RISING_ROOTS, "2026-09-02");
		stats.addEvent(ForestryEvent.RISING_ROOTS, "2026-09-01");
		stats.addEvent(ForestryEvent.BEE_HIVE, "2026-09-02");
		stats.addLeaves(LeafType.OAK, 4, "2026-09-01");
		stats.addLog("Oak logs", "2026-09-02");
		stats.addLog("Oak logs", "2026-09-02");
		stats.addXp(100L, "2026-09-01");
		stats.addXp(200L, "2026-09-02");

		assertEquals(8, stats.getBark());
		assertEquals(2, stats.getEventsSeen());
		assertEquals(2, stats.getLogs());
		assertEquals(300L, stats.getXp());

		Map<String, DayStats> days = stats.getDays();
		assertEquals(2, days.size());

		DayStats day1 = days.get("2026-09-01");
		assertEquals(5, day1.bark);
		assertEquals(1, day1.events);
		assertEquals(4, day1.leaves);
		assertEquals(0, day1.logs);
		assertEquals(100L, day1.xp);

		DayStats day2 = days.get("2026-09-02");
		assertEquals(3, day2.bark);
		assertEquals(1, day2.events);
		assertEquals(2, day2.logs);
		assertEquals(200L, day2.xp);
	}

	@Test
	public void zeroAndNegativeAmountsAreIgnoredPerDay()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addLeaves(LeafType.YEW, 0, "2026-09-01");
		stats.addLeaves(LeafType.YEW, -3, "2026-09-01");
		stats.addXp(0L, "2026-09-01");
		stats.addXp(-5L, "2026-09-01");

		assertEquals(0, stats.getTotalLeaves());
		assertEquals(0L, stats.getXp());
		assertTrue(stats.getDays().isEmpty());
	}

	@Test
	public void daysAreKeptOldestFirst()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addLog("Logs", "2026-09-03");
		stats.addLog("Logs", "2026-09-01");
		stats.addLog("Logs", "2026-09-02");

		assertEquals("[2026-09-01, 2026-09-02, 2026-09-03]", stats.getDays().keySet().toString());
	}

	@Test
	public void oldestDaysAreEvictedBeyondMaxDays()
	{
		LifetimeStats stats = new LifetimeStats();
		LocalDate start = LocalDate.parse("2026-01-01");
		int totalDays = LifetimeStats.MAX_DAYS + 5;
		for (int i = 0; i < totalDays; i++)
		{
			stats.addLog("Logs", start.plusDays(i).toString());
		}

		Map<String, DayStats> days = stats.getDays();
		assertEquals(LifetimeStats.MAX_DAYS, days.size());

		// The 5 oldest days were evicted as the cap was exceeded.
		for (int i = 0; i < 5; i++)
		{
			assertFalse(days.containsKey(start.plusDays(i).toString()));
		}
		assertTrue(days.containsKey(start.plusDays(5).toString()));
		assertTrue(days.containsKey(start.plusDays(totalDays - 1).toString()));

		// Lifetime totals are unaffected by day-bucket eviction.
		assertEquals(totalDays, stats.getLogs());
	}
}
