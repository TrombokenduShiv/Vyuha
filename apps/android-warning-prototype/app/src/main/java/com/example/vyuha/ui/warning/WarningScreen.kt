package com.example.vyuha.ui.warning

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SafeGreen = Color(0xFF7FFFD4)
private val WatchYellow = Color(0xFFFFD166)
private val AlertOrange = Color(0xFFFF9F43)
private val CriticalRed = Color(0xFFFF6B6B)
private val EmergencyPurple = Color(0xFFC77DFF)

@Composable
fun WarningScreen() {
    var warningLevel by remember { mutableIntStateOf(0) }

    val warningState = when (warningLevel) {
        0 -> WarningState.PASS
        1 -> WarningState.MICRO_INQUIRY
        2 -> WarningState.REFLECTION
        3 -> WarningState.ISOLATION_BREAK
        4 -> WarningState.TRUSTED_VERIFY
        else -> WarningState.RESOLUTION
    }

    val warningManager = remember { WarningManager() }
    val warningSpec = warningManager.getWarningSpec(warningState)
    var cooldownRemaining by remember { mutableLongStateOf(warningSpec.cooldownMs) }

    LaunchedEffect(warningState) {
        cooldownRemaining = warningSpec.cooldownMs
        while (cooldownRemaining > 0) {
            delay(1000L)
            cooldownRemaining -= 1000L
        }
    }

    fun handleAction(actionId: String) {
        when (actionId) {
            "SOMEONE_DIRECTING" -> warningLevel = 2
            "CONFIRM_SAFE" -> warningLevel = 0
            "CONTINUE" -> warningLevel = 3
            "CANCEL" -> warningLevel = 0
            "CONTINUE_AFTER_COOLDOWN" -> warningLevel = 4
            "TRUSTED_VERIFIED" -> warningLevel = 5
            "RESOLUTION_CONTINUE" -> warningLevel = 0
        }
    }

    val stateColor = when (warningState) {
        WarningState.PASS -> SafeGreen
        WarningState.MICRO_INQUIRY -> WatchYellow
        WarningState.REFLECTION -> AlertOrange
        WarningState.ISOLATION_BREAK -> CriticalRed
        WarningState.TRUSTED_VERIFY -> EmergencyPurple
        WarningState.RESOLUTION -> SafeGreen
    }

    val backgroundColors = when (warningState) {
        WarningState.PASS -> listOf(Color(0xFF06352B), Color(0xFF0A211C), Color(0xFF03100D))
        WarningState.MICRO_INQUIRY -> listOf(Color(0xFF3A2A08), Color(0xFF211906), Color(0xFF100D03))
        WarningState.REFLECTION -> listOf(Color(0xFF3D1F08), Color(0xFF241208), Color(0xFF100704))
        WarningState.ISOLATION_BREAK -> listOf(Color(0xFF3D1015), Color(0xFF250A0E), Color(0xFF100406))
        WarningState.TRUSTED_VERIFY -> listOf(Color(0xFF28103D), Color(0xFF180A26), Color(0xFF09040F))
        WarningState.RESOLUTION -> listOf(Color(0xFF06352B), Color(0xFF0A211C), Color(0xFF03100D))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backgroundColors))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("VYUHA", color = stateColor, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("SAFETY INTELLIGENCE", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(32.dp))
            WarningLevelCard(warningState, warningSpec, stateColor, cooldownRemaining, ::handleAction)
            Spacer(modifier = Modifier.height(24.dp))
            ThreeDButton("NEXT WARNING STATE", stateColor) { warningLevel = (warningLevel + 1) % 6 }
        }
    }
}

@Composable
fun WarningLevelCard(
    state: WarningState,
    spec: WarningSpec,
    color: Color,
    cooldownRemaining: Long,
    onAction: (String) -> Unit
) {
    val levelText = when (state) {
        WarningState.PASS -> "A0 • NORMAL"
        WarningState.MICRO_INQUIRY -> "A1 • MICRO-INQUIRY"
        WarningState.REFLECTION -> "A2 • REFLECTION"
        WarningState.ISOLATION_BREAK -> "A3 • ISOLATION BREAK"
        WarningState.TRUSTED_VERIFY -> "A4 • TRUSTED VERIFY"
        WarningState.RESOLUTION -> "RESOLUTION"
    }
    val cardShape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, cardShape)
            .background(
                brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.28f))),
                shape = cardShape
            )
            .border(1.dp, color.copy(alpha = 0.45f), cardShape)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(levelText, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (spec.titleRes.isNotEmpty()) {
            Text(spec.titleRes, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (spec.bodyRes.isNotEmpty()) {
            Text(spec.bodyRes, color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (spec.cooldownMs > 0) {
            Text("COOLDOWN ACTIVE • ${cooldownRemaining / 1000} SECONDS", color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (spec.primaryAction.id != "NO_ACTION") {
            ThreeDButton(spec.primaryAction.labelRes, color) {
                if (cooldownRemaining <= 0L) onAction(spec.primaryAction.id)
            }
        }

        spec.secondaryAction?.let { action ->
            Spacer(modifier = Modifier.height(12.dp))
            OutlineButton(action.labelRes, color) { onAction(action.id) }
        }
    }
}

@Composable
fun ThreeDButton(text: String, color: Color, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val buttonOffset by animateDpAsState(targetValue = if (isPressed) 4.dp else 0.dp, label = "buttonPress")
    val buttonElevation by animateDpAsState(targetValue = if (isPressed) 4.dp else 10.dp, label = "buttonElevation")
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = buttonOffset)
            .shadow(buttonElevation, shape)
            .background(Color.Black.copy(alpha = 0.30f), shape)
            .border(1.5.dp, color.copy(alpha = 0.9f), shape)
            .clickable {
                isPressed = true
                onClick()
                isPressed = false
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color.Black.copy(alpha = 0.30f), shape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.18f))), shape)
                .border(1.dp, color.copy(alpha = 0.75f), shape)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
    }
}

@Composable
fun OutlineButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}
