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
import com.vyuha.demo.viewmodel.PaymentResult
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ResultScreen(
    viewModel: PaymentFlowViewModel
) {
    val result by viewModel.paymentResult.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val selectedContact by viewModel.selectedContact.collectAsState()
    val customVpa by viewModel.customVpa.collectAsState()
    val currentResult = result ?: return

    val payeeName = selectedContact?.name ?: customVpa
    val amountFormatted = try {
        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        nf.format(amount.toDouble())
    } catch (e: Exception) {
        "₹$amount"
    }

    val (icon, title, subtitle, containerColor, contentColor, iconColor) = when (currentResult) {
        PaymentResult.SUCCESS -> ResultStyle(
            icon = Icons.Default.CheckCircle,
            title = "Payment Successful!",
            subtitle = "$amountFormatted sent to $payeeName",
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            iconColor = MaterialTheme.colorScheme.tertiary
        )
        PaymentResult.CANCELLED -> ResultStyle(
            icon = Icons.Default.Cancel,
            title = "Payment Cancelled",
            subtitle = "Transaction was cancelled. No money was deducted.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.outline
        )
        PaymentResult.BLOCKED -> ResultStyle(
            icon = Icons.Default.Shield,
            title = "Transaction Blocked",
            subtitle = "This transaction was blocked for your safety. Contact your bank if needed.",
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            iconColor = MaterialTheme.colorScheme.error
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Icon ────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(28.dp),
            color = iconColor.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = iconColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Title ───────────────────────────────────────────────────
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Subtitle ────────────────────────────────────────────────
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (currentResult == PaymentResult.SUCCESS) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(amountFormatted, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("To", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(payeeName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Completed ✓", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // ── Back to Home ────────────────────────────────────────────
        Button(
            onClick = { viewModel.resetFlow() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to Home", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Helper data class for result screen styling.
 */
private data class ResultStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val iconColor: androidx.compose.ui.graphics.Color
)
