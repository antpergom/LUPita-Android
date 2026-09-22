package com.antoniopg.lupita.ui.app.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.BudgetPeriod
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.ui.app.ChoiceRow
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.app.SectionLabel
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/** Una fila editable de la seccion "por profundidad": tope de coste y de llamadas de pago. */
data class BudgetDepthRow(val depth: Depth, val costLimitUsd: Double, val maxPaidCalls: Int)

/** Una fila editable de la seccion "por herramienta": solo tope de coste. */
data class BudgetToolRow(val tool: ToolId, val limitUsd: Double)

/**
 * Todo lo que la ventana de Presupuesto puede cambiar (F4 paso 4). Cada accion persiste en el acto,
 * igual que [com.antoniopg.lupita.ui.app.privacy.PrivacyActions]. `resetGeneration` cambia cada vez que
 * se restablecen los valores de fabrica: los campos de texto lo usan como `key` para releer el valor
 * nuevo en vez de conservar lo que el usuario tecleaba.
 */
class BudgetUi(
    val depths: List<BudgetDepthRow>,
    val tools: List<BudgetToolRow>,
    val globalLimitUsd: Double,
    val globalPeriod: BudgetPeriod,
    val resetGeneration: Int,
    val onDepthCostChange: (Depth, Double) -> Unit,
    val onDepthCallsChange: (Depth, Int) -> Unit,
    val onToolLimitChange: (ToolId, Double) -> Unit,
    val onGlobalLimitChange: (Double) -> Unit,
    val onGlobalPeriodChange: (BudgetPeriod) -> Unit,
    val onReset: () -> Unit,
)

/**
 * Ajustes -> Presupuesto: los 3 topes duros (decision 2026-09-22) editables desde la app. Un valor no
 * numerico simplemente no se guarda (el campo se queda con lo tecleado hasta que sea valido) - no hay
 * mensaje de error, igual de silencioso que el resto de la edicion en linea de este proyecto.
 */
@Composable
fun BudgetScreen(ui: BudgetUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val c = Lupita.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val back = stringResource(R.string.privacy_back)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = back, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(R.string.budget_title),
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.budget_intro), color = c.subtle, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.budget_depths_title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ui.depths.forEach { row ->
                DepthCard(
                    row = row,
                    resetGeneration = ui.resetGeneration,
                    onCostChange = { ui.onDepthCostChange(row.depth, it) },
                    onCallsChange = { ui.onDepthCallsChange(row.depth, it) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.budget_tools_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.tools.forEach { row ->
                MoneyField(
                    key = "tool_${row.tool.key}_${ui.resetGeneration}",
                    label = toolLabel(row.tool),
                    initialUsd = row.limitUsd,
                    onValueChange = { ui.onToolLimitChange(row.tool, it) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.budget_global_title))
        Text(stringResource(R.string.budget_global_hint), color = c.subtle, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        MoneyField(
            key = "global_${ui.resetGeneration}",
            label = stringResource(R.string.budget_global_limit),
            initialUsd = ui.globalLimitUsd,
            onValueChange = ui.onGlobalLimitChange,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetPeriod.entries.forEach { period ->
                Box(modifier = Modifier.weight(1f)) {
                    ChoiceRow(
                        label = periodLabel(period),
                        selected = period == ui.globalPeriod,
                        showCheck = true,
                        onClick = { ui.onGlobalPeriodChange(period) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        TextButton(onClick = ui.onReset) {
            Text(stringResource(R.string.budget_reset), color = c.danger)
        }
    }
}

@Composable
private fun DepthCard(
    row: BudgetDepthRow,
    resetGeneration: Int,
    onCostChange: (Double) -> Unit,
    onCallsChange: (Int) -> Unit,
) {
    val c = Lupita.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(depthLabel(row.depth), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        MoneyField(
            key = "depth_cost_${row.depth.key}_$resetGeneration",
            label = stringResource(R.string.budget_depth_cost),
            initialUsd = row.costLimitUsd,
            onValueChange = onCostChange,
        )
        IntField(
            key = "depth_calls_${row.depth.key}_$resetGeneration",
            label = stringResource(R.string.budget_depth_calls),
            initialValue = row.maxPaidCalls,
            onValueChange = onCallsChange,
        )
    }
}

/**
 * Campo de texto para un importe en USD. El valor local (`text`) se siembra UNA vez por [key] (no se
 * resincroniza en cada recomposicion): solo cambiar [key] (p. ej. al restablecer valores de fabrica)
 * hace que se vuelva a leer [initialUsd].
 */
@Composable
private fun MoneyField(key: Any, label: String, initialUsd: Double, onValueChange: (Double) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(formatUsd(initialUsd)) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toDoubleOrNull()?.let { if (it >= 0) onValueChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntField(key: Any, label: String, initialValue: Int, onValueChange: (Int) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(initialValue.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toIntOrNull()?.let { if (it >= 0) onValueChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatUsd(value: Double): String {
    // Sin ceros de sobra ("2" en vez de "2.0"), pero sin redondear el detalle real del gasto.
    val rounded = kotlin.math.round(value * 1_000_000) / 1_000_000
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun depthLabel(depth: Depth): String = stringResource(
    when (depth) {
        Depth.LOW -> R.string.depth_low
        Depth.MEDIUM -> R.string.depth_medium
        Depth.HIGH -> R.string.depth_high
    },
)

@Composable
private fun toolLabel(tool: ToolId): String = stringResource(
    when (tool) {
        ToolId.GENERAL -> R.string.budget_tool_general
        ToolId.VERIFY -> R.string.budget_tool_verify
        ToolId.AI_DETECT -> R.string.budget_tool_ai_detect
        ToolId.ENTITY -> R.string.budget_tool_entity
    },
)

@Composable
private fun periodLabel(period: BudgetPeriod): String = stringResource(
    when (period) {
        BudgetPeriod.DAILY -> R.string.budget_period_daily
        BudgetPeriod.MONTHLY -> R.string.budget_period_monthly
    },
)
