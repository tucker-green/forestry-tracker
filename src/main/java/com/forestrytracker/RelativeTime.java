package com.forestrytracker;

import java.time.Duration;

/**
 * Formats a {@link Duration} elapsed since some past instant as a short relative-time string,
 * e.g. "just now", "4m ago", "1h 3m ago".
 */
final class RelativeTime
{
	private RelativeTime()
	{
	}

	static String ago(Duration d)
	{
		if (d == null)
		{
			return "—";
		}
		long secs = Math.max(0, d.getSeconds());
		if (secs < 60)
		{
			return "just now";
		}
		long h = secs / 3600;
		long m = (secs % 3600) / 60;
		if (h > 0)
		{
			return h + "h " + m + "m ago";
		}
		return m + "m ago";
	}

	/** Elapsed time from a session start, formatted for chart x-axis labels, e.g. "0m", "1h 25m". */
	static String elapsed(Duration d)
	{
		if (d == null)
		{
			return "0m";
		}
		long secs = Math.max(0, d.getSeconds());
		long h = secs / 3600;
		long m = (secs % 3600) / 60;
		if (h > 0)
		{
			return h + "h " + m + "m";
		}
		return m + "m";
	}

	/** A plain (non-"ago") duration, e.g. "45s", "3m", "1h 3m" - used for durations/gaps in the Rates card. */
	static String duration(Duration d)
	{
		if (d == null)
		{
			return "—";
		}
		long secs = Math.max(0, d.getSeconds());
		long h = secs / 3600;
		long m = (secs % 3600) / 60;
		if (h > 0)
		{
			return h + "h " + m + "m";
		}
		if (m > 0)
		{
			return m + "m";
		}
		return secs + "s";
	}
}
