package com.forestrytracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure chat-message parsing for Forestry-related game messages. No RuneLite client dependency so
 * it can be unit tested directly.
 *
 * <p>In-game chat lines dress plain text up with colour tags (e.g. {@code <col=0000ff>...</col>})
 * and, for some Forestry messages, chatbox macros (e.g. {@code @mes_hl_blu@}). {@link
 * #stripFormatting(String)} normalises a raw message to plain text before any of the {@code
 * parse*} methods look at it.
 */
final class ChatParser
{
	/**
	 * Bark award message. The core Woodcutting plugin matches
	 * "You've been awarded <col=..>N Anima-infused bark</col>."; this is slightly looser about the
	 * apostrophe and casing, and is applied after {@link #stripFormatting(String)} has already
	 * removed any colour tags or macros.
	 */
	private static final Pattern ANIMA_BARK_PATTERN = Pattern.compile(
		"You(?:'|’)?ve been awarded (\\d+) Anima-infused bark\\.?|You have been awarded (\\d+) Anima-infused bark\\.?",
		Pattern.CASE_INSENSITIVE);

	/** Leaves picked up into the forestry kit; the leaf-type word is absent for plain "leaves". */
	private static final Pattern LEAF_PATTERN = Pattern.compile(
		"Some (?:(oak|willow|maple|yew|magic) )?leaves fall to the ground and you place them into your Forestry kit\\.?",
		Pattern.CASE_INSENSITIVE);

	/** Log-cutting message; matches the same text the core Woodcutting plugin keys off. */
	private static final Pattern LOG_CUT_PATTERN = Pattern.compile(
		"You get (?:some|an) ([\\w ]*?(?:logs?|mushrooms))\\.?",
		Pattern.CASE_INSENSITIVE);

	/**
	 * Leprechaun's Luck: after standing in the rainbow, the next several log cuts each pay bark. This
	 * line precedes the normal "You've been awarded ..." award and is the only way to tell that bark
	 * apart from other awards, since it arrives while chopping and often after the leprechaun has left.
	 */
	private static final Pattern LEPRECHAUN_LUCK_PATTERN = Pattern.compile(
		"You use (?:the last of )?your leprechaun(?:'|’)s luck to gather some Anima-infused bark\\.?",
		Pattern.CASE_INSENSITIVE);

	/** Strips HTML-ish colour tags (e.g. {@code <col=0000ff>}, {@code </col>}) from a chat message. */
	private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");

	/** Strips chatbox macros such as {@code @mes_hl_blu@}. */
	private static final Pattern MACRO_PATTERN = Pattern.compile("@[^@]*@");

	private ChatParser()
	{
	}

	/**
	 * Removes every {@code <...>} tag and every {@code @...@} chat macro, collapses doubled spaces
	 * left behind, and trims the result.
	 */
	static String stripFormatting(String msg)
	{
		String stripped = TAG_PATTERN.matcher(msg).replaceAll("");
		stripped = MACRO_PATTERN.matcher(stripped).replaceAll("");
		stripped = stripped.replaceAll(" {2,}", " ");
		return stripped.trim();
	}

	/**
	 * Parses a "You've been awarded N Anima-infused bark." message (after {@link
	 * #stripFormatting(String)}). Returns -1 if {@code msg} isn't a bark award message.
	 */
	static int parseBark(String msg)
	{
		Matcher m = ANIMA_BARK_PATTERN.matcher(stripFormatting(msg));
		if (!m.find())
		{
			return -1;
		}
		String amount = m.group(1) != null ? m.group(1) : m.group(2);
		return Integer.parseInt(amount);
	}

	/** True for the "You use your leprechaun's luck to gather some Anima-infused bark." line. */
	static boolean isLeprechaunLuck(String msg)
	{
		return LEPRECHAUN_LUCK_PATTERN.matcher(stripFormatting(msg)).find();
	}

	/**
	 * Parses a "Some [type ]leaves fall to the ground and you place them into your Forestry kit."
	 * message (after {@link #stripFormatting(String)}) into the {@link LeafType} collected. Returns
	 * null if {@code msg} doesn't match (including leaves that fell but were not collected).
	 */
	static LeafType parseLeaves(String msg)
	{
		Matcher m = LEAF_PATTERN.matcher(stripFormatting(msg));
		if (!m.matches())
		{
			return null;
		}
		String type = m.group(1);
		if (type == null)
		{
			return LeafType.NORMAL;
		}
		switch (type.toLowerCase())
		{
			case "oak":
				return LeafType.OAK;
			case "willow":
				return LeafType.WILLOW;
			case "maple":
				return LeafType.MAPLE;
			case "yew":
				return LeafType.YEW;
			case "magic":
				return LeafType.MAGIC;
			default:
				return null;
		}
	}

	/**
	 * Parses a "You get some/an &lt;type&gt;." log-cutting message (after {@link
	 * #stripFormatting(String)}) into a capitalised type name (e.g. "Willow logs"). Returns null if
	 * {@code msg} doesn't match.
	 */
	static String parseLog(String msg)
	{
		Matcher m = LOG_CUT_PATTERN.matcher(stripFormatting(msg));
		if (!m.find())
		{
			return null;
		}
		return capitalize(m.group(1));
	}

	private static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
