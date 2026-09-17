package com.forestrytracker;

/**
 * Lifetime activity for one calendar day (local time). Plain fields so Gson can persist it.
 */
class DayStats
{
	int logs;
	int bark;
	int leaves;
	int events;
	long xp;
}
