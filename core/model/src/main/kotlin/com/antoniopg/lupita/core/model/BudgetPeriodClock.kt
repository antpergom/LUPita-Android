package com.antoniopg.lupita.core.model

import java.time.Instant
import java.time.ZoneId

/**
 * Cuando empieza el periodo actual del tope global (F4 paso 3), segun [BudgetPeriod]. `java.time.*` es
 * JVM puro (no `android.*`), asi que esto vive en `core:model` y se prueba sin Android.
 */
object BudgetPeriodClock {
    fun periodStartMillis(period: BudgetPeriod, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val startOfDay = now.toLocalDate().atStartOfDay(zone)
        val start = when (period) {
            BudgetPeriod.DAILY -> startOfDay
            BudgetPeriod.MONTHLY -> startOfDay.withDayOfMonth(1)
        }
        return start.toInstant().toEpochMilli()
    }
}
