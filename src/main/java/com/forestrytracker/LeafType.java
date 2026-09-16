package com.forestrytracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * Leaf items that Forestry can award / store in the forestry kit.
 */
@Getter
@RequiredArgsConstructor
public enum LeafType
{
	NORMAL("Leaves", ItemID.LEAVES),
	OAK("Oak leaves", ItemID.LEAVES_OAK),
	WILLOW("Willow leaves", ItemID.LEAVES_WILLOW),
	MAPLE("Maple leaves", ItemID.LEAVES_MAPLE),
	YEW("Yew leaves", ItemID.LEAVES_YEW),
	MAGIC("Magic leaves", ItemID.LEAVES_MAGIC);

	private final String displayName;
	private final int itemId;

	public static LeafType fromItemId(int itemId)
	{
		for (LeafType type : values())
		{
			if (type.itemId == itemId)
			{
				return type;
			}
		}
		return null;
	}
}
