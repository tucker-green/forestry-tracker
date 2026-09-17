package com.forestrytracker;

import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;

/**
 * Running totals across all sessions for one character. Serialized as JSON into the RuneScape profile config.
 */
class LifetimeStats
{
	@Getter
	private int bark;
	@Getter
	private int eventsSeen;
	private Map<ForestryEvent, Integer> eventCounts = new EnumMap<>(ForestryEvent.class);
	private Map<ForestryEvent, Integer> barkByEvent = new EnumMap<>(ForestryEvent.class);
	private Map<LeafType, Integer> leaves = new EnumMap<>(LeafType.class);

	void addBark(int amount, ForestryEvent event)
	{
		bark += amount;
		if (event != null)
		{
			barkByEvent.merge(event, amount, Integer::sum);
		}
	}

	void addEvent(ForestryEvent event)
	{
		eventsSeen++;
		eventCounts.merge(event, 1, Integer::sum);
	}

	void addLeaves(LeafType type, int amount)
	{
		if (amount > 0)
		{
			leaves.merge(type, amount, Integer::sum);
		}
	}

	int getEventCount(ForestryEvent event)
	{
		return eventCounts.getOrDefault(event, 0);
	}

	int getBarkForEvent(ForestryEvent event)
	{
		return barkByEvent.getOrDefault(event, 0);
	}

	int getLeaves(LeafType type)
	{
		return leaves.getOrDefault(type, 0);
	}

	int getTotalLeaves()
	{
		int total = 0;
		for (int n : leaves.values())
		{
			total += n;
		}
		return total;
	}

	/** Gson may leave maps as plain HashMaps or null; normalise after deserialization. */
	LifetimeStats normalize()
	{
		eventCounts = copy(eventCounts, ForestryEvent.class);
		barkByEvent = copy(barkByEvent, ForestryEvent.class);
		leaves = copy(leaves, LeafType.class);
		return this;
	}

	private static <K extends Enum<K>> Map<K, Integer> copy(Map<K, Integer> source, Class<K> type)
	{
		Map<K, Integer> result = new EnumMap<>(type);
		if (source != null)
		{
			source.forEach((k, v) ->
			{
				if (k != null && v != null)
				{
					result.put(k, v);
				}
			});
		}
		return result;
	}
}
