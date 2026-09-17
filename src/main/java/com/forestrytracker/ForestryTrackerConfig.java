package com.forestrytracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Units;

@ConfigGroup(ForestryTrackerConfig.GROUP)
public interface ForestryTrackerConfig extends Config
{
	String GROUP = "forestrytracker";
	String LIFETIME_BARK_KEY = "lifetimeBark";
	String RESET_LIFETIME_KEY = "resetLifetimeBark";

	@ConfigSection(
		name = "Session",
		description = "When the session starts and resets",
		position = 0
	)
	String sessionSection = "session";

	@ConfigSection(
		name = "Overlay",
		description = "Which lines the in-game overlay shows",
		position = 1
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Lifetime",
		description = "Persistent totals",
		position = 2
	)
	String lifetimeSection = "lifetime";

	@ConfigItem(
		keyName = "statTimeout",
		name = "Reset stats",
		description = "Hide the overlay and (optionally) start a new session after this many minutes without a Forestry event or bark",
		section = sessionSection,
		position = 0
	)
	@Units(Units.MINUTES)
	default int statTimeout()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "resetOnTimeout",
		name = "New session after timeout",
		description = "When stats have timed out, the next event or bark starts a fresh session instead of resuming the old one",
		section = sessionSection,
		position = 1
	)
	default boolean resetOnTimeout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the Forestry stats overlay",
		section = overlaySection,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEventName",
		name = "Show last event",
		description = "Show the name of the current / most recent event",
		section = overlaySection,
		position = 1
	)
	default boolean showEventName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTimeSinceEvent",
		name = "Show time since event",
		description = "Show how long ago the last event started",
		section = overlaySection,
		position = 2
	)
	default boolean showTimeSinceEvent()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBarkPerHour",
		name = "Show bark/hr",
		description = "Show anima-infused bark per hour for this session",
		section = overlaySection,
		position = 3
	)
	default boolean showBarkPerHour()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLogs",
		name = "Show logs cut",
		description = "Show the number of logs cut, and logs per hour, this session",
		section = overlaySection,
		position = 4
	)
	default boolean showLogs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showXp",
		name = "Show XP/hr",
		description = "Show Woodcutting experience per hour for this session",
		section = overlaySection,
		position = 5
	)
	default boolean showXp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLastEventBark",
		name = "Show last event bark",
		description = "Show the bark awarded by the current / most recent event",
		section = overlaySection,
		position = 6
	)
	default boolean showLastEventBark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSessionBark",
		name = "Show session bark",
		description = "Show total bark for this session",
		section = overlaySection,
		position = 7
	)
	default boolean showSessionBark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLifetimeBark",
		name = "Show lifetime bark",
		description = "Show bark tracked across all sessions on this character",
		section = overlaySection,
		position = 8
	)
	default boolean showLifetimeBark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEventsSeen",
		name = "Show events seen",
		description = "Show how many Forestry events have been seen this session",
		section = overlaySection,
		position = 9
	)
	default boolean showEventsSeen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLeaves",
		name = "Show leaves",
		description = "Show leaves gathered this session",
		section = overlaySection,
		position = 10
	)
	default boolean showLeaves()
	{
		return true;
	}

	@ConfigItem(
		keyName = "leavesBreakdown",
		name = "Leaves per type",
		description = "Break the leaves line down into Leaves / Oak / Willow / Maple / Yew / Magic",
		section = overlaySection,
		position = 11
	)
	default boolean leavesBreakdown()
	{
		return false;
	}

	@ConfigItem(
		keyName = RESET_LIFETIME_KEY,
		name = "Reset lifetime bark",
		description = "Tick to reset the lifetime bark total for the logged-in character to zero (unticks itself)",
		section = lifetimeSection,
		position = 0
	)
	default boolean resetLifetimeBark()
	{
		return false;
	}
}
