package com.vyuha.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vyuha.demo.screens.*
import com.vyuha.demo.theme.DemoBankTheme
import com.vyuha.demo.viewmodel.FlowStep
import com.vyuha.demo.viewmodel.PaymentFlowViewModel

/**
 * DemoBank — MainActivity
 * ========================
 * Single Activity that hosts all screens via state-based navigation.
 *
 * The demo shows a realistic bank payment flow:
 *   Home → EnterAmount → BeneficiarySelection → ConfirmPayment
 *     → [Vyuha SDK Evaluation]
 *       → Success (A0_PASS)
 *       → InterventionScreen (A1-A6)
 *         → ResultScreen
 *
 * The Vyuha SDK is initialized in DemoBankApplication.onCreate(),
 * NOT here — proving it's a library that lives in the Application lifecycle.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DemoBankTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoBankApp()
                }
            }
        }
    }
}

@Composable
fun DemoBankApp(viewModel: PaymentFlowViewModel = viewModel()) {
    val flowStep by viewModel.flowStep.collectAsState()

    when (flowStep) {
        FlowStep.HOME -> {
            HomeScreen(onSendMoney = { viewModel.goToEnterAmount() })
        }
        FlowStep.ENTER_AMOUNT -> {
            EnterAmountScreen(
                viewModel = viewModel,
                onBack = { viewModel.resetFlow() },
                onNext = { /* navigation handled by ViewModel */ }
            )
        }
        FlowStep.BENEFICIARY -> {
            BeneficiaryScreen(
                viewModel = viewModel,
                onBack = { viewModel.resetFlow() },
                onNext = { /* navigation handled by ViewModel */ }
            )
        }
        FlowStep.CONFIRM -> {
            ConfirmPaymentScreen(
                viewModel = viewModel,
                onBack = { viewModel.resetFlow() }
            )
        }
        FlowStep.INTERVENTION -> {
            InterventionScreen(viewModel = viewModel)
        }
        FlowStep.RESULT -> {
            ResultScreen(viewModel = viewModel)
        }
    }
}
