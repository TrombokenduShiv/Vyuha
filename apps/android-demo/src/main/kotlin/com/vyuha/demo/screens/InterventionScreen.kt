package com.vyuha.demo.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vyuha.demo.viewmodel.PaymentFlowViewModel
import com.vyuha.sdk.InterventionType

/**
 * VyuhaInterceptor — The Intervention Screen
 * =============================================
 * This screen is rendered ONLY when the Vyuha SDK determines
 * that the transaction is risky.
 *
 * The host bank app doesn't decide WHAT to show — the SDK's RiskVerdict
 * tells it what type of intervention to render. The bank just renders it.
 */
@Composable
fun InterventionScreen(
    viewModel: PaymentFlowViewModel
) {
    val verdict by viewModel.verdict.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentVerdict = verdict ?: return

    val icon = when (currentVerdict.interventionType) {
        InterventionType.PROMPT -> Icons.Default.HelpOutline
        InterventionType.CHALLENGE -> Icons.Default.Psychology
        InterventionType.DELAY -> Icons.Default.Timer
        InterventionType.CALL_BLOCK -> Icons.Default.PhoneDisabled
        InterventionType.VERIFY_CONTACT -> Icons.Default.VerifiedUser
        InterventionType.HARD_BLOCK -> Icons.Default.Block
        null -> Icons.Default.Warning
    }

    val actionButtonText = when (currentVerdict.interventionType) {
        InterventionType.PROMPT -> "Yes, I'm sure"
        InterventionType.CHALLENGE -> "I understand, proceed"
        InterventionType.DELAY -> "Wait and proceed"
        InterventionType.CALL_BLOCK -> "I have ended the call"
        InterventionType.VERIFY_CONTACT -> "Send verification"
        InterventionType.HARD_BLOCK -> "Contact Bank"
        null -> "Continue"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Icon ────────────────────────────────────────────────
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Title ───────────────────────────────────────────────
            Text(
                text = currentVerdict.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Message ─────────────────────────────────────────────
            Text(
                text = currentVerdict.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isProcessing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.error)
            } else {
                // ── Action Button ───────────────────────────────────
                if (currentVerdict.interventionType != InterventionType.HARD_BLOCK) {
                    Button(
                        onClick = { viewModel.respondToIntervention() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            actionButtonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Cancel Button ───────────────────────────────────
                OutlinedButton(
                    onClick = { viewModel.cancelPayment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Cancel Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Debug Card ──────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Debug: SDK Risk Verdict",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Intervention: ${currentVerdict.interventionType?.name ?: "NONE"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Belief Score: ${"%.3f".format(currentVerdict.debugBeliefScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Reason Codes: ${currentVerdict.reasonCodes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
