package com.forestrytracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class ForestryTrackerPanel extends PluginPanel
{
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final Color ACTIVE_EVENT = new Color(120, 220, 120);

	private final ForestryTrackerPlugin plugin;

	private final JLabel statusValue = valueLabel();
	private final JLabel eventValue = valueLabel();
	private final JLabel sinceValue = valueLabel();
	private final JLabel lastBarkValue = valueLabel();
	private final JLabel barkHrValue = valueLabel();
	private final JLabel sessionBarkValue = valueLabel();
	private final JLabel lifetimeBarkValue = valueLabel();
	private final JLabel eventsValue = valueLabel();
	private final JLabel leavesValue = valueLabel();

	private final Map<ForestryEvent, JLabel> eventCountLabels = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, JLabel> eventBarkLabels = new EnumMap<>(ForestryEvent.class);
	private final Map<LeafType, JLabel> leafLabels = new EnumMap<>(LeafType.class);

	private final JLabel lifeBarkValue = valueLabel();
	private final JLabel lifeEventsValue = valueLabel();
	private final JLabel lifeLeavesValue = valueLabel();
	private final Map<ForestryEvent, JLabel> lifeEventCountLabels = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, JLabel> lifeEventBarkLabels = new EnumMap<>(ForestryEvent.class);
	private final Map<LeafType, JLabel> lifeLeafLabels = new EnumMap<>(LeafType.class);

	private final JPanel historyList = new JPanel();
	private final JLabel historyEmpty = new JLabel("No events yet");

	ForestryTrackerPanel(ForestryTrackerPlugin plugin)
	{
		super();
		this.plugin = plugin;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Forestry Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		add(title);
		add(Box.createVerticalStrut(8));

		JPanel stats = section("Session");
		stats.add(row("Status", statusValue));
		stats.add(row("Event", eventValue));
		stats.add(row("Since event", sinceValue));
		stats.add(row("Last event bark", lastBarkValue));
		stats.add(row("Bark/hr", barkHrValue));
		stats.add(row("Session bark", sessionBarkValue));
		stats.add(row("Lifetime bark", lifetimeBarkValue));
		stats.add(row("Events seen", eventsValue));
		stats.add(row("Leaves", leavesValue));
		add(stats);
		add(Box.createVerticalStrut(6));

		JButton reset = new JButton("Reset session");
		reset.setFocusable(false);
		reset.addActionListener(e -> plugin.resetSession());
		reset.setAlignmentX(LEFT_ALIGNMENT);
		add(reset);
		add(Box.createVerticalStrut(10));

		JPanel leaves = section("Leaves");
		for (LeafType type : LeafType.values())
		{
			JLabel value = valueLabel();
			leafLabels.put(type, value);
			leaves.add(row(type.getDisplayName(), value));
		}
		add(leaves);
		add(Box.createVerticalStrut(10));

		JPanel events = section("Events (count / bark)");
		for (ForestryEvent event : ForestryEvent.values())
		{
			JLabel count = valueLabel();
			JLabel bark = valueLabel();
			eventCountLabels.put(event, count);
			eventBarkLabels.put(event, bark);

			JPanel values = new JPanel(new GridLayout(1, 2, 4, 0));
			values.setOpaque(false);
			values.add(count);
			values.add(bark);
			events.add(row(event.getDisplayName(), values));
		}
		add(events);
		add(Box.createVerticalStrut(10));

		JPanel history = section("History");
		historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
		historyList.setOpaque(false);
		historyEmpty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		historyList.add(historyEmpty);
		history.add(historyList);
		add(history);
		add(Box.createVerticalStrut(10));

		JPanel lifetime = section("Lifetime (this character)");
		lifetime.add(row("Bark", lifeBarkValue));
		lifetime.add(row("Events seen", lifeEventsValue));
		lifetime.add(row("Leaves", lifeLeavesValue));
		for (LeafType type : LeafType.values())
		{
			JLabel value = valueLabel();
			lifeLeafLabels.put(type, value);
			lifetime.add(row("  " + type.getDisplayName(), value));
		}
		for (ForestryEvent event : ForestryEvent.values())
		{
			JLabel count = valueLabel();
			JLabel bark = valueLabel();
			lifeEventCountLabels.put(event, count);
			lifeEventBarkLabels.put(event, bark);

			JPanel values = new JPanel(new GridLayout(1, 2, 4, 0));
			values.setOpaque(false);
			values.add(count);
			values.add(bark);
			lifetime.add(row(event.getDisplayName(), values));
		}
		add(lifetime);

		refresh();
	}

	private void refreshLifetime()
	{
		LifetimeStats life = plugin.getLifetime();
		lifeBarkValue.setText(Integer.toString(life.getBark()));
		lifeEventsValue.setText(Integer.toString(life.getEventsSeen()));
		lifeLeavesValue.setText(Integer.toString(life.getTotalLeaves()));
		for (Map.Entry<LeafType, JLabel> e : lifeLeafLabels.entrySet())
		{
			e.getValue().setText(Integer.toString(life.getLeaves(e.getKey())));
		}
		for (ForestryEvent e : ForestryEvent.values())
		{
			lifeEventCountLabels.get(e).setText(Integer.toString(life.getEventCount(e)));
			lifeEventBarkLabels.get(e).setText(Integer.toString(life.getBarkForEvent(e)));
		}
	}

	void refresh()
	{
		ForestrySession session = plugin.getSession();
		lifetimeBarkValue.setText(Integer.toString(plugin.getLifetimeBark()));
		refreshLifetime();

		if (session == null)
		{
			statusValue.setText("Waiting for an event");
			statusValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			eventValue.setText("-");
			sinceValue.setText("-");
			lastBarkValue.setText("0");
			barkHrValue.setText("0");
			sessionBarkValue.setText("0");
			eventsValue.setText("0");
			leavesValue.setText("0");
			for (JLabel l : leafLabels.values())
			{
				l.setText("0");
			}
			for (ForestryEvent e : ForestryEvent.values())
			{
				eventCountLabels.get(e).setText("0");
				eventBarkLabels.get(e).setText("0");
			}
			renderHistory(List.of());
			return;
		}

		if (session.getCurrentEvent() != null)
		{
			statusValue.setText("Event in progress");
			statusValue.setForeground(ACTIVE_EVENT);
		}
		else if (session.isActive())
		{
			statusValue.setText("Active");
			statusValue.setForeground(Color.WHITE);
		}
		else
		{
			statusValue.setText("Idle (timed out)");
			statusValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		ForestryEvent shown = session.getCurrentEvent() != null ? session.getCurrentEvent() : session.getLastEvent();
		eventValue.setText(shown != null ? shown.getDisplayName() : (session.getLastBarkAward() != null ? "Unknown" : "-"));
		eventValue.setForeground(session.getCurrentEvent() != null ? ACTIVE_EVENT : Color.WHITE);

		Duration since = session.getTimeSinceLastEvent();
		sinceValue.setText(since == null ? "-" : ForestryTrackerOverlay.formatDuration(since));
		lastBarkValue.setText(Integer.toString(session.getLastEventBark()));
		barkHrValue.setText(Integer.toString(session.getBarkPerHour()));
		sessionBarkValue.setText(Integer.toString(session.getTotalBark()));
		eventsValue.setText(Integer.toString(session.getEventsSeen()));
		leavesValue.setText(Integer.toString(session.getTotalLeaves()));

		for (Map.Entry<LeafType, JLabel> e : leafLabels.entrySet())
		{
			e.getValue().setText(Integer.toString(session.getLeaves(e.getKey())));
		}
		for (ForestryEvent e : ForestryEvent.values())
		{
			eventCountLabels.get(e).setText(Integer.toString(session.getEventCount(e)));
			eventBarkLabels.get(e).setText(Integer.toString(session.getBarkForEvent(e)));
		}

		renderHistory(session.getHistory());
	}

	private void renderHistory(List<EventRecord> records)
	{
		historyList.removeAll();
		if (records.isEmpty())
		{
			historyList.add(historyEmpty);
		}
		else
		{
			for (EventRecord record : records)
			{
				historyList.add(historyRow(record));
			}
		}
		historyList.revalidate();
		historyList.repaint();
	}

	private static JPanel historyRow(EventRecord record)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

		Instant when = record.getEnd() != null ? record.getEnd() : record.getStart();
		JLabel left = new JLabel(TIME_FORMAT.format(when) + "  " + record.getEventName());
		left.setFont(FontManager.getRunescapeSmallFont());
		left.setForeground(record.getEvent() == null ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);

		JLabel right = new JLabel(record.getBark() + " bark", SwingConstants.RIGHT);
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(ColorScheme.BRAND_ORANGE);

		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static JPanel section(String name)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		panel.setAlignmentX(LEFT_ALIGNMENT);

		JLabel header = new JLabel(name);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		panel.add(header);
		return panel;
	}

	private static JPanel row(String name, java.awt.Component value)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		JLabel label = new JLabel(name);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private static JLabel valueLabel()
	{
		JLabel label = new JLabel("0", SwingConstants.RIGHT);
		label.setForeground(Color.WHITE);
		return label;
	}
}
