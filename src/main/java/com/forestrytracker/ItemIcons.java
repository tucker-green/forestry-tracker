package com.forestrytracker;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Small helper for loading a game item's sprite into a {@link JLabel}, resized for the side panel.
 */
final class ItemIcons
{
	private ItemIcons()
	{
	}

	/** Asynchronously (or immediately, if already cached) sets {@code label}'s icon to item {@code itemId}. */
	static void apply(ItemManager itemManager, int itemId, JLabel label, int size)
	{
		if (itemManager == null)
		{
			return;
		}
		AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable set = () -> label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, size, size)));
		img.onLoaded(set);
		set.run();
	}
}
