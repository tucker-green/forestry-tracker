package com.forestrytracker;

import java.time.Instant;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One completed Forestry event (or a bark award that could not be tied to a detected event).
 */
@Getter
@AllArgsConstructor
public class EventRecord
{
	@Nullable
	private final ForestryEvent event;
	private final Instant start;
	private Instant end;
	private int bark;

	void addBark(int amount, Instant when)
	{
		bark += amount;
		end = when;
	}

	public String getEventName()
	{
		return event == null ? "Unknown event" : event.getDisplayName();
	}
}
