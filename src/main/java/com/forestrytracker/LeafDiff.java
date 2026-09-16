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
}
