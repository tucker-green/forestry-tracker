package com.forestrytracker;

/**
 * Small piece of state carried between consecutive chat messages by
 * {@link ForestryTrackerPlugin#handleChatMessage}.
 */
class ChatState
{
	/**
	 * Set by the "You use your leprechaun's luck..." line; consumed by the bark award that follows it,
	 * so that award is attributed to the Leprechaun even though it arrives while chopping and possibly
	 * after the leprechaun has despawned.
	 */
	boolean leprechaunLuckPending;

	void reset()
	{
		leprechaunLuckPending = false;
	}
}
