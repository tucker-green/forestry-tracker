package com.forestrytracker;

import com.google.gson.Gson;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class SessionStateTest
{
	private static final Gson GSON = new Gson();

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
	public void lifetimeStatsRoundTripThroughJson()
	{
		LifetimeStats stats = new LifetimeStats();
		stats.addEvent(ForestryEvent.POACHERS);
		stats.addEvent(ForestryEvent.POACHERS);
		stats.addBark(11, ForestryEvent.POACHERS);
		stats.addBark(3, null);
		stats.addLeaves(LeafType.YEW, 9);

		LifetimeStats restored = GSON.fromJson(GSON.toJson(stats), LifetimeStats.class).normalize();
		assertEquals(14, restored.getBark());
		assertEquals(2, restored.getEventsSeen());
		assertEquals(2, restored.getEventCount(ForestryEvent.POACHERS));
		assertEquals(11, restored.getBarkForEvent(ForestryEvent.POACHERS));
		assertEquals(9, restored.getLeaves(LeafType.YEW));
		assertEquals(9, restored.getTotalLeaves());
	}

	@Test
	public void emptyLifetimeJsonIsSafe()
	{
		LifetimeStats restored = GSON.fromJson("{}", LifetimeStats.class).normalize();
		assertEquals(0, restored.getBark());
		assertEquals(0, restored.getEventCount(ForestryEvent.LEPRECHAUN));
	}
}
