package com.forestrytracker;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LeafDiffTest
{
	@Test
	public void countIgnoresNonLeafItems()
	{
		Map<LeafType, Integer> counts = LeafDiff.count(
			new int[]{ItemID.LEAVES_OAK, ItemID.FORESTRY_CURRENCY, ItemID.LEAVES_OAK, ItemID.LEAVES_MAGIC, -1},
			new int[]{3, 500, 2, 1, 0});

		assertEquals(5, (int) counts.get(LeafType.OAK));
		assertEquals(1, (int) counts.get(LeafType.MAGIC));
		assertEquals(2, counts.size());
	}

	@Test
	public void deltaReportsOnlyChangedTypes()
	{
		Map<LeafType, Integer> prev = new EnumMap<>(LeafType.class);
		prev.put(LeafType.OAK, 3);
		prev.put(LeafType.YEW, 2);
		Map<LeafType, Integer> next = new EnumMap<>(LeafType.class);
		next.put(LeafType.OAK, 5);
		next.put(LeafType.WILLOW, 1);

		Map<LeafType, Integer> delta = LeafDiff.delta(prev, next);
		assertEquals(2, (int) delta.get(LeafType.OAK));
		assertEquals(1, (int) delta.get(LeafType.WILLOW));
		assertEquals(-2, (int) delta.get(LeafType.YEW));
		assertEquals(3, delta.size());
	}

	@Test
	public void movingLeavesBetweenContainersNetsToZero()
	{
		Map<LeafType, Integer> kitBefore = new EnumMap<>(LeafType.class);
		kitBefore.put(LeafType.MAPLE, 10);
		Map<LeafType, Integer> kitAfter = new EnumMap<>(LeafType.class);
		kitAfter.put(LeafType.MAPLE, 4);

		Map<LeafType, Integer> invBefore = new EnumMap<>(LeafType.class);
		Map<LeafType, Integer> invAfter = new EnumMap<>(LeafType.class);
		invAfter.put(LeafType.MAPLE, 6);

		Map<LeafType, Integer> pending = new EnumMap<>(LeafType.class);
		LeafDiff.accumulate(pending, LeafDiff.delta(kitBefore, kitAfter));
		LeafDiff.accumulate(pending, LeafDiff.delta(invBefore, invAfter));

		assertEquals(0, (int) pending.get(LeafType.MAPLE));
	}

	@Test
	public void emptyDeltaForIdenticalSnapshots()
	{
		Map<LeafType, Integer> a = new EnumMap<>(LeafType.class);
		a.put(LeafType.NORMAL, 7);
		assertTrue(LeafDiff.delta(a, new EnumMap<>(a)).isEmpty());
	}

	@Test
	public void resolveCreditsAnImmediateGainWithNothingHeld()
	{
		Map<LeafType, int[]> held = new EnumMap<>(LeafType.class);
		Map<LeafType, Integer> tick = new EnumMap<>(LeafType.class);
		tick.put(LeafType.OAK, 5);

		Map<LeafType, Integer> gains = LeafDiff.resolve(tick, held, 2);

		assertEquals(5, (int) gains.get(LeafType.OAK));
		assertTrue(held.isEmpty());
	}

	@Test
	public void resolveHoldsALossAwaitingAnOffsettingGainNextTick()
	{
		Map<LeafType, int[]> held = new EnumMap<>(LeafType.class);

		// Tick 1: the kit's -5 lands, the inventory's +5 hasn't arrived yet.
		Map<LeafType, Integer> tick1 = new EnumMap<>(LeafType.class);
		tick1.put(LeafType.OAK, -5);
		Map<LeafType, Integer> gains1 = LeafDiff.resolve(tick1, held, 2);
		assertTrue("a bare loss must not be credited as a gain", gains1.isEmpty());
		assertTrue(held.containsKey(LeafType.OAK));

		// Tick 2: the inventory's +5 lands a tick late and must cancel the held loss, not be
		// counted as a fresh 5-leaf gain.
		Map<LeafType, Integer> tick2 = new EnumMap<>(LeafType.class);
		tick2.put(LeafType.OAK, 5);
		Map<LeafType, Integer> gains2 = LeafDiff.resolve(tick2, held, 2);
		assertTrue("the straddling transfer must net to zero, not a phantom gain", gains2.isEmpty());
		assertTrue(held.isEmpty());
	}

	@Test
	public void resolveExpiresAnUnoffsetLossAfterTheGraceWindow()
	{
		Map<LeafType, int[]> held = new EnumMap<>(LeafType.class);
		Map<LeafType, Integer> lossTick = new EnumMap<>(LeafType.class);
		lossTick.put(LeafType.OAK, -5);

		LeafDiff.resolve(lossTick, held, 2);
		assertTrue(held.containsKey(LeafType.OAK));

		// Two idle ticks with no offsetting gain: the loss is given up on (never counted either way).
		Map<LeafType, Integer> empty = new EnumMap<>(LeafType.class);
		Map<LeafType, Integer> g1 = LeafDiff.resolve(empty, held, 2);
		assertTrue(g1.isEmpty());
		Map<LeafType, Integer> g2 = LeafDiff.resolve(empty, held, 2);
		assertTrue(g2.isEmpty());
		assertTrue("the loss must expire instead of being held forever", held.isEmpty());
	}

	@Test
	public void resolveNetsAPartialOffsetAcrossMultipleTicks()
	{
		Map<LeafType, int[]> held = new EnumMap<>(LeafType.class);

		Map<LeafType, Integer> tick1 = new EnumMap<>(LeafType.class);
		tick1.put(LeafType.MAPLE, -5);
		assertTrue(LeafDiff.resolve(tick1, held, 2).isEmpty());

		Map<LeafType, Integer> tick2 = new EnumMap<>(LeafType.class);
		tick2.put(LeafType.MAPLE, 3);
		assertTrue(LeafDiff.resolve(tick2, held, 2).isEmpty());
		assertEquals(-2, held.get(LeafType.MAPLE)[0]);

		Map<LeafType, Integer> tick3 = new EnumMap<>(LeafType.class);
		tick3.put(LeafType.MAPLE, 2);
		assertTrue(LeafDiff.resolve(tick3, held, 2).isEmpty());
		assertTrue(held.isEmpty());
	}
}
