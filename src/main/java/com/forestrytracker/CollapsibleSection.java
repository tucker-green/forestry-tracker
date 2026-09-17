package com.forestrytracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A bordered card with a clickable header (chevron, optional icon, title, right-aligned count)
 * that shows/hides a body panel. The caller populates {@link #getBody()} once and mutates its
 * children across refreshes; this component only manages the expand/collapse chrome.
 */
class CollapsibleSection extends JPanel
{
	private static final Color BORDER = new Color(58, 58, 58);

	private final JLabel chevron = new JLabel();
	private final JLabel icon = new JLabel();
	private final JLabel countLabel = new JLabel();
	private final JPanel body = new JPanel();
	private boolean expanded;

	CollapsibleSection(String title)
	{
		this(title, null, null, true);
	}

	CollapsibleSection(String title, Integer iconItemId, ItemManager itemManager, boolean expandedByDefault)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(6, 7, 6, 7)));
		setAlignmentX(LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setAlignmentX(LEFT_ALIGNMENT);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		chevron.setFont(FontManager.getRunescapeSmallFont());
		chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		left.add(chevron);
		if (iconItemId != null)
		{
			ItemIcons.apply(itemManager, iconItemId, icon, 16);
			left.add(icon);
		}
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		left.add(titleLabel);
		header.add(left, BorderLayout.WEST);

		countLabel.setFont(FontManager.getRunescapeSmallFont());
		countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		header.add(countLabel, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!expanded);
			}
		});

		add(header);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setAlignmentX(LEFT_ALIGNMENT);
		body.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		add(body);

		setExpanded(expandedByDefault);
	}

	/**
	 * Bounds this card to its own preferred height so it can't be stretched to fill leftover
	 * vertical space in the enclosing {@code BoxLayout(Y_AXIS)} column, unlike every other row
	 * type in the panel which is explicitly bounded. Header/body use {@link BorderLayout}, whose
	 * {@code maximumLayoutSize()} is unbounded, so without this override the card's own maximum
	 * size is unbounded too.
	 */
	@Override
	public java.awt.Dimension getMaximumSize()
	{
		return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	JPanel getBody()
	{
		return body;
	}

	void setCount(String text)
	{
		countLabel.setText(text);
	}

	boolean isExpanded()
	{
		return expanded;
	}

	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		chevron.setText(expanded ? "▾" : "▸");
		body.setVisible(expanded);
		revalidate();
		repaint();
	}
}
