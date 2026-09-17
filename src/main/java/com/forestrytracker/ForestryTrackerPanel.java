package com.forestrytracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel: a "Session" / "Lifetime" segmented view over {@link ForestrySession} and
 * {@link LifetimeStats}. Component tree is built once in the constructor; {@link #refresh()} only
 * updates labels/values and small list bodies in place, so it is cheap to call every second.
 */
class ForestryTrackerPanel extends PluginPanel
{
	private static final Color BORDER = new Color(58, 58, 58);
	private static final Color STATUS_GREEN = new Color(0x78, 0xdc, 0x78);
	private static final Color LOGS_COLOR = new Color(0x39, 0x87, 0xe5);
	private static final Color BARK_COLOR = new Color(0xd9, 0x59, 0x26);
	private static final Color LEAVES_COLOR = new Color(0x19, 0x9e, 0x70);
	private static final Color EVENTS_COLOR = new Color(0xc9, 0x85, 0x00);
	private static final Color XP_COLOR = new Color(0x90, 0x85, 0xe9);

	private static final int CHART_HEIGHT = 104;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
	private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");

	private final ForestryTrackerPlugin plugin;
	private final ItemManager itemManager;

	private final TabBar tabBar;
	private final JPanel sessionTab = new JPanel();
	private final JPanel lifetimeTab = new JPanel();

	// --- session tab -------------------------------------------------------------------------------------------

	private final JLabel statusDot = new JLabel("●");
	private final JLabel statusText = new JLabel();

	private final StatTile sessionBarkTile = new StatTile("Session bark");
	private final StatTile barkHrTile = new StatTile("Bark / hr");
	private final StatTile logsTile = new StatTile("Logs cut");
	private final StatTile logsHrTile = new StatTile("Logs / hr");
	private final StatTile xpTile = new StatTile("WC XP");
	private final StatTile xpHrTile = new StatTile("XP / hr");

	private final JLabel eventsSeenValue = new JLabel("0", SwingConstants.RIGHT);

	private final JLabel lastEventName = new JLabel();
	private final JLabel lastEventBark = new JLabel();
	private final JLabel lastEventTime = new JLabel();

	private final CollapsibleSection leavesSection;
	private final Map<LeafType, JLabel> leafValues = new EnumMap<>(LeafType.class);
	private final Map<LeafType, JPanel> leafRows = new EnumMap<>(LeafType.class);
	private final JLabel leavesEmpty = mutedLabel("No leaves yet.");

	private final CollapsibleSection eventsSection;
	private final EventTable eventTable = new EventTable(true, "No events this session.");

	private final CollapsibleSection recentEventsSection;
	private final JPanel recentEventsBody = new JPanel();
	private final JLabel recentEventsEmpty = mutedLabel("No events this session.");
	private final RecentEventRow[] recentEventRows = new RecentEventRow[10];

	private final CollapsibleSection logsSection;
	private final HBarChart logsChart = new HBarChart("Logs by type", LOGS_COLOR);

	private final CollapsibleSection chartsSection;
	private final JPanel chartsTimeSeries = new JPanel();
	private final BarChart barkPerEventChart = new BarChart("Bark per event", BARK_COLOR, CHART_HEIGHT);
	private final HBarChart barkByEventTypeChart = new HBarChart("Bark by event type", BARK_COLOR);
	private BarChart logsBucketChart;
	private BarChart barkBucketChart;
	private BarChart xpBucketChart;
	private int bucketFactor = -1;

	private final CollapsibleSection ratesSection;
	private final JLabel ratesDuration = new JLabel();
	private final JLabel ratesEventsPerHr = new JLabel();
	private final JLabel ratesAvgBark = new JLabel();
	private final JLabel ratesBestEvent = new JLabel();
	private final JLabel ratesAvgGap = new JLabel();
	private final JLabel ratesLeavesPerHr = new JLabel();

	// --- lifetime tab --------------------------------------------------------------------------------------------

	private final StatTile lifeBarkTile = new StatTile("Lifetime bark");
	private final StatTile lifeEventsTile = new StatTile("Events seen");
	private final StatTile lifeLogsTile = new StatTile("Logs cut");
	private final StatTile lifeXpTile = new StatTile("WC XP");
	private final StatTile lifeLeavesTile = new StatTile("Leaves total");

	private final CollapsibleSection lifeLeavesSection;
	private final Map<LeafType, JLabel> lifeLeafValues = new EnumMap<>(LeafType.class);
	private final Map<LeafType, JPanel> lifeLeafRows = new EnumMap<>(LeafType.class);
	private final JLabel lifeLeavesEmpty = mutedLabel("No leaves yet.");

	private final CollapsibleSection lifeEventsSection;
	private final EventTable lifeEventTable = new EventTable(false, "No events yet.");

	private final CollapsibleSection lifeLogsSection;
	private final HBarChart lifeLogsChart = new HBarChart("Logs by type", LOGS_COLOR);

	private final CollapsibleSection lifeDaysSection;
	private final BarChart lifeBarkPerDay = new BarChart("Bark per day", BARK_COLOR, CHART_HEIGHT);
	private final BarChart lifeLogsPerDay = new BarChart("Logs per day", LOGS_COLOR, CHART_HEIGHT);
	private final BarChart lifeEventsPerDay = new BarChart("Events per day", EVENTS_COLOR, CHART_HEIGHT);

	ForestryTrackerPanel(ForestryTrackerPlugin plugin)
	{
		super();
		this.plugin = plugin;
		this.itemManager = plugin.getItemManager();

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader());
		add(Box.createVerticalStrut(6));

		tabBar = new TabBar(new String[]{"Session", "Lifetime"}, index ->
		{
			sessionTab.setVisible(index == 0);
			lifetimeTab.setVisible(index == 1);
		});
		add(tabBar);
		add(Box.createVerticalStrut(8));

		sessionTab.setLayout(new BoxLayout(sessionTab, BoxLayout.Y_AXIS));
		sessionTab.setOpaque(false);
		sessionTab.setAlignmentX(LEFT_ALIGNMENT);
		add(sessionTab);

		lifetimeTab.setLayout(new BoxLayout(lifetimeTab, BoxLayout.Y_AXIS));
		lifetimeTab.setOpaque(false);
		lifetimeTab.setAlignmentX(LEFT_ALIGNMENT);
		lifetimeTab.setVisible(false);
		add(lifetimeTab);

		leavesSection = new CollapsibleSection("Leaves", ItemID.LEAVES, itemManager, true);
		eventsSection = new CollapsibleSection("Event breakdown", ItemID.FORESTRY_KIT, itemManager, true);
		recentEventsSection = new CollapsibleSection("Recent events", ItemID.BOOK_OF_THE_DEAD, itemManager, true);
		logsSection = new CollapsibleSection("Logs", ItemID.LOGS, itemManager, true);
		chartsSection = new CollapsibleSection("Charts", null, null, false);
		ratesSection = new CollapsibleSection("Rates");

		lifeLeavesSection = new CollapsibleSection("Leaves", ItemID.LEAVES, itemManager, true);
		lifeEventsSection = new CollapsibleSection("Event breakdown", ItemID.FORESTRY_KIT, itemManager, true);
		lifeLogsSection = new CollapsibleSection("Logs", ItemID.LOGS, itemManager, true);
		lifeDaysSection = new CollapsibleSection("Last 14 days", null, null, true);

		buildSessionTab();
		buildLifetimeTab();

		refresh();
	}

	// --- construction --------------------------------------------------------------------------------------------

	private JPanel buildHeader()
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		BufferedImage icon = ImageUtil.loadImageResource(ForestryTrackerPlugin.class, "icon.png");
		titleRow.add(new JLabel(new ImageIcon(ImageUtil.resizeImage(icon, 20, 20))));
		JLabel title = new JLabel("Forestry Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		titleRow.add(title);
		wrap.add(titleRow);
		wrap.add(Box.createVerticalStrut(6));

		JSeparator sep = new JSeparator();
		sep.setForeground(BORDER);
		sep.setBackground(BORDER);
		sep.setAlignmentX(LEFT_ALIGNMENT);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		wrap.add(sep);
		return wrap;
	}

	private void buildSessionTab()
	{
		JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		status.setOpaque(false);
		status.setAlignmentX(LEFT_ALIGNMENT);
		statusDot.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusText.setForeground(Color.WHITE);
		statusText.setFont(FontManager.getRunescapeSmallFont());
		status.add(statusDot);
		status.add(statusText);
		sessionTab.add(status);
		sessionTab.add(Box.createVerticalStrut(8));

		sessionBarkTile.setIcon(itemManager, ItemID.FORESTRY_CURRENCY);
		barkHrTile.setIcon(itemManager, ItemID.FORESTRY_CURRENCY);
		logsTile.setIcon(itemManager, ItemID.LOGS);
		logsHrTile.setIcon(itemManager, ItemID.LOGS);
		sessionTab.add(tileRow(sessionBarkTile, barkHrTile));
		sessionTab.add(Box.createVerticalStrut(6));
		sessionTab.add(tileRow(logsTile, logsHrTile));
		sessionTab.add(Box.createVerticalStrut(6));
		sessionTab.add(tileRow(xpTile, xpHrTile));
		sessionTab.add(Box.createVerticalStrut(6));

		eventsSeenValue.setForeground(Color.WHITE);
		eventsSeenValue.setFont(FontManager.getRunescapeBoldFont());
		sessionTab.add(borderedRow("Events seen", eventsSeenValue));
		sessionTab.add(Box.createVerticalStrut(6));

		sessionTab.add(buildLastEventCard());
		sessionTab.add(Box.createVerticalStrut(6));

		for (LeafType type : LeafType.values())
		{
			JLabel value = smallValueLabel();
			leafValues.put(type, value);
			JPanel row = plainRow(type.getDisplayName(), value);
			leafRows.put(type, row);
			leavesSection.getBody().add(row);
		}
		leavesSection.getBody().add(leavesEmpty);
		sessionTab.add(leavesSection);
		sessionTab.add(Box.createVerticalStrut(6));

		eventsSection.getBody().add(eventTable.panel);
		sessionTab.add(eventsSection);
		sessionTab.add(Box.createVerticalStrut(6));

		recentEventsBody.setLayout(new BoxLayout(recentEventsBody, BoxLayout.Y_AXIS));
		recentEventsBody.setOpaque(false);
		recentEventsBody.setAlignmentX(LEFT_ALIGNMENT);
		recentEventsBody.add(recentEventsEmpty);
		for (int i = 0; i < recentEventRows.length; i++)
		{
			recentEventRows[i] = new RecentEventRow(i > 0);
			recentEventRows[i].panel.setVisible(false);
			recentEventsBody.add(recentEventRows[i].panel);
		}
		recentEventsSection.getBody().add(recentEventsBody);
		sessionTab.add(recentEventsSection);
		sessionTab.add(Box.createVerticalStrut(6));

		logsSection.getBody().add(logsChart);
		sessionTab.add(logsSection);
		sessionTab.add(Box.createVerticalStrut(6));

		chartsTimeSeries.setLayout(new BoxLayout(chartsTimeSeries, BoxLayout.Y_AXIS));
		chartsTimeSeries.setOpaque(false);
		chartsTimeSeries.setAlignmentX(LEFT_ALIGNMENT);
		ensureBucketCharts(1);
		chartsSection.getBody().add(chartsTimeSeries);
		chartsSection.getBody().add(Box.createVerticalStrut(6));
		barkPerEventChart.setEmptyText("No completed events yet");
		chartsSection.getBody().add(barkPerEventChart);
		chartsSection.getBody().add(Box.createVerticalStrut(6));
		chartsSection.getBody().add(barkByEventTypeChart);
		sessionTab.add(chartsSection);
		sessionTab.add(Box.createVerticalStrut(6));

		ratesSection.getBody().add(plainRow("Session duration", ratesDuration));
		ratesSection.getBody().add(plainRow("Events / hr", ratesEventsPerHr));
		ratesSection.getBody().add(plainRow("Avg bark / event", ratesAvgBark));
		ratesSection.getBody().add(plainRow("Best event", ratesBestEvent));
		ratesSection.getBody().add(plainRow("Avg gap between events", ratesAvgGap));
		ratesSection.getBody().add(plainRow("Leaves / hr", ratesLeavesPerHr));
		for (JLabel l : new JLabel[]{ratesDuration, ratesEventsPerHr, ratesAvgBark, ratesBestEvent, ratesAvgGap, ratesLeavesPerHr})
		{
			l.setForeground(Color.WHITE);
			l.setFont(FontManager.getRunescapeSmallFont());
		}
		sessionTab.add(ratesSection);
		sessionTab.add(Box.createVerticalStrut(8));

		JButton resetSession = new JButton("Reset session");
		resetSession.setFocusable(false);
		resetSession.setAlignmentX(LEFT_ALIGNMENT);
		resetSession.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		resetSession.addActionListener(e -> plugin.resetSession());
		sessionTab.add(resetSession);
	}

	private void buildLifetimeTab()
	{
		lifeBarkTile.setIcon(itemManager, ItemID.FORESTRY_CURRENCY);
		lifeLogsTile.setIcon(itemManager, ItemID.LOGS);
		lifeLeavesTile.setIcon(itemManager, ItemID.LEAVES);

		lifetimeTab.add(tileRow(lifeBarkTile, lifeEventsTile));
		lifetimeTab.add(Box.createVerticalStrut(6));
		lifetimeTab.add(tileRow(lifeLogsTile, lifeXpTile));
		lifetimeTab.add(Box.createVerticalStrut(6));
		lifeLeavesTile.setAlignmentX(LEFT_ALIGNMENT);
		lifeLeavesTile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		lifetimeTab.add(lifeLeavesTile);
		lifetimeTab.add(Box.createVerticalStrut(6));

		for (LeafType type : LeafType.values())
		{
			JLabel value = smallValueLabel();
			lifeLeafValues.put(type, value);
			JPanel row = plainRow(type.getDisplayName(), value);
			lifeLeafRows.put(type, row);
			lifeLeavesSection.getBody().add(row);
		}
		lifeLeavesSection.getBody().add(lifeLeavesEmpty);
		lifetimeTab.add(lifeLeavesSection);
		lifetimeTab.add(Box.createVerticalStrut(6));

		lifeEventsSection.getBody().add(lifeEventTable.panel);
		lifetimeTab.add(lifeEventsSection);
		lifetimeTab.add(Box.createVerticalStrut(6));

		lifeLogsSection.getBody().add(lifeLogsChart);
		lifetimeTab.add(lifeLogsSection);
		lifetimeTab.add(Box.createVerticalStrut(6));

		lifeDaysSection.getBody().add(lifeBarkPerDay);
		lifeDaysSection.getBody().add(Box.createVerticalStrut(6));
		lifeDaysSection.getBody().add(lifeLogsPerDay);
		lifeDaysSection.getBody().add(Box.createVerticalStrut(6));
		lifeDaysSection.getBody().add(lifeEventsPerDay);
		lifetimeTab.add(lifeDaysSection);
		lifetimeTab.add(Box.createVerticalStrut(8));

		JButton resetLifetime = new JButton("Reset lifetime stats");
		resetLifetime.setFocusable(false);
		resetLifetime.setAlignmentX(LEFT_ALIGNMENT);
		resetLifetime.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		resetLifetime.addActionListener(e -> plugin.resetLifetime());
		lifetimeTab.add(resetLifetime);
	}

	private JPanel buildLastEventCard()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(6, 7, 6, 7)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel header = new JLabel("LAST EVENT");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(header);
		card.add(Box.createVerticalStrut(3));

		JPanel line1 = new JPanel(new BorderLayout());
		line1.setOpaque(false);
		lastEventName.setFont(FontManager.getRunescapeBoldFont());
		lastEventName.setForeground(Color.WHITE);
		lastEventBark.setFont(FontManager.getRunescapeSmallFont());
		lastEventBark.setForeground(ColorScheme.BRAND_ORANGE);
		line1.add(lastEventName, BorderLayout.WEST);
		line1.add(lastEventBark, BorderLayout.EAST);
		card.add(line1);

		lastEventTime.setFont(FontManager.getRunescapeSmallFont());
		lastEventTime.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(lastEventTime);
		return card;
	}

	private static JPanel tileRow(StatTile a, StatTile b)
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		row.add(a);
		row.add(b);
		return row;
	}

	private void ensureBucketCharts(int factor)
	{
		if (factor == bucketFactor)
		{
			return;
		}
		bucketFactor = factor;
		int minutes = factor * 5;
		chartsTimeSeries.removeAll();
		logsBucketChart = new BarChart("Logs per " + minutes + " min", LOGS_COLOR, CHART_HEIGHT);
		barkBucketChart = new BarChart("Bark per " + minutes + " min", BARK_COLOR, CHART_HEIGHT);
		xpBucketChart = new BarChart("XP per " + minutes + " min", XP_COLOR, CHART_HEIGHT);
		chartsTimeSeries.add(logsBucketChart);
		chartsTimeSeries.add(Box.createVerticalStrut(6));
		chartsTimeSeries.add(barkBucketChart);
		chartsTimeSeries.add(Box.createVerticalStrut(6));
		chartsTimeSeries.add(xpBucketChart);
		chartsTimeSeries.revalidate();
	}

	// --- refresh ---------------------------------------------------------------------------------------------

	void refresh()
	{
		refreshSession();
		refreshLifetime();
	}

	private void refreshSession()
	{
		ForestrySession session = plugin.getSession();

		if (session == null)
		{
			setStatus(ColorScheme.LIGHT_GRAY_COLOR, "Waiting for an event");
			sessionBarkTile.setValue("0");
			barkHrTile.setValue("—");
			logsTile.setValue("0");
			logsHrTile.setValue("—");
			xpTile.setValue("0");
			xpHrTile.setValue("—");
			eventsSeenValue.setText("0");
			renderLastEvent(null, 0, null, false);
			renderLeafRows(leafValues, leafRows, leavesEmpty, null);
			eventTable.render(e -> 0, e -> 0, 0);
			renderRecentEvents(List.of());
			logsChart.setData(java.util.Collections.emptyMap());
			ensureBucketCharts(1);
			logsBucketChart.setData(new double[0], null, "0m", "0m");
			barkBucketChart.setData(new double[0], null, "0m", "0m");
			xpBucketChart.setData(new double[0], null, "0m", "0m");
			barkPerEventChart.setData(new double[0], null, "", "");
			barkByEventTypeChart.setData(java.util.Collections.emptyMap());
			renderRates(null);
			return;
		}

		if (session.getCurrentEvent() != null)
		{
			setStatus(STATUS_GREEN, "Event in progress: " + session.getCurrentEvent().getDisplayName());
		}
		else if (session.isActive())
		{
			setStatus(ColorScheme.LIGHT_GRAY_COLOR, "Waiting for an event");
		}
		else
		{
			setStatus(ColorScheme.LIGHT_GRAY_COLOR, "Idle (timed out)");
		}

		sessionBarkTile.setValue(fmt(session.getTotalBark()));
		barkHrTile.setValue(session.getTotalBark() <= 0 ? "—" : fmt(session.getBarkPerHour()));
		logsTile.setValue(fmt(session.getLogsCut()));
		logsHrTile.setValue(session.getLogsCut() <= 0 ? "—" : fmt(session.getLogsPerHour()));
		xpTile.setValue(BarChart.format(session.getXpGained()));
		xpHrTile.setValue(session.getXpGained() <= 0 ? "—" : BarChart.format(session.getXpPerHour()));
		eventsSeenValue.setText(fmt(session.getEventsSeen()));

		boolean hasLastEvent = session.getLastEvent() != null || session.getLastBarkAward() != null;
		String lastName = session.getLastEvent() != null ? session.getLastEvent().getDisplayName()
			: (hasLastEvent ? "Unknown event" : null);
		renderLastEvent(lastName, session.getLastEventBark(), session.getTimeSinceLastEvent(), hasLastEvent);

		renderLeafRows(leafValues, leafRows, leavesEmpty, session::getLeaves);
		leavesSection.setCount(fmt(session.getTotalLeaves()));

		eventTable.render(session::getEventCount, session::getBarkForEvent, session.getEventsSeen());
		eventsSection.setCount(fmt(session.getEventsSeen()));

		renderRecentEvents(session.getHistory());

		logsChart.setData(session.getLogsByType());
		logsSection.setCount(fmt(session.getLogsCut()));

		renderCharts(session);
		renderRates(session);
	}

	private void renderCharts(ForestrySession session)
	{
		List<TimeBucket> timeline = session.getTimeline();
		int n = timeline.size();
		int factor = n <= 36 ? 1 : (int) Math.ceil(n / 36.0);
		ensureBucketCharts(factor);

		List<TimeBucket> merged = new ArrayList<>();
		for (int i = 0; i < n; i += factor)
		{
			TimeBucket m = new TimeBucket(timeline.get(i).start);
			for (int j = i; j < Math.min(n, i + factor); j++)
			{
				m.add(timeline.get(j));
			}
			merged.add(m);
		}

		double[] logs = new double[merged.size()];
		double[] bark = new double[merged.size()];
		double[] xp = new double[merged.size()];
		for (int i = 0; i < merged.size(); i++)
		{
			logs[i] = merged.get(i).logs;
			bark[i] = merged.get(i).bark;
			xp[i] = merged.get(i).xp;
		}

		String xStart = "0m";
		String xEnd = merged.isEmpty() ? "0m"
			: RelativeTime.elapsed(Duration.ofMillis(merged.get(merged.size() - 1).start - session.getStart().toEpochMilli()));

		logsBucketChart.setData(logs, null, xStart, xEnd);
		barkBucketChart.setData(bark, null, xStart, xEnd);
		xpBucketChart.setData(xp, null, xStart, xEnd);
		// The newest slice is still filling while the session is active.
		logsBucketChart.setPartialLast(session.isActive());
		barkBucketChart.setPartialLast(session.isActive());
		xpBucketChart.setPartialLast(session.isActive());

		List<EventRecord> completed = new ArrayList<>();
		for (EventRecord r : session.getHistory())
		{
			if (r.getEvent() != null)
			{
				completed.add(r);
			}
		}
		java.util.Collections.reverse(completed);
		double[] eventBark = new double[completed.size()];
		String[] tooltips = new String[completed.size()];
		for (int i = 0; i < completed.size(); i++)
		{
			eventBark[i] = completed.get(i).getBark();
			tooltips[i] = completed.get(i).getEventName() + ": " + fmt(completed.get(i).getBark()) + " bark";
		}
		barkPerEventChart.setData(eventBark, tooltips, "", "");

		Map<String, Integer> byType = new java.util.LinkedHashMap<>();
		for (ForestryEvent e : ForestryEvent.values())
		{
			byType.put(e.getDisplayName(), session.getBarkForEvent(e));
		}
		barkByEventTypeChart.setData(byType);
	}

	private void renderRates(ForestrySession session)
	{
		if (session == null)
		{
			ratesDuration.setText("—");
			ratesEventsPerHr.setText("—");
			ratesAvgBark.setText("—");
			ratesBestEvent.setText("—");
			ratesAvgGap.setText("—");
			ratesLeavesPerHr.setText("—");
			return;
		}
		ratesDuration.setText(RelativeTime.duration(session.getDuration()));
		ratesEventsPerHr.setText(session.getEventsSeen() == 0 ? "—" : String.format("%.1f", session.getEventsPerHour()));
		ratesAvgBark.setText(session.getEventsSeen() == 0 ? "—" : fmt(session.getAverageBarkPerEvent()));
		EventRecord best = session.getBestEvent();
		ratesBestEvent.setText(best == null ? "—" : best.getEventName() + " " + fmt(best.getBark()));
		Duration gap = session.getAverageEventGap();
		ratesAvgGap.setText(gap == null ? "—" : RelativeTime.duration(gap));
		ratesLeavesPerHr.setText(session.getTotalLeaves() == 0 ? "—" : fmt(session.getLeavesPerHour()));
	}

	private void refreshLifetime()
	{
		LifetimeStats life = plugin.getLifetime();

		lifeBarkTile.setValue(fmt(life.getBark()));
		lifeEventsTile.setValue(fmt(life.getEventsSeen()));
		lifeLogsTile.setValue(fmt(life.getLogs()));
		lifeXpTile.setValue(BarChart.format(life.getXp()));
		lifeLeavesTile.setValue(fmt(life.getTotalLeaves()));

		renderLeafRows(lifeLeafValues, lifeLeafRows, lifeLeavesEmpty, life::getLeaves);
		lifeLeavesSection.setCount(fmt(life.getTotalLeaves()));

		lifeEventTable.render(life::getEventCount, life::getBarkForEvent, life.getEventsSeen());
		lifeEventsSection.setCount(fmt(life.getEventsSeen()));

		lifeLogsChart.setData(life.getLogsByType());
		lifeLogsSection.setCount(fmt(life.getLogs()));

		LocalDate today = LocalDate.now();
		double[] barkPerDay = new double[14];
		double[] logsPerDay = new double[14];
		double[] eventsPerDay = new double[14];
		for (int i = 0; i < 14; i++)
		{
			LocalDate day = today.minusDays(13 - i);
			DayStats ds = life.getDays().get(day.toString());
			barkPerDay[i] = ds == null ? 0 : ds.bark;
			logsPerDay[i] = ds == null ? 0 : ds.logs;
			eventsPerDay[i] = ds == null ? 0 : ds.events;
		}
		String xStart = DAY_LABEL.format(today.minusDays(13));
		String xEnd = DAY_LABEL.format(today);
		lifeBarkPerDay.setData(barkPerDay, null, xStart, xEnd);
		lifeLogsPerDay.setData(logsPerDay, null, xStart, xEnd);
		lifeEventsPerDay.setData(eventsPerDay, null, xStart, xEnd);
	}

	private void setStatus(Color color, String text)
	{
		statusDot.setForeground(color);
		statusText.setText(text);
	}

	private void renderLastEvent(String name, int bark, Duration since, boolean hasData)
	{
		if (!hasData)
		{
			lastEventName.setText("—");
			lastEventBark.setText("");
			lastEventTime.setText("Start a Forestry event to see your stats.");
			return;
		}
		lastEventName.setText(name);
		lastEventBark.setText("+" + fmt(bark) + " bark");
		lastEventTime.setText(RelativeTime.ago(since));
	}

	private static void renderLeafRows(Map<LeafType, JLabel> values, Map<LeafType, JPanel> rows, JLabel empty, ToIntFunction<LeafType> countFn)
	{
		boolean any = false;
		for (LeafType type : LeafType.values())
		{
			int count = countFn == null ? 0 : countFn.applyAsInt(type);
			values.get(type).setText(fmt(count));
			rows.get(type).setVisible(count > 0);
			any |= count > 0;
		}
		empty.setVisible(!any);
	}

	/**
	 * Updates the fixed pool of up to {@link #recentEventRows}.length reusable rows in place
	 * (toggling visibility rather than removing/re-adding components) since relative-time text
	 * needs refreshing every tick regardless of whether the history itself changed.
	 */
	private void renderRecentEvents(List<EventRecord> history)
	{
		int n = Math.min(recentEventRows.length, history.size());
		recentEventsEmpty.setVisible(n == 0);
		for (int i = 0; i < recentEventRows.length; i++)
		{
			if (i < n)
			{
				recentEventRows[i].update(history.get(i));
				recentEventRows[i].panel.setVisible(true);
			}
			else
			{
				recentEventRows[i].panel.setVisible(false);
			}
		}
	}

	/** One reusable "name / +bark / relative time" row in the "Recent events" card. */
	private static final class RecentEventRow
	{
		final JPanel panel = new JPanel();
		final JLabel name = new JLabel();
		final JLabel bark = new JLabel();
		final JLabel time = new JLabel();

		RecentEventRow(boolean divider)
		{
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(false);
			panel.setAlignmentX(LEFT_ALIGNMENT);
			panel.setBorder(divider
				? new CompoundBorder(new MatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(5, 0, 5, 0))
				: new EmptyBorder(0, 0, 5, 0));

			JPanel line1 = new JPanel(new BorderLayout());
			line1.setOpaque(false);
			name.setFont(FontManager.getRunescapeSmallFont());
			bark.setFont(FontManager.getRunescapeSmallFont());
			bark.setForeground(ColorScheme.BRAND_ORANGE);
			line1.add(name, BorderLayout.WEST);
			line1.add(bark, BorderLayout.EAST);
			panel.add(line1);

			time.setFont(FontManager.getRunescapeSmallFont());
			time.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(time);
		}

		void update(EventRecord record)
		{
			name.setText(record.getEventName());
			name.setForeground(record.getEvent() == null ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
			bark.setText("+" + fmt(record.getBark()));
			Instant when = record.getEnd() != null ? record.getEnd() : record.getStart();
			time.setText(RelativeTime.ago(Duration.between(when, Instant.now())));
		}
	}

	// --- shared small builders ---------------------------------------------------------------------------------

	private static JPanel borderedRow(String label, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(6, 7, 6, 7)));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		JLabel l = new JLabel(label);
		l.setForeground(Color.WHITE);
		l.setFont(FontManager.getRunescapeFont());
		row.add(l, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private static JPanel plainRow(String label, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel l = new JLabel(label);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(l, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private static JLabel smallValueLabel()
	{
		JLabel label = new JLabel("0", SwingConstants.RIGHT);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	private static JLabel mutedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel linkLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return label;
	}

	private static JPanel threeColRow(java.awt.Component name, JLabel count, JLabel bark)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(name, BorderLayout.CENTER);

		Dimension countSize = new Dimension(28, 16);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		count.setPreferredSize(countSize);
		count.setMinimumSize(countSize);
		count.setMaximumSize(countSize);

		Dimension barkSize = new Dimension(40, 16);
		bark.setHorizontalAlignment(SwingConstants.RIGHT);
		bark.setPreferredSize(barkSize);
		bark.setMinimumSize(barkSize);
		bark.setMaximumSize(barkSize);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		right.setOpaque(false);
		right.add(count);
		right.add(bark);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static String fmt(long v)
	{
		return NUMBER_FORMAT.format(v);
	}

	/**
	 * The "Event | Count | Bark" breakdown table shared by the session (top-3 + show-all toggle) and
	 * lifetime (always-all, no toggle) tabs.
	 */
	private final class EventTable
	{
		private final JPanel panel = new JPanel();
		private final JPanel headerRow;
		private final JPanel rows = new JPanel();
		private final JLabel empty;
		private final JLabel toggle;
		private final Map<ForestryEvent, JPanel> rowPanels = new EnumMap<>(ForestryEvent.class);
		private final Map<ForestryEvent, JLabel> countLabels = new EnumMap<>(ForestryEvent.class);
		private final Map<ForestryEvent, JLabel> barkLabels = new EnumMap<>(ForestryEvent.class);
		private final boolean toggleable;
		private List<ForestryEvent> renderedOrder = null;
		private boolean showAll;

		EventTable(boolean toggleable, String emptyMessage)
		{
			this.toggleable = toggleable;
			this.empty = mutedLabel(emptyMessage);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(false);
			panel.setAlignmentX(LEFT_ALIGNMENT);

			JLabel headerName = mutedLabel("Event");
			JLabel headerCount = mutedLabel("Count");
			JLabel headerBark = mutedLabel("Bark");
			headerRow = threeColRow(headerName, headerCount, headerBark);
			panel.add(headerRow);

			rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
			rows.setOpaque(false);
			rows.setAlignmentX(LEFT_ALIGNMENT);
			for (ForestryEvent e : ForestryEvent.values())
			{
				JLabel count = smallValueLabel();
				JLabel bark = smallValueLabel();
				countLabels.put(e, count);
				barkLabels.put(e, bark);
				JLabel name = mutedLabel(e.getDisplayName());
				rowPanels.put(e, threeColRow(name, count, bark));
			}
			panel.add(rows);
			panel.add(empty);

			if (toggleable)
			{
				toggle = linkLabel("");
				toggle.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						showAll = !showAll;
						refresh();
					}
				});
				panel.add(toggle);
			}
			else
			{
				toggle = null;
			}
		}

		void render(ToIntFunction<ForestryEvent> countFn, ToIntFunction<ForestryEvent> barkFn, int totalSeen)
		{
			boolean any = totalSeen > 0;
			headerRow.setVisible(any);
			rows.setVisible(any);
			empty.setVisible(!any);
			if (toggle != null)
			{
				toggle.setVisible(any);
			}
			if (!any)
			{
				return;
			}

			List<ForestryEvent> order = new ArrayList<>(Arrays.asList(ForestryEvent.values()));
			order.sort((a, b) ->
			{
				int c = Integer.compare(countFn.applyAsInt(b), countFn.applyAsInt(a));
				return c != 0 ? c : Integer.compare(a.ordinal(), b.ordinal());
			});

			boolean all = !toggleable || showAll;
			List<ForestryEvent> shown = all ? order : order.subList(0, Math.min(3, order.size()));

			for (ForestryEvent e : shown)
			{
				countLabels.get(e).setText(fmt(countFn.applyAsInt(e)));
				barkLabels.get(e).setText(fmt(barkFn.applyAsInt(e)));
			}

			if (!shown.equals(renderedOrder))
			{
				rows.removeAll();
				for (ForestryEvent e : shown)
				{
					rows.add(rowPanels.get(e));
				}
				rows.revalidate();
				rows.repaint();
				renderedOrder = new ArrayList<>(shown);
			}

			if (toggle != null)
			{
				toggle.setText(showAll ? "Show top 3 ‹" : "Show all " + ForestryEvent.values().length + " event types ›");
			}
		}
	}
}
