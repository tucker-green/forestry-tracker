package com.forestrytracker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of a {@link ForestrySession}; stored as JSON in the RuneScape profile config.
 * Instants are epoch milliseconds; null means "not set".
 */
class SessionState
{
	long start;
	long lastActivity;
	boolean active;
	int totalBark;
	int lastEventBark;
	ForestryEvent lastEvent;
	Long lastEventStart;
	Long lastEventEnd;
	Long lastBarkAward;
	int eventsSeen;
	int logsCut;
	long xpGained;
	long pausedMillis;
	Map<ForestryEvent, Integer> eventCounts = new EnumMap<>(ForestryEvent.class);
	Map<ForestryEvent, Integer> barkByEvent = new EnumMap<>(ForestryEvent.class);
	Map<LeafType, Integer> leaves = new EnumMap<>(LeafType.class);
	Map<String, Integer> logsByType = new LinkedHashMap<>();
	List<RecordState> history = new ArrayList<>();
	List<TimeBucket> timeline = new ArrayList<>();

	static class RecordState
	{
		ForestryEvent event;
		long start;
		Long end;
		int bark;
	}
}
