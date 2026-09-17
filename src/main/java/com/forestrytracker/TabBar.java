package com.forestrytracker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A two-way segmented control ("Session" | "Lifetime"): the active tab gets orange text and a 2px
 * orange bottom border, the inactive tab is muted grey.
 */
class TabBar extends JPanel
{
	private static final Color BORDER = new Color(58, 58, 58);

	private final JLabel[] tabs;
	private int selected;

	TabBar(String[] labels, IntConsumer onChange)
	{
		super(new GridLayout(1, labels.length, 0, 0));
		setBorder(BorderFactory.createLineBorder(BORDER));
		setAlignmentX(LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		tabs = new JLabel[labels.length];
		for (int i = 0; i < labels.length; i++)
		{
			int index = i;
			JLabel tab = new JLabel(labels[i], SwingConstants.CENTER);
			tab.setFont(FontManager.getRunescapeSmallFont());
			tab.setOpaque(true);
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (selected != index)
					{
						setSelected(index);
						onChange.accept(index);
					}
				}
			});
			tabs[i] = tab;
			add(tab);
		}
		setSelected(0);
	}

	void setSelected(int index)
	{
		selected = index;
		for (int i = 0; i < tabs.length; i++)
		{
			JLabel tab = tabs[i];
			tab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			if (i == index)
			{
				tab.setForeground(ColorScheme.BRAND_ORANGE);
				tab.setBorder(new CompoundBorder(
					new MatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE),
					new EmptyBorder(5, 0, 3, 0)));
			}
			else
			{
				tab.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				tab.setBorder(new EmptyBorder(5, 0, 5, 0));
			}
		}
	}

	int getSelected()
	{
		return selected;
	}
}
