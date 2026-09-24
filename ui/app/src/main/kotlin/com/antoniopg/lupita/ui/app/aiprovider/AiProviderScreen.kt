package com.antoniopg.lupita.ui.app.aiprovider

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.app.SectionLabel
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/**
 * Todo lo que la ventana de Proveedor de IA puede cambiar (F5 paso 1). [keyConfigured] es lo unico
 * que se sabe sobre la clave guardada — nunca se vuelve a leer en claro hacia la UI, ni aqui ni en
 * ningun sitio (escritura, no lectura, igual que cualquier campo de credencial de este proyecto).
 */
class AiProviderUi(val keyConfigured: Boolean, val onSave: (String) -> Unit, val onClear: () -> Unit)

/** Ajustes -> Proveedor de IA: la clave de API de DeepSeek (decision 2026-09-22, "por defecto: DeepSeek"). */
@Composable
fun AiProviderScreen(ui: AiProviderUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val c = Lupita.colors
    var apiKey by rememberSaveable { mutableStateOf("") }

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
                stringResource(R.string.ai_provider_title),
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.ai_provider_intro), color = c.subtle, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.ai_provider_deepseek))
        Text(
            stringResource(if (ui.keyConfigured) R.string.ai_provider_configured else R.string.ai_provider_not_configured),
            color = if (ui.keyConfigured) c.ink else c.subtle,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.ai_provider_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = apiKey.isNotBlank(),
                onClick = {
                    ui.onSave(apiKey)
                    apiKey = ""
                },
            ) {
                Text(stringResource(R.string.ai_provider_save))
            }
            if (ui.keyConfigured) {
                TextButton(onClick = ui.onClear) {
                    Text(stringResource(R.string.ai_provider_clear), color = c.danger)
                }
            }
        }
    }
}
