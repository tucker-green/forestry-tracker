package com.forestrytracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class ForestryTrackerOverlay extends OverlayPanel
{
	private static final Color ACTIVE_EVENT = new Color(120, 220, 120);

	private final ForestryTrackerPlugin plugin;
	private final ForestryTrackerConfig config;

	@Inject
	ForestryTrackerOverlay(ForestryTrackerPlugin plugin, ForestryTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Forestry Tracker overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ForestrySession session = plugin.getSession();
		if (!config.showOverlay() || session == null || !session.isActive())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Forestry")
			.color(session.getCurrentEvent() != null ? ACTIVE_EVENT : Color.WHITE)
			.build());

		if (config.showEventName())
		{
			ForestryEvent current = session.getCurrentEvent();
			ForestryEvent last = session.getLastEvent();
			String name = current != null ? current.getDisplayName()
				: last != null ? last.getDisplayName()
				: session.getLastBarkAward() != null ? "Unknown" : "-";
			panelComponent.getChildren().add(LineComponent.builder()
				.left(current != null ? "Event:" : "Last event:")
				.right(name)
				.rightColor(current != null ? ACTIVE_EVENT : Color.WHITE)
				.build());
		}

		if (config.showTimeSinceEvent())
		{
			Duration since = session.getTimeSinceLastEvent();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Since event:")
				.right(since == null ? "-" : formatDuration(since))
				.build());
		}

		if (config.showLastEventBark())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Last event bark:")
				.right(Integer.toString(session.getLastEventBark()))
				.build());
		}

		if (config.showBarkPerHour())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Bark/hr:")
				.right(Integer.toString(session.getBarkPerHour()))
				.build());
		}

		if (config.showSessionBark())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Session bark:")
				.right(Integer.toString(session.getTotalBark()))
				.build());
		}

		if (config.showLifetimeBark())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Lifetime bark:")
				.right(Integer.toString(plugin.getLifetimeBark()))
				.build());
		}

		if (config.showEventsSeen())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Events:")
				.right(Integer.toString(session.getEventsSeen()))
				.build());
		}

		if (config.showLeaves())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Leaves:")
				.right(Integer.toString(session.getTotalLeaves()))
				.build());

			if (config.leavesBreakdown())
			{
				for (LeafType type : LeafType.values())
				{
					int n = session.getLeaves(type);
					if (n > 0)
					{
						panelComponent.getChildren().add(LineComponent.builder()
							.left("  " + type.getDisplayName() + ":")
							.right(Integer.toString(n))
							.build());
					}
				}
			}
		}

		return super.render(graphics);
	}

	static String formatDuration(Duration d)
	{
		long secs = Math.max(0, d.getSeconds());
		long h = secs / 3600;
		long m = (secs % 3600) / 60;
		long s = secs % 60;
		if (h > 0)
		{
			return String.format("%d:%02d:%02d", h, m, s);
		}
		return String.format("%02d:%02d", m, s);
	}
}
