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
 * Single-series vertical bar chart for the side panel: a title row, a recessive two-line grid with axis
 * labels in a gutter sized to fit them, uniform thin rounded bars with a consistent gap, the largest bar
 * direct-labelled, first/last x labels, an optional dimmed "in progress" last bar, and per-bar tooltips.
 */
class BarChart extends JComponent
{
	static final Color GRID = new Color(58, 58, 58);
	static final Color TEXT_MUTED = new Color(160, 160, 160);
	static final Color TEXT = Color.WHITE;

	private static final int PAD_RIGHT = 6;
	private static final int GUTTER_GAP = 5;
	private static final int TITLE_GAP = 4;
	private static final int X_LABEL_GAP = 3;

	private final String title;
	private final Color color;
	private final Color partialColor;
	private double[] values = new double[0];
	private String[] tooltips = new String[0];
	private String xStart = "";
	private String xEnd = "";
	private String emptyText = "No data yet";
	private boolean partialLast;

	BarChart(String title, Color color, int height)
	{
		this.title = title;
		this.color = color;
		this.partialColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 110);
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFont(FontManager.getRunescapeSmallFont());
		setAlignmentX(LEFT_ALIGNMENT);
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

	/** When true the last bar is drawn dimmed to show it is still accumulating. */
	void setPartialLast(boolean partialLast)
	{
		if (this.partialLast != partialLast)
		{
			this.partialLast = partialLast;
			repaint();
		}
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		Layout l = layout(getFontMetrics(getFont()));
		int i = barAt(e.getX(), l);
		if (i < 0)
		{
			return null;
		}
		String base = i < tooltips.length && tooltips[i] != null ? tooltips[i] : format(values[i]);
		return partialLast && i == values.length - 1 ? base + " (in progress)" : base;
	}

	private int barAt(int x, Layout l)
	{
		int n = values.length;
		if (n == 0 || x < l.plotX || l.plotW <= 0)
		{
			return -1;
		}
		return Math.min(n - 1, (x - l.plotX) * n / l.plotW);
	}

	/** Pixel geometry derived from the current size, font and data. */
	private static final class Layout
	{
		int plotX;
		int plotY;
		int plotW;
		int plotH;
		double niceMax;
		String topLabel;
		String midLabel;
	}

	private Layout layout(FontMetrics fm)
	{
		Layout l = new Layout();
		double max = 0;
		for (double v : values)
		{
			max = Math.max(max, v);
		}
		l.niceMax = niceCeiling(max);
		l.topLabel = format(l.niceMax);
		l.midLabel = format(l.niceMax / 2);
		int gutter = Math.max(fm.stringWidth(l.topLabel), fm.stringWidth(l.midLabel)) + GUTTER_GAP;

		int titleH = fm.getHeight() + TITLE_GAP;
		int valueLabelH = fm.getHeight();
		l.plotX = gutter;
		l.plotY = titleH + valueLabelH;
		l.plotW = getWidth() - gutter - PAD_RIGHT;
		l.plotH = getHeight() - l.plotY - (fm.getHeight() + X_LABEL_GAP);
		return l;
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
			Layout l = layout(fm);

			g2.setColor(TEXT);
			g2.drawString(title, 0, fm.getAscent());

			if (l.plotW <= 0 || l.plotH <= 0)
			{
				return;
			}

			boolean empty = true;
			for (double v : values)
			{
				if (v > 0)
				{
					empty = false;
					break;
				}
			}
			if (empty)
			{
				g2.setColor(TEXT_MUTED);
				int tw = fm.stringWidth(emptyText);
				g2.drawString(emptyText, l.plotX + Math.max(0, (l.plotW - tw) / 2), l.plotY + l.plotH / 2 + fm.getAscent() / 2);
				return;
			}

			int baseline = l.plotY + l.plotH;

			// Grid: baseline, mid, top; labels on mid and top.
			g2.setStroke(new BasicStroke(1f));
			for (int i = 0; i <= 2; i++)
			{
				int y = baseline - (l.plotH * i) / 2;
				g2.setColor(GRID);
				g2.drawLine(l.plotX, y, l.plotX + l.plotW, y);
				if (i > 0)
				{
					String label = i == 2 ? l.topLabel : l.midLabel;
					g2.setColor(TEXT_MUTED);
					g2.drawString(label, l.plotX - GUTTER_GAP - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
				}
			}

			// Bars: uniform width, centred in equal slots.
			int n = values.length;
			double slot = (double) l.plotW / n;
			int gap = slot >= 6 ? 2 : 1;
			int barW = Math.max(2, (int) Math.floor(slot) - gap);
			int maxIdx = -1;
			for (int i = 0; i < n; i++)
			{
				if (values[i] > 0 && (maxIdx < 0 || values[i] > values[maxIdx]))
				{
					maxIdx = i;
				}
			}
			for (int i = 0; i < n; i++)
			{
				double v = values[i];
				if (v <= 0)
				{
					continue;
				}
				int barH = Math.max(2, (int) Math.round(l.plotH * v / l.niceMax));
				int x = barX(i, l, slot, barW);
				int y = baseline - barH;
				g2.setColor(partialLast && i == n - 1 ? partialColor : color);
				int radius = Math.min(4, barW);
				// Rounded top; the bottom is squared off against the baseline.
				g2.fill(new RoundRectangle2D.Float(x, y, barW, barH, radius, radius));
				g2.fillRect(x, baseline - Math.min(barH, radius), barW, Math.min(barH, radius));
			}

			// Direct label on the largest bar, in the reserved row above the plot when needed.
			if (maxIdx >= 0)
			{
				String label = format(values[maxIdx]);
				int barH = Math.max(2, (int) Math.round(l.plotH * values[maxIdx] / l.niceMax));
				int cx = barX(maxIdx, l, slot, barW) + barW / 2;
				int x = Math.max(l.plotX, Math.min(cx - fm.stringWidth(label) / 2, l.plotX + l.plotW - fm.stringWidth(label)));
				int y = baseline - barH - 3;
				g2.setColor(TEXT);
				g2.drawString(label, x, y);
			}

			// X labels.
			g2.setColor(TEXT_MUTED);
			int ty = getHeight() - 2;
			g2.drawString(xStart, l.plotX, ty);
			g2.drawString(xEnd, l.plotX + l.plotW - fm.stringWidth(xEnd), ty);
		}
		finally
		{
			g2.dispose();
		}
	}

	private static int barX(int i, Layout l, double slot, int barW)
	{
		return l.plotX + (int) Math.round(i * slot + (slot - barW) / 2);
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
			return trimDecimal(v / 1_000_000) + "m";
		}
		if (v >= 1000)
		{
			return trimDecimal(v / 1000) + "k";
		}
		if (v == Math.floor(v))
		{
			return Long.toString((long) v);
		}
		return String.format("%.1f", v);
	}

	/** "3" for 3.0, "2.5" for 2.5. */
	private static String trimDecimal(double v)
	{
		double rounded = Math.round(v * 10) / 10.0;
		if (rounded == Math.floor(rounded))
		{
			return Long.toString((long) rounded);
		}
		return String.format("%.1f", rounded);
	}
}
