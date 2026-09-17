package com.forestrytracker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BarChartTest
{
	@Test
	public void wholeNumbersBelowAThousandAreExact()
	{
		assertEquals("0", BarChart.format(0));
		assertEquals("42", BarChart.format(42));
		assertEquals("999", BarChart.format(999));
	}

	@Test
	public void thousandsRangeKeepsOneDecimalOfPrecision()
	{
		// 12,400 must read as "12.4k", not be truncated to whole thousands like "12k".
		assertEquals("12.4k", BarChart.format(12_400));
		assertEquals("1.0k", BarChart.format(1_000));
		assertEquals("999.9k", BarChart.format(999_900));
	}

	@Test
	public void millionsRangeDropsToOneDecimal()
	{
		assertEquals("1.0m", BarChart.format(1_000_000));
		assertEquals("2.5m", BarChart.format(2_500_000));
	}
}
