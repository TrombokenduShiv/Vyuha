package com.example.vyuha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vyuha.ui.theme.VyuhaTheme
import com.example.vyuha.ui.warning.WarningScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VyuhaTheme {
                WarningScreen()
            }
        }
    }
}
