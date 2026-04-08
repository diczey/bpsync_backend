package com.example.finalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.SessionStore
import com.example.finalapp.data.repository.SettingsStore
import com.example.finalapp.ui.navigation.AppNavHost
import com.example.finalapp.ui.theme.FinalAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStore.initialize(applicationContext)
        SettingsStore.initialize(applicationContext)
        val bleRepository = BleRepository(applicationContext)
        if (savedInstanceState == null) {
            bleRepository.resetForColdStart()
        }
        setContent {
            FinalAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
