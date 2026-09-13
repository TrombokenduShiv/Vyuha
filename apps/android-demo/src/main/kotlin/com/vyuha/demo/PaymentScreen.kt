package com.vyuha.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PaymentScreen(viewModel: PaymentViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Vyuha Demo Bank") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is PaymentUiState.Idle -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Initiate Transfer",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Button(onClick = { viewModel.runGoldenPath() }) {
                            Text("Test Golden Path (Known Payee)")
                        }
                        Button(
                            onClick = { viewModel.runCoercionScenario() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Test Coercion Scenario (Unknown/Call)")
                        }
                    }
                }
                is PaymentUiState.Processing -> {
                    CircularProgressIndicator()
                }
                is PaymentUiState.Passed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Payment Successful!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Decision: ${state.decision.action}")
                        Button(onClick = { viewModel.reset() }) {
                            Text("Start Over")
                        }
                    }
                }
                is PaymentUiState.Intervention -> {
                    InterventionRenderer(
                        decision = state.decision,
                        onResponse = { response ->
                            viewModel.respondToIntervention(response)
                        }
                    )
                }
                is PaymentUiState.Completed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Flow Completed",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Final Status: ${state.status.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { viewModel.reset() }) {
                            Text("Start Over")
                        }
                    }
                }
            }
        }
    }
}
