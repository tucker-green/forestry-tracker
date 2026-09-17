package com.forestrytracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A single bordered stat card: a small muted label on top, and a large bold number next to an
 * optional game-item icon underneath.
 */
class StatTile extends JPanel
{
	private static final Color BORDER = new Color(58, 58, 58);

	private final JLabel valueLabel = new JLabel();
	private final JLabel iconLabel = new JLabel();

	StatTile(String title)
	{
		setLayout(new BorderLayout(0, 2));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(6, 7, 6, 7)));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(titleLabel, BorderLayout.NORTH);

		valueLabel.setFont(FontManager.getRunescapeBoldFont());
		valueLabel.setForeground(java.awt.Color.WHITE);

		JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		valueRow.setOpaque(false);
		valueRow.setBorder(BorderFactory.createEmptyBorder());
		iconLabel.setVisible(false);
		valueRow.add(iconLabel);
		valueRow.add(valueLabel);
		add(valueRow, BorderLayout.CENTER);
	}

	void setIcon(ItemManager itemManager, int itemId)
	{
		iconLabel.setVisible(true);
		ItemIcons.apply(itemManager, itemId, iconLabel, 20);
	}

	void setValue(String text)
	{
		valueLabel.setText(text);
	}

	void setValueColor(Color color)
	{
		valueLabel.setForeground(color);
	}
}
