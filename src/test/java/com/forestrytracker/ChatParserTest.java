package com.forestrytracker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChatParserTest
{
	// --- stripFormatting -----------------------------------------------------------------------------------------

	@Test
	public void stripFormattingRemovesTagsAndMacros()
	{
		assertEquals("You've been awarded 11 Anima-infused bark.",
			ChatParser.stripFormatting("You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));
		assertEquals("You've been awarded 11 Anima-infused bark.",
			ChatParser.stripFormatting("You've been awarded <col=0000ff>11 Anima-infused bark</col>."));
	}

	@Test
	public void stripFormattingCollapsesDoubledSpacesAndTrims()
	{
		assertEquals("Some willow leaves fall.",
			ChatParser.stripFormatting("  Some <col=ff0000>willow</col>  leaves fall.  "));
	}

	// --- parseBark: real captured messages -------------------------------------------------------------------

	@Test
	public void parseBarkSpam11()
	{
		assertEquals(11, ChatParser.parseBark("You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));
	}

	@Test
	public void parseBarkSpam21()
	{
		assertEquals(21, ChatParser.parseBark("You've been awarded @mes_hl_blu@21 Anima-infused bark</col>."));
	}

	@Test
	public void parseBarkGamemessage143()
	{
		assertEquals(143, ChatParser.parseBark("You've been awarded @mes_hl_blu@143 Anima-infused bark</col>."));
	}

	@Test
	public void parseBarkLegacyColTagFormat()
	{
		assertEquals(11, ChatParser.parseBark("You've been awarded <col=0000ff>11 Anima-infused bark</col>."));
	}

	@Test
	public void parseBarkCurlyApostrophe()
	{
		assertEquals(11, ChatParser.parseBark("You’ve been awarded @mes_hl_blu@11 Anima-infused bark</col>."));
	}

	@Test
	public void parseBarkStraightApostrophe()
	{
		assertEquals(11, ChatParser.parseBark("You've been awarded 11 Anima-infused bark."));
	}

	@Test
	public void parseBarkYouHaveVariant()
	{
		assertEquals(11, ChatParser.parseBark("You have been awarded 11 Anima-infused bark."));
	}

	@Test
	public void parseBarkCaseInsensitive()
	{
		assertEquals(11, ChatParser.parseBark("you've been awarded 11 anima-infused bark."));
	}

	@Test
	public void parseBarkNoTrailingPeriod()
	{
		assertEquals(11, ChatParser.parseBark("You've been awarded 11 Anima-infused bark"));
	}

	@Test
	public void parseBarkReturnsMinusOneForUnrelatedMessage()
	{
		assertEquals(-1, ChatParser.parseBark("Well done, you completed 8 steps of the ritual! The Dryad has left you a gift..."));
	}

	@Test
	public void parseBarkReturnsMinusOneForPublicChatMentioningBark()
	{
		assertEquals(-1, ChatParser.parseBark("does anyone want to trade anima-infused bark for gp?"));
	}

	// --- parseLeaves -----------------------------------------------------------------------------------------

	@Test
	public void parseLeavesNormal()
	{
		assertEquals(LeafType.NORMAL, ChatParser.parseLeaves(
			"Some leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesOak()
	{
		assertEquals(LeafType.OAK, ChatParser.parseLeaves(
			"Some oak leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesWillow()
	{
		assertEquals(LeafType.WILLOW, ChatParser.parseLeaves(
			"Some willow leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesMaple()
	{
		assertEquals(LeafType.MAPLE, ChatParser.parseLeaves(
			"Some maple leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesYew()
	{
		assertEquals(LeafType.YEW, ChatParser.parseLeaves(
			"Some yew leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesMagic()
	{
		assertEquals(LeafType.MAGIC, ChatParser.parseLeaves(
			"Some magic leaves fall to the ground and you place them into your Forestry kit."));
	}

	@Test
	public void parseLeavesReturnsNullWhenNotCollected()
	{
		assertNull(ChatParser.parseLeaves("Some leaves fall to the ground."));
	}

	@Test
	public void parseLeavesReturnsNullForUnrelatedMessage()
	{
		assertNull(ChatParser.parseLeaves("You get some willow logs."));
	}

	@Test
	public void parseLeavesCaseInsensitive()
	{
		assertEquals(LeafType.WILLOW, ChatParser.parseLeaves(
			"some WILLOW LEAVES fall to the ground and you place them into your forestry kit."));
	}

	// --- parseLog --------------------------------------------------------------------------------------------

	@Test
	public void parseLogWillow()
	{
		assertEquals("Willow logs", ChatParser.parseLog("You get some willow logs."));
	}

	@Test
	public void parseLogPlain()
	{
		assertEquals("Logs", ChatParser.parseLog("You get some logs."));
	}

	@Test
	public void parseLogMushrooms()
	{
		assertEquals("Mushrooms", ChatParser.parseLog("You get some mushrooms."));
	}

	@Test
	public void parseLogAnVariant()
	{
		assertEquals("Oak logs", ChatParser.parseLog("You get an oak logs."));
	}

	@Test
	public void parseLogReturnsNullForUnrelatedMessage()
	{
		assertNull(ChatParser.parseLog("You've been awarded 11 Anima-infused bark."));
	}

	// --- real captured message: ritual completion must not be mistaken for bark/leaves -----------------------

	@Test
	public void ritualCompletionMessageIsNotBarkOrLeaves()
	{
		String msg = "@mes_hl_blu@Well done, you completed 8 steps of the ritual! The Dryad has left you a gift...</col>";
		assertEquals(-1, ChatParser.parseBark(msg));
		assertNull(ChatParser.parseLeaves(msg));
		assertNull(ChatParser.parseLog(msg));
	}
}
