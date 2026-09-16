package com.forestrytracker;

import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}
