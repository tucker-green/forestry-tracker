package com.forestrytracker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutable per-session statistics. Pure model: no RuneLite client dependencies so it can be unit tested.
 */
public class ForestrySession
{
	private static final int MAX_HISTORY = 100;

	/**
	 * A bark award arriving this soon after an event ended is attributed to that event
	 * (e.g. the Struggling Sapling reward message can arrive after the sapling despawns).
	 */
	static final Duration BARK_ATTRIBUTION_GRACE = Duration.ofSeconds(10);

	private final Supplier<Instant> clock;

	@Getter
	private final Instant start;
	@Getter
	private Instant lastActivity;
	@Getter
	@Setter
	private boolean active = true;

	@Getter
	private int totalBark;
	@Getter
	private int lastEventBark;
	@Getter
	@Nullable
	private ForestryEvent lastEvent;
	@Getter
	@Nullable
	private Instant lastEventStart;
	@Getter
	@Nullable
	private Instant lastEventEnd;
	@Getter
	@Nullable
	private Instant lastBarkAward;
	@Getter
	private int eventsSeen;

	private final Map<ForestryEvent, Integer> eventCounts = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, Integer> barkByEvent = new EnumMap<>(ForestryEvent.class);
	private final Map<LeafType, Integer> leaves = new EnumMap<>(LeafType.class);
	private final List<EventRecord> history = new ArrayList<>();

	@Getter
	@Nullable
	private ForestryEvent currentEvent;
	@Getter
	private int currentEventBark;
	@Getter
	@Nullable
	private Instant currentEventStart;

	public ForestrySession()
	{
		this(Instant::now);
	}

	ForestrySession(Supplier<Instant> clock)
	{
		this.clock = clock;
		this.start = clock.get();
		this.lastActivity = start;
	}

	/** Marks the session as active right now. */
	public void touch()
	{
		lastActivity = clock.get();
		active = true;
	}

	/** Called when a Forestry event is first seen. Ends any event still marked current. */
	public void startEvent(ForestryEvent event)
	{
		if (currentEvent != null)
		{
			endEvent();
		}

		Instant now = clock.get();
		currentEvent = event;
		currentEventBark = 0;
		currentEventStart = now;
		lastEvent = event;
		lastEventStart = now;
		eventsSeen++;
		eventCounts.merge(event, 1, Integer::sum);
		touch();
	}

	/** Called when the current event's NPCs/objects are all gone. */
	public void endEvent()
	{
		if (currentEvent == null)
		{
			return;
		}

		Instant now = clock.get();
		lastEventEnd = now;
		lastEventBark = currentEventBark;
		lastEvent = currentEvent;
		addHistory(new EventRecord(currentEvent, currentEventStart, now, currentEventBark));

		currentEvent = null;
		currentEventBark = 0;
		currentEventStart = null;
	}

	/** Records an anima-infused bark award. */
	public void addBark(int amount)
	{
		Instant now = clock.get();
		totalBark += amount;
		lastBarkAward = now;
		touch();

		if (currentEvent != null)
		{
			currentEventBark += amount;
			lastEventBark = currentEventBark;
			barkByEvent.merge(currentEvent, amount, Integer::sum);
			return;
		}

		// Event just ended: attribute to it.
		if (lastEventEnd != null && !history.isEmpty()
			&& Duration.between(lastEventEnd, now).compareTo(BARK_ATTRIBUTION_GRACE) <= 0)
		{
			EventRecord last = history.get(0);
			last.addBark(amount, now);
			lastEventBark = last.getBark();
			if (last.getEvent() != null)
			{
				barkByEvent.merge(last.getEvent(), amount, Integer::sum);
			}
			return;
		}

		// No detected event: record it standalone.
		lastEvent = null;
		lastEventBark = amount;
		lastEventStart = now;
		lastEventEnd = now;
		addHistory(new EventRecord(null, now, now, amount));
	}

	public void addLeaves(LeafType type, int amount)
	{
		if (amount <= 0)
		{
			return;
		}
		leaves.merge(type, amount, Integer::sum);
		touch();
	}

	public int getLeaves(LeafType type)
	{
		return leaves.getOrDefault(type, 0);
	}

	public int getTotalLeaves()
	{
		int total = 0;
		for (int n : leaves.values())
		{
			total += n;
		}
		return total;
	}

	public int getEventCount(ForestryEvent event)
	{
		return eventCounts.getOrDefault(event, 0);
	}

	public int getBarkForEvent(ForestryEvent event)
	{
		return barkByEvent.getOrDefault(event, 0);
	}

	/** Newest first. */
	public List<EventRecord> getHistory()
	{
		return Collections.unmodifiableList(history);
	}

	/** Bark per hour over the whole session, computed live so it stays current between awards. */
	public int getBarkPerHour()
	{
		if (totalBark <= 0)
		{
			return 0;
		}
		long elapsedMs = Duration.between(start, clock.get()).toMillis();
		if (elapsedMs <= 0)
		{
			return 0;
		}
		return (int) ((double) totalBark * Duration.ofHours(1).toMillis() / elapsedMs);
	}

	/** Time since the most recent event began (or, failing that, the most recent bark award). */
	@Nullable
	public Duration getTimeSinceLastEvent()
	{
		Instant ref = lastEventStart != null ? lastEventStart : lastBarkAward;
		if (ref == null)
		{
			return null;
		}
		return Duration.between(ref, clock.get());
	}

	public Duration getTimeSinceActivity()
	{
		return Duration.between(lastActivity, clock.get());
	}

	private void addHistory(EventRecord record)
	{
		history.add(0, record);
		while (history.size() > MAX_HISTORY)
		{
			history.remove(history.size() - 1);
		}
	}
}
