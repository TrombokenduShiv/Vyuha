package com.vyuha.demo.warning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SafeGreen = Color(0xFF7FFFD4)
private val WatchYellow = Color(0xFFFFD166)
private val AlertOrange = Color(0xFFFF9F43)
private val CriticalRed = Color(0xFFFF6B6B)
private val EmergencyPurple = Color(0xFFC77DFF)

@Composable
fun VyuhaWarningScreen(
    state: WarningState,
    beliefScore: Double,
    uncertainty: Double,
    reasonCodes: List<String>,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onVerified: () -> Unit
) {
    val spec = WarningManager.spec(state)
    var cooldownRemaining by remember(state) { mutableLongStateOf(spec.cooldownMs) }

    LaunchedEffect(state) {
        cooldownRemaining = spec.cooldownMs
        while (cooldownRemaining > 0) {
            delay(1_000L)
            cooldownRemaining = (cooldownRemaining - 1_000L).coerceAtLeast(0L)
        }
    }

    val accent = when (state) {
        WarningState.PASS -> SafeGreen
        WarningState.MICRO_INQUIRY -> WatchYellow
        WarningState.REFLECTION, WarningState.COOLING_DELAY -> AlertOrange
        WarningState.ISOLATION_BREAK, WarningState.STEP_UP_REQUIRED -> CriticalRed
        WarningState.TRUSTED_VERIFY -> EmergencyPurple
    }
    val background = when (state) {
        WarningState.PASS -> listOf(Color(0xFF06352B), Color(0xFF03100D))
        WarningState.MICRO_INQUIRY -> listOf(Color(0xFF3A2A08), Color(0xFF100D03))
        WarningState.REFLECTION, WarningState.COOLING_DELAY -> listOf(Color(0xFF3D1F08), Color(0xFF100704))
        WarningState.ISOLATION_BREAK, WarningState.STEP_UP_REQUIRED -> listOf(Color(0xFF3D1015), Color(0xFF100406))
        WarningState.TRUSTED_VERIFY -> listOf(Color(0xFF28103D), Color(0xFF09040F))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(background))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("VYUHA", color = accent, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(6.dp))
            Text("AGENCY INTEGRITY", color = Color.White.copy(alpha = .55f), fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(28.dp))

            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, shape)
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = .18f), Color.Black.copy(alpha = .28f))), shape)
                    .border(1.dp, accent.copy(alpha = .45f), shape)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(spec.uiSpecId, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(16.dp))
                Text(spec.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(spec.body, color = Color.White.copy(alpha = .74f), fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)

                if (spec.cooldownMs > 0) {
                    Spacer(Modifier.height(18.dp))
                    Text("SAFETY PAUSE • ${cooldownRemaining / 1_000}s", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                spec.primaryAction?.let { action ->
                    Spacer(Modifier.height(24.dp))
                    WarningButton(action.label, accent, enabled = cooldownRemaining <= 0L) {
                        if (action.id == "VERIFIED") onVerified() else onContinue()
                    }
                }
                spec.secondaryAction?.let { action ->
                    Spacer(Modifier.height(12.dp))
                    WarningOutlineButton(action.label, accent, onCancel)
                }

                Spacer(Modifier.height(24.dp))
                Text("Belief ${"%.3f".format(beliefScore)} • uncertainty ${"%.3f".format(uncertainty)}", color = Color.White.copy(alpha = .38f), fontSize = 11.sp)
                if (reasonCodes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(reasonCodes.joinToString(" • "), color = Color.White.copy(alpha = .30f), fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun WarningButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = if (enabled) .34f else .12f), shape)
            .border(1.5.dp, color.copy(alpha = if (enabled) .9f else .3f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 1f else .45f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun WarningOutlineButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = .45f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = .76f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
