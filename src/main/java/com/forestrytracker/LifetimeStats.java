package com.forestrytracker;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;

/**
 * Running totals across all sessions for one character. Serialized as JSON into the RuneScape profile config.
 * Per-day figures are keyed by ISO local date ("2026-09-17") and capped at {@link #MAX_DAYS}.
 */
class LifetimeStats
{
	static final int MAX_DAYS = 90;

	@Getter
	private int bark;
	@Getter
	private int eventsSeen;
	@Getter
	private int logs;
	@Getter
	private long xp;
	private Map<ForestryEvent, Integer> eventCounts = new EnumMap<>(ForestryEvent.class);
	private Map<ForestryEvent, Integer> barkByEvent = new EnumMap<>(ForestryEvent.class);
	private Map<LeafType, Integer> leaves = new EnumMap<>(LeafType.class);
	private Map<String, Integer> logsByType = new LinkedHashMap<>();
	private Map<String, DayStats> days = new TreeMap<>();

	void addBark(int amount, ForestryEvent event, String day)
	{
		bark += amount;
		if (event != null)
		{
			barkByEvent.merge(event, amount, Integer::sum);
		}
		day(day).bark += amount;
	}

	void addEvent(ForestryEvent event, String day)
	{
		eventsSeen++;
		eventCounts.merge(event, 1, Integer::sum);
		day(day).events++;
	}

	void addLeaves(LeafType type, int amount, String day)
	{
		if (amount > 0)
		{
			leaves.merge(type, amount, Integer::sum);
			day(day).leaves += amount;
		}
	}

	void addLog(String type, String day)
	{
		logs++;
		logsByType.merge(type, 1, Integer::sum);
		day(day).logs++;
	}

	void addXp(long delta, String day)
	{
		if (delta > 0)
		{
			xp += delta;
			day(day).xp += delta;
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

	Map<String, Integer> getLogsByType()
	{
		return Collections.unmodifiableMap(logsByType);
	}

	/** Oldest day first. */
	Map<String, DayStats> getDays()
	{
		return Collections.unmodifiableMap(days);
	}

	private DayStats day(String key)
	{
		if (key == null)
		{
			return new DayStats();
		}
		DayStats d = days.get(key);
		if (d == null)
		{
			d = new DayStats();
			days.put(key, d);
			while (days.size() > MAX_DAYS)
			{
				days.remove(((TreeMap<String, DayStats>) days).firstKey());
			}
		}
		return d;
	}

	/** Gson may leave maps as plain HashMaps or null; normalise after deserialization. */
	LifetimeStats normalize()
	{
		eventCounts = copy(eventCounts, ForestryEvent.class);
		barkByEvent = copy(barkByEvent, ForestryEvent.class);
		leaves = copy(leaves, LeafType.class);
		logsByType = logsByType == null ? new LinkedHashMap<>() : new LinkedHashMap<>(logsByType);
		Map<String, DayStats> sortedDays = new TreeMap<>();
		if (days != null)
		{
			days.forEach((k, v) ->
			{
				if (k != null && v != null)
				{
					sortedDays.put(k, v);
				}
			});
		}
		days = sortedDays;
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
