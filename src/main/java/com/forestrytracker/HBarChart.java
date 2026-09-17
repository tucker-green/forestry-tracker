package com.forestrytracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Horizontal bar breakdown (label, bar, value) sorted largest first. Zero rows are hidden.
 */
class HBarChart extends JComponent
{
	private static final int ROW_H = 15;
	private static final int LABEL_W = 78;
	private static final int VALUE_W = 34;
	private static final int TITLE_H = 14;

	private final String title;
	private final Color color;
	private final List<String> labels = new ArrayList<>();
	private final List<Double> values = new ArrayList<>();

	HBarChart(String title, Color color)
	{
		this.title = title;
		this.color = color;
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFont(FontManager.getRunescapeSmallFont());
		updateSize();
	}

	/** Replaces the rows; insertion order of {@code data} is ignored, rows are sorted by value. */
	void setData(Map<String, ? extends Number> data)
	{
		labels.clear();
		values.clear();
		List<Map.Entry<String, ? extends Number>> entries = new ArrayList<>(data.entrySet());
		entries.sort((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()));
		for (Map.Entry<String, ? extends Number> e : entries)
		{
			double v = e.getValue().doubleValue();
			if (v > 0)
			{
				labels.add(e.getKey());
				values.add(v);
			}
		}
		updateSize();
		revalidate();
		repaint();
	}

	private void updateSize()
	{
		int rows = Math.max(1, values.size());
		int h = TITLE_H + rows * ROW_H + 4;
		setPreferredSize(new Dimension(180, h));
		setMinimumSize(new Dimension(100, h));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();

			g2.setColor(BarChart.TEXT);
			g2.drawString(title, 2, fm.getAscent() + 1);

			if (values.isEmpty())
			{
				g2.setColor(BarChart.TEXT_MUTED);
				g2.drawString("No data yet", 2, TITLE_H + fm.getAscent() + 1);
				return;
			}

			double max = 0;
			for (double v : values)
			{
				max = Math.max(max, v);
			}
			int barX = LABEL_W;
			int barW = Math.max(10, getWidth() - LABEL_W - VALUE_W - 4);

			for (int i = 0; i < values.size(); i++)
			{
				int y = TITLE_H + i * ROW_H;
				int textY = y + (ROW_H + fm.getAscent()) / 2 - 1;

				String label = ellipsize(labels.get(i), fm, LABEL_W - 6);
				g2.setColor(BarChart.TEXT_MUTED);
				g2.drawString(label, 2, textY);

				int w = (int) Math.round(barW * values.get(i) / max);
				g2.setColor(color);
				g2.fill(new RoundRectangle2D.Float(barX, y + 3, Math.max(2, w), ROW_H - 6, 4, 4));

				String value = BarChart.format(values.get(i));
				g2.setColor(BarChart.TEXT);
				g2.drawString(value, getWidth() - VALUE_W + (VALUE_W - fm.stringWidth(value)) - 2, textY);
			}
		}
		finally
		{
			g2.dispose();
		}
	}

	private static String ellipsize(String s, FontMetrics fm, int maxWidth)
	{
		if (fm.stringWidth(s) <= maxWidth)
		{
			return s;
		}
		String dots = "…";
		int end = s.length();
		while (end > 1 && fm.stringWidth(s.substring(0, end) + dots) > maxWidth)
		{
			end--;
		}
		return s.substring(0, end) + dots;
	}
}
