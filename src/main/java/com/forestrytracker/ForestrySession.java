package com.forestrytracker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
	 * Cap on the number of timeline slices kept, oldest dropped first. Bounds memory/JSON size
	 * for a session whose {@code start} is very old (e.g. a stale multi-day session touched back
	 * to life) instead of backfilling one slice per {@link #BUCKET} all the way from start.
	 */
	private static final int MAX_TIMELINE = 288;

	/** Width of one timeline slice. */
	static final Duration BUCKET = Duration.ofMinutes(5);

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
	@Getter
	private int logsCut;
	@Getter
	private long xpGained;

	/**
	 * Total idle time excluded from {@link #getDuration()}: accumulated whenever {@link #touch()}
	 * reactivates a session that had timed out, so a long-idle-then-resumed session doesn't have
	 * that idle gap flood back into per-hour rates.
	 */
	private long pausedMillis;

	private final Map<ForestryEvent, Integer> eventCounts = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, Integer> barkByEvent = new EnumMap<>(ForestryEvent.class);
	private final Map<LeafType, Integer> leaves = new EnumMap<>(LeafType.class);
	private final Map<String, Integer> logsByType = new LinkedHashMap<>();
	private final List<EventRecord> history = new ArrayList<>();
	private final List<TimeBucket> timeline = new ArrayList<>();

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

	ForestrySession(SessionState state)
	{
		this(state, Instant::now);
	}

	/** Restores a session from a persisted snapshot. Any event that was in progress is treated as ended. */
	ForestrySession(SessionState state, Supplier<Instant> clock)
	{
		this.clock = clock;
		this.start = Instant.ofEpochMilli(state.start);
		this.lastActivity = Instant.ofEpochMilli(state.lastActivity);
		this.active = state.active;
		this.totalBark = state.totalBark;
		this.lastEventBark = state.lastEventBark;
		this.lastEvent = state.lastEvent;
		this.lastEventStart = toInstant(state.lastEventStart);
		this.lastEventEnd = toInstant(state.lastEventEnd);
		this.lastBarkAward = toInstant(state.lastBarkAward);
		this.eventsSeen = state.eventsSeen;
		this.logsCut = state.logsCut;
		this.xpGained = state.xpGained;
		this.pausedMillis = state.pausedMillis;
		if (state.eventCounts != null)
		{
			state.eventCounts.forEach((k, v) -> putIfPresent(eventCounts, k, v));
		}
		if (state.barkByEvent != null)
		{
			state.barkByEvent.forEach((k, v) -> putIfPresent(barkByEvent, k, v));
		}
		if (state.leaves != null)
		{
			state.leaves.forEach((k, v) -> putIfPresent(leaves, k, v));
		}
		if (state.logsByType != null)
		{
			state.logsByType.forEach((k, v) -> putIfPresent(logsByType, k, v));
		}
		if (state.history != null)
		{
			for (SessionState.RecordState r : state.history)
			{
				if (r == null)
				{
					continue;
				}
				history.add(new EventRecord(r.event, Instant.ofEpochMilli(r.start), toInstant(r.end), r.bark));
				if (history.size() >= MAX_HISTORY)
				{
					break;
				}
			}
		}
		if (state.timeline != null)
		{
			for (TimeBucket b : state.timeline)
			{
				if (b != null)
				{
					timeline.add(b);
				}
			}
			while (timeline.size() > MAX_TIMELINE)
			{
				timeline.remove(0);
			}
		}
	}

	/** Snapshot for persistence. An event in progress is recorded as if it had just ended. */
	SessionState toState()
	{
		SessionState state = new SessionState();
		state.start = start.toEpochMilli();
		state.lastActivity = lastActivity.toEpochMilli();
		state.active = active;
		state.totalBark = totalBark;
		state.lastEventBark = currentEvent != null ? currentEventBark : lastEventBark;
		state.lastEvent = lastEvent;
		state.lastEventStart = toMillis(lastEventStart);
		state.lastEventEnd = toMillis(lastEventEnd);
		state.lastBarkAward = toMillis(lastBarkAward);
		state.eventsSeen = eventsSeen;
		state.logsCut = logsCut;
		state.xpGained = xpGained;
		state.pausedMillis = pausedMillis;
		state.eventCounts.putAll(eventCounts);
		state.barkByEvent.putAll(barkByEvent);
		state.leaves.putAll(leaves);
		state.logsByType.putAll(logsByType);
		state.timeline.addAll(timeline);

		if (currentEvent != null)
		{
			SessionState.RecordState r = new SessionState.RecordState();
			r.event = currentEvent;
			r.start = currentEventStart.toEpochMilli();
			r.end = clock.get().toEpochMilli();
			r.bark = currentEventBark;
			state.history.add(r);
		}
		for (EventRecord record : history)
		{
			SessionState.RecordState r = new SessionState.RecordState();
			r.event = record.getEvent();
			r.start = record.getStart().toEpochMilli();
			r.end = toMillis(record.getEnd());
			r.bark = record.getBark();
			state.history.add(r);
		}
		return state;
	}

	// --- mutation ----------------------------------------------------------------------------------------------

	/**
	 * Marks the session as active right now. Reactivating a session that had timed out excludes
	 * the idle gap since it went inactive from {@link #getDuration()}, so resuming after a long
	 * pause doesn't flood that idle time back into per-hour rates.
	 */
	public void touch()
	{
		Instant now = clock.get();
		if (!active)
		{
			Duration idle = Duration.between(lastActivity, now);
			if (!idle.isNegative())
			{
				pausedMillis += idle.toMillis();
			}
		}
		lastActivity = now;
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
		bucket().events++;
		touch();
	}

	/**
	 * Re-opens the most recently completed event so it becomes current again, e.g. when the
	 * plugin ended it early (the player stopped participating) but the entities are still around
	 * and later bark should attach to the same event rather than double-counting it. Does not
	 * touch {@link #eventsSeen} or the per-event counts: it is the same event, not a new one.
	 * {@link #lastEventEnd} is cleared since the event is active again, not ended.
	 *
	 * @return true if a record was reopened; false if there is no history, or the most recent
	 * record is a standalone "Unknown event" bark award (nothing to reopen).
	 */
	public boolean resumeLastEvent()
	{
		if (currentEvent != null || history.isEmpty())
		{
			return false;
		}

		EventRecord record = history.get(0);
		if (record.getEvent() == null)
		{
			return false;
		}

		history.remove(0);
		currentEvent = record.getEvent();
		currentEventStart = record.getStart();
		currentEventBark = record.getBark();
		lastEvent = record.getEvent();
		lastEventStart = record.getStart();
		lastEventEnd = null;
		touch();
		return true;
	}

	/**
	 * Whether {@code event} is the event of the most recent history record, i.e. the record
	 * {@link #resumeLastEvent()} would reopen. Lets the plugin decide whether entities it still
	 * sees for {@code event} belong to that record before deciding to reopen it.
	 */
	public boolean isLastRecord(@Nullable ForestryEvent event)
	{
		return !history.isEmpty() && history.get(0).getEvent() == event;
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
		bucket().bark += amount;
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
		bucket().leaves += amount;
		touch();
	}

	/** Records one log (or mushroom) cut; {@code type} is the display name, e.g. "Oak logs". */
	public void addLog(String type)
	{
		logsCut++;
		logsByType.merge(type, 1, Integer::sum);
		bucket().logs++;
		touch();
	}

	/** Records Woodcutting experience gained. */
	public void addXp(long delta)
	{
		if (delta <= 0)
		{
			return;
		}
		xpGained += delta;
		bucket().xp += delta;
		touch();
	}

	// --- queries -----------------------------------------------------------------------------------------------

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

	public Map<String, Integer> getLogsByType()
	{
		return Collections.unmodifiableMap(logsByType);
	}

	/** Newest first. */
	public List<EventRecord> getHistory()
	{
		return Collections.unmodifiableList(history);
	}

	/** Oldest first; one entry per {@link #BUCKET} from the session start up to the last activity. */
	public List<TimeBucket> getTimeline()
	{
		return Collections.unmodifiableList(timeline);
	}

	/** Elapsed session time: up to now while active, frozen at the last activity once timed out. */
	public Duration getDuration()
	{
		Instant end = active ? clock.get() : lastActivity;
		Duration d = Duration.between(start, end).minus(Duration.ofMillis(pausedMillis));
		return d.isNegative() ? Duration.ZERO : d;
	}

	public int getBarkPerHour()
	{
		return (int) perHour(totalBark);
	}

	public int getLogsPerHour()
	{
		return (int) perHour(logsCut);
	}

	public long getXpPerHour()
	{
		return perHour(xpGained);
	}

	public int getLeavesPerHour()
	{
		return (int) perHour(getTotalLeaves());
	}

	/** Events per hour, with one decimal of precision (e.g. 4.5). */
	public double getEventsPerHour()
	{
		long ms = getDuration().toMillis();
		if (eventsSeen == 0 || ms <= 0)
		{
			return 0;
		}
		return Math.round(eventsSeen * 36_000_000.0 / ms) / 10.0;
	}

	public int getAverageBarkPerEvent()
	{
		return eventsSeen == 0 ? 0 : Math.round((float) totalBark / eventsSeen);
	}

	/** The completed event that awarded the most bark, or null. */
	@Nullable
	public EventRecord getBestEvent()
	{
		EventRecord best = null;
		for (EventRecord r : history)
		{
			if (r.getEvent() != null && (best == null || r.getBark() > best.getBark()))
			{
				best = r;
			}
		}
		return best;
	}

	/** Mean time between consecutive event starts, or null with fewer than two events. */
	@Nullable
	public Duration getAverageEventGap()
	{
		List<Instant> starts = new ArrayList<>();
		if (currentEventStart != null)
		{
			starts.add(currentEventStart);
		}
		for (EventRecord r : history)
		{
			if (r.getEvent() != null)
			{
				starts.add(r.getStart());
			}
		}
		if (starts.size() < 2)
		{
			return null;
		}
		Collections.sort(starts);
		long total = Duration.between(starts.get(0), starts.get(starts.size() - 1)).toMillis();
		return Duration.ofMillis(total / (starts.size() - 1));
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

	// --- internals ---------------------------------------------------------------------------------------------

	private long perHour(long count)
	{
		if (count <= 0)
		{
			return 0;
		}
		long ms = getDuration().toMillis();
		if (ms <= 0)
		{
			return 0;
		}
		return (long) ((double) count * Duration.ofHours(1).toMillis() / ms);
	}

	/**
	 * The timeline slice containing "now", creating empty slices up to it as needed. Drops the
	 * oldest slices first so a session whose activity resumes long after {@code start} (e.g. a
	 * stale multi-day session restored from disk) can't grow this list without bound.
	 */
	private TimeBucket bucket()
	{
		long bucketMs = BUCKET.toMillis();
		long idx = Math.max(0, Duration.between(start, clock.get()).toMillis() / bucketMs);

		while (!timeline.isEmpty() && idx - firstBucketIndex(bucketMs) >= MAX_TIMELINE)
		{
			timeline.remove(0);
		}

		long base = timeline.isEmpty() ? Math.max(0, idx - MAX_TIMELINE + 1) : firstBucketIndex(bucketMs);
		while (timeline.size() <= idx - base)
		{
			timeline.add(new TimeBucket(start.toEpochMilli() + (base + timeline.size()) * bucketMs));
		}
		return timeline.get((int) (idx - base));
	}

	/** Absolute (from {@code start}) bucket index of the oldest slice currently kept. */
	private long firstBucketIndex(long bucketMs)
	{
		return (timeline.get(0).start - start.toEpochMilli()) / bucketMs;
	}

	private void addHistory(EventRecord record)
	{
		history.add(0, record);
		while (history.size() > MAX_HISTORY)
		{
			history.remove(history.size() - 1);
		}
	}

	private static <K> void putIfPresent(Map<K, Integer> map, K key, Integer value)
	{
		if (key != null && value != null)
		{
			map.put(key, value);
		}
	}

	@Nullable
	private static Instant toInstant(@Nullable Long millis)
	{
		return millis == null ? null : Instant.ofEpochMilli(millis);
	}

	@Nullable
	private static Long toMillis(@Nullable Instant instant)
	{
		return instant == null ? null : instant.toEpochMilli();
	}
}
