package com.forestrytracker;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure helpers for turning item-container snapshots into leaf gains.
 */
final class LeafDiff
{
	private LeafDiff()
	{
	}

	/** Counts leaves in a container given (itemId, quantity) pairs. */
	static Map<LeafType, Integer> count(int[] itemIds, int[] quantities)
	{
		Map<LeafType, Integer> counts = new EnumMap<>(LeafType.class);
		for (int i = 0; i < itemIds.length; i++)
		{
			LeafType type = LeafType.fromItemId(itemIds[i]);
			if (type != null && quantities[i] > 0)
			{
				counts.merge(type, quantities[i], Integer::sum);
			}
		}
		return counts;
	}

	/** Per-type change from {@code previous} to {@code next}; negative values mean leaves left the container. */
	static Map<LeafType, Integer> delta(Map<LeafType, Integer> previous, Map<LeafType, Integer> next)
	{
		Map<LeafType, Integer> delta = new EnumMap<>(LeafType.class);
		for (LeafType type : LeafType.values())
		{
			int change = next.getOrDefault(type, 0) - previous.getOrDefault(type, 0);
			if (change != 0)
			{
				delta.put(type, change);
			}
		}
		return delta;
	}

	/** Adds {@code delta} into {@code accumulator} in place. */
	static void accumulate(Map<LeafType, Integer> accumulator, Map<LeafType, Integer> delta)
	{
		for (Map.Entry<LeafType, Integer> e : delta.entrySet())
		{
			accumulator.merge(e.getKey(), e.getValue(), Integer::sum);
		}
	}

	/**
	 * Resolves one tick's container delta against unresolved losses carried over from recent
	 * prior ticks, and returns the per-type amounts to credit as gains this tick. A kit&lt;-&gt;
	 * inventory transfer (or a bank move) usually nets to zero within a single tick, but its two
	 * container updates can land a tick apart; an unresolved loss is held here so a same-type
	 * gain arriving shortly after still cancels it out instead of being counted as a real pickup.
	 *
	 * <p>{@code held} is mutated in place: {@code held.get(type)[0]} is the outstanding (negative)
	 * amount and {@code [1]} the ticks remaining before it is given up on as a genuine, untracked
	 * loss. A type touched by {@code tickDelta} has its countdown refreshed to {@code holdTicks};
	 * an untouched one just ages down.
	 */
	static Map<LeafType, Integer> resolve(Map<LeafType, Integer> tickDelta, Map<LeafType, int[]> held, int holdTicks)
	{
		Map<LeafType, Integer> combined = new EnumMap<>(LeafType.class);
		for (Map.Entry<LeafType, int[]> e : held.entrySet())
		{
			combined.put(e.getKey(), e.getValue()[0]);
		}
		for (Map.Entry<LeafType, Integer> e : tickDelta.entrySet())
		{
			combined.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		Map<LeafType, Integer> gains = new EnumMap<>(LeafType.class);
		Map<LeafType, int[]> newHeld = new EnumMap<>(LeafType.class);
		for (Map.Entry<LeafType, Integer> e : combined.entrySet())
		{
			LeafType type = e.getKey();
			int amount = e.getValue();
			if (amount > 0)
			{
				gains.put(type, amount);
			}
			else if (amount < 0)
			{
				int[] prior = held.get(type);
				boolean touched = tickDelta.containsKey(type);
				int ticksLeft = touched || prior == null ? holdTicks : prior[1] - 1;
				if (ticksLeft > 0)
				{
					newHeld.put(type, new int[]{amount, ticksLeft});
				}
				// else: never offset within the grace window -- a genuine, untracked loss.
			}
		}

		held.clear();
		held.putAll(newHeld);
		return gains;
	}
}
