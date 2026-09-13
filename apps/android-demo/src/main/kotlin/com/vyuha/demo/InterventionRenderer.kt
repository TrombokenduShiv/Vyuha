package com.vyuha.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vyuha.sdk.contracts.ActionId
import com.vyuha.sdk.contracts.InterventionDecision
import com.vyuha.sdk.contracts.UserResponse

/**
 * Renders the appropriate UI block based on the Vyuha InterventionDecision.
 */
@Composable
fun InterventionRenderer(
    decision: InterventionDecision,
    onResponse: (UserResponse) -> Unit
) {
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
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))

            when (decision.action) {
                "A4_ISOLATION_BREAK" -> {
                    Text(
                        text = "Active Call Detected",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You are attempting a high-risk transfer while on an active call or sharing your screen.\n\nTo proceed, you must disconnect the call.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onResponse(UserResponse.CONTINUED) }, // Simulates ending the call
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("I have ended the call")
                    }
                }
                "A5_TRUSTED_VERIFY" -> {
                    Text(
                        text = "Verify with Trusted Contact",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This transaction is highly unusual. We require verification from your Safety Circle before proceeding.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onResponse(UserResponse.VERIFIED) }, // Simulates contact approving
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Send Verification Request")
                    }
                }
                else -> {
                    Text(
                        text = "Risk Detected",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Action required: ${decision.action}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onResponse(UserResponse.CONTINUED) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Acknowledge & Continue")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onResponse(UserResponse.CANCELLED) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Transaction")
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Debug info for the hackathon slice
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Debug Info (Slice 0/1)", fontWeight = FontWeight.Bold)
                    Text("Action ID: ${decision.action}")
                    Text("Severity: ${decision.severity}")
                    Text("Reason Codes: ${decision.reasonCodes.joinToString()}")
                }
            }
        }
    }
}
