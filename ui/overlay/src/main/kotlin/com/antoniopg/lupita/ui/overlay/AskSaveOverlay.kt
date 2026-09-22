package com.antoniopg.lupita.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts
import com.antoniopg.lupita.ui.theme.LupitaTheme

/**
 * Dialogo de superposicion para la politica de guardado «preguntar antes» (`ImageSavePolicy.ASK`): una
 * ventana propia, independiente de [BubbleOverlay] (que ya se ha retirado cuando esto se muestra). Se
 * destruye a si misma tras la decision del usuario.
 */
class AskSaveOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()
    private var window: ComposeOverlayWindow? = null

    fun show(summary: String, onDecision: (save: Boolean) -> Unit) {
        owner.start()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = params,
            content = {
                AskSaveContent(
                    summary = summary,
                    onSave = { decide(onDecision, true) },
                    onDismiss = { decide(onDecision, false) },
                )
            },
        ).also { it.add() }
    }

    private fun decide(onDecision: (Boolean) -> Unit, save: Boolean) {
        onDecision(save)
        remove()
    }

    fun remove() {
        window?.remove()
        window = null
        owner.destroy()
    }
}

private val Scrim = Color(0xAD0A1416)

@Composable
private fun AskSaveContent(summary: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    LupitaTheme {
        val c = Lupita.colors
        Box(
            modifier = Modifier.fillMaxSize().background(Scrim).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.background)
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.ask_save_title),
                    color = c.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LupitaFonts.heading,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ask_save_body, summary), color = c.subtle, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.card)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.ask_save_dismiss), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accent)
                            .clickable(role = Role.Button, onClick = onSave)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.ask_save_confirm), color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
