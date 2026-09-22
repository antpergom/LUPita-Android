package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Coste en micro-dolares (1 USD = 1 000 000). Evita el error de coma flotante al sumar muchas llamadas
 * pequeñas (tokens a fracciones de centimo) — el mismo motivo por el que Google Ads factura en micros.
 * Es la unidad comun para tokens de LLM y llamadas externas de pago (ROADMAP F4: "tokens y llamadas
 * externas de pago" comparten el mismo contador).
 */
@JvmInline
value class CostMicros(val value: Long) : Comparable<CostMicros> {
    operator fun plus(other: CostMicros) = CostMicros(value + other.value)
    operator fun minus(other: CostMicros) = CostMicros(value - other.value)
    override fun compareTo(other: CostMicros): Int = value.compareTo(other.value)

    fun toUsd(): Double = value / 1_000_000.0

    companion object {
        val ZERO = CostMicros(0)

        fun ofUsd(usd: Double): CostMicros = CostMicros((usd * 1_000_000).toLong())
    }
}

/** Con que frecuencia se reinicia el tope global de gasto. De fabrica: mensual (el ciclo habitual de facturacion de un proveedor). */
enum class BudgetPeriod(val key: String) {
    DAILY("daily"),
    MONTHLY("monthly"),
    ;

    companion object {
        val DEFAULT = MONTHLY

        fun fromKey(key: String?): BudgetPeriod? = entries.firstOrNull { it.key == key }
    }
}

/** Tope de una profundidad: cuanto se puede gastar y cuantas llamadas de pago se permiten en una misma seleccion. */
data class DepthBudget(val costLimit: CostMicros, val maxPaidCalls: Int)

/**
 * Los tres topes duros (decision 2026-09-22): por profundidad, por herramienta, y un tope global que se
 * reinicia por periodo. Todo editable desde Ajustes; los valores de fabrica vienen de [BudgetDefaults]
 * (datos, no constantes en Kotlin) para poder variarlos por usuario o region en el futuro sin tocar codigo.
 */
data class BudgetSettings(
    val depthBudgets: Map<Depth, DepthBudget> = emptyMap(),
    val toolLimits: Map<ToolId, CostMicros> = emptyMap(),
    val globalLimit: CostMicros? = null,
    val globalPeriod: BudgetPeriod = BudgetPeriod.DEFAULT,
) {
    fun depthBudget(depth: Depth, defaults: BudgetDefaults): DepthBudget = depthBudgets[depth] ?: defaults.depthBudget(depth)

    fun toolLimit(tool: ToolId, defaults: BudgetDefaults): CostMicros = toolLimits[tool] ?: defaults.toolLimit(tool)

    fun effectiveGlobalLimit(defaults: BudgetDefaults): CostMicros = globalLimit ?: defaults.globalLimit
}

/** Contrato de la configuracion de presupuesto. La implementacion (DataStore) vive en `:app`. */
interface BudgetSettingsRepository {
    val settings: Flow<BudgetSettings>

    suspend fun setDepthBudget(depth: Depth, budget: DepthBudget)
    suspend fun setToolLimit(tool: ToolId, limit: CostMicros)
    suspend fun setGlobalLimit(limit: CostMicros)
    suspend fun setGlobalPeriod(period: BudgetPeriod)

    /** Vuelve todos los topes a los valores de fabrica de [BudgetDefaults]. */
    suspend fun resetToDefaults()
}
