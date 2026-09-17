package com.forestrytracker;

/**
 * Activity within one fixed-length slice of a session. Plain fields so Gson can persist it.
 */
class TimeBucket
{
	long start;
	int logs;
	int bark;
	int leaves;
	int events;
	long xp;

	TimeBucket()
	{
	}

	TimeBucket(long start)
	{
		this.start = start;
	}

	void add(TimeBucket other)
	{
		logs += other.logs;
		bark += other.bark;
		leaves += other.leaves;
		events += other.events;
		xp += other.xp;
	}
}
