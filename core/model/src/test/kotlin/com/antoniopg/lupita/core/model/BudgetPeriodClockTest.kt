package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BudgetPeriodClockTest {
    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun `daily period starts at midnight of the same day`() {
        val now = ZonedDateTime.of(2026, 9, 23, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 9, 23, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, BudgetPeriodClock.periodStartMillis(BudgetPeriod.DAILY, now, zone))
    }

    @Test
    fun `monthly period starts on the first day of the same month`() {
        val now = ZonedDateTime.of(2026, 9, 23, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, BudgetPeriodClock.periodStartMillis(BudgetPeriod.MONTHLY, now, zone))
    }

    @Test
    fun `monthly period does not leak into the previous month on day 1`() {
        val now = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, BudgetPeriodClock.periodStartMillis(BudgetPeriod.MONTHLY, now, zone))
    }
}
