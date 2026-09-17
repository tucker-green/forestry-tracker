package com.forestrytracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Single-series vertical bar chart for the side panel: recessive grid, thin rounded bars, the largest bar
 * direct-labelled, first/last x labels, and a per-bar hover tooltip.
 */
class BarChart extends JComponent
{
	static final Color GRID = new Color(58, 58, 58);
	static final Color TEXT_MUTED = new Color(160, 160, 160);
	static final Color TEXT = Color.WHITE;

	private static final int PAD_LEFT = 30;
	private static final int PAD_RIGHT = 4;
	private static final int PAD_TOP = 14;
	private static final int PAD_BOTTOM = 14;
	private static final int GAP = 2;

	private final String title;
	private final Color color;
	private double[] values = new double[0];
	private String[] tooltips = new String[0];
	private String xStart = "";
	private String xEnd = "";
	private String emptyText = "No data yet";

	BarChart(String title, Color color, int height)
	{
		this.title = title;
		this.color = color;
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(180, height));
		setMinimumSize(new Dimension(100, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		setToolTipText(""); // registers with ToolTipManager; text is computed per bar
	}

	void setData(double[] values, String[] tooltips, String xStart, String xEnd)
	{
		this.values = values == null ? new double[0] : values;
		this.tooltips = tooltips == null ? new String[0] : tooltips;
		this.xStart = xStart == null ? "" : xStart;
		this.xEnd = xEnd == null ? "" : xEnd;
		repaint();
	}

	void setEmptyText(String text)
	{
		emptyText = text;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int i = barAt(e.getX());
		if (i < 0 || i >= tooltips.length)
		{
			return null;
		}
		return tooltips[i];
	}

	private int barAt(int x)
	{
		int n = values.length;
		if (n == 0)
		{
			return -1;
		}
		int plotW = getWidth() - PAD_LEFT - PAD_RIGHT;
		if (x < PAD_LEFT || plotW <= 0)
		{
			return -1;
		}
		return Math.min(n - 1, (x - PAD_LEFT) * n / plotW);
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

			g2.setColor(TEXT);
			g2.drawString(title, 2, fm.getAscent() + 1);

			int w = getWidth();
			int h = getHeight();
			int plotX = PAD_LEFT;
			int plotY = PAD_TOP + 2;
			int plotW = w - PAD_LEFT - PAD_RIGHT;
			int plotH = h - plotY - PAD_BOTTOM;
			if (plotW <= 0 || plotH <= 0)
			{
				return;
			}

			double max = 0;
			for (double v : values)
			{
				max = Math.max(max, v);
			}
			if (values.length == 0 || max <= 0)
			{
				g2.setColor(TEXT_MUTED);
				int tw = fm.stringWidth(emptyText);
				g2.drawString(emptyText, plotX + (plotW - tw) / 2, plotY + plotH / 2 + fm.getAscent() / 2);
				return;
			}
			double niceMax = niceCeiling(max);

			// Grid: baseline, mid, top. Labels at top and mid only (baseline is implicitly 0).
			g2.setColor(GRID);
			g2.setStroke(new BasicStroke(1f));
			for (int i = 0; i <= 2; i++)
			{
				int y = plotY + plotH - (plotH * i) / 2;
				g2.drawLine(plotX, y, plotX + plotW, y);
				if (i > 0)
				{
					String label = format(niceMax * i / 2);
					g2.setColor(TEXT_MUTED);
					g2.drawString(label, plotX - fm.stringWidth(label) - 3, y + fm.getAscent() / 2 - 1);
					g2.setColor(GRID);
				}
			}

			// Bars.
			int n = values.length;
			double slot = (double) plotW / n;
			int barW = Math.max(1, (int) Math.floor(slot) - GAP);
			int maxIdx = -1;
			for (int i = 0; i < n; i++)
			{
				if (values[i] > 0 && (maxIdx < 0 || values[i] > values[maxIdx]))
				{
					maxIdx = i;
				}
			}
			int baseline = plotY + plotH;
			for (int i = 0; i < n; i++)
			{
				double v = values[i];
				if (v <= 0)
				{
					continue;
				}
				int barH = Math.max(2, (int) Math.round(plotH * v / niceMax));
				int x = plotX + (int) Math.round(i * slot) + GAP / 2;
				int y = baseline - barH;
				g2.setColor(color);
				// Rounded top, square bottom anchored to the baseline.
				g2.fill(new RoundRectangle2D.Float(x, y, barW, barH + 3, 4, 4));
				g2.fillRect(x, baseline - Math.min(barH, 3), barW, Math.min(barH, 3));
				g2.setColor(getBackground());
				g2.fillRect(x, baseline + 1, barW, 3);
			}

			// Direct label on the largest bar.
			if (maxIdx >= 0)
			{
				String label = format(values[maxIdx]);
				int barH = Math.max(2, (int) Math.round(plotH * values[maxIdx] / niceMax));
				int x = plotX + (int) Math.round(maxIdx * slot) + GAP / 2 + barW / 2 - fm.stringWidth(label) / 2;
				x = Math.max(plotX, Math.min(x, plotX + plotW - fm.stringWidth(label)));
				int y = baseline - barH - 2;
				if (y < plotY + fm.getAscent())
				{
					y = plotY + fm.getAscent();
				}
				g2.setColor(TEXT);
				g2.drawString(label, x, y);
			}

			// X labels.
			g2.setColor(TEXT_MUTED);
			int ty = h - 3;
			g2.drawString(xStart, plotX, ty);
			g2.drawString(xEnd, plotX + plotW - fm.stringWidth(xEnd), ty);
		}
		finally
		{
			g2.dispose();
		}
	}

	static double niceCeiling(double max)
	{
		if (max <= 0)
		{
			return 1;
		}
		double exp = Math.floor(Math.log10(max));
		double base = Math.pow(10, exp);
		double m = max / base;
		double nice = m <= 1 ? 1 : m <= 2 ? 2 : m <= 2.5 ? 2.5 : m <= 5 ? 5 : 10;
		return nice * base;
	}

	static String format(double v)
	{
		if (v >= 1_000_000)
		{
			return String.format("%.1fm", v / 1_000_000);
		}
		if (v >= 1000)
		{
			return String.format("%.1fk", v / 1000);
		}
		if (v == Math.floor(v))
		{
			return Long.toString((long) v);
		}
		return String.format("%.1f", v);
	}
}
