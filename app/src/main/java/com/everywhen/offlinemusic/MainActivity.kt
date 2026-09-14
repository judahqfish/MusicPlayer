package com.everywhen.offlinemusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crash = (application as MusicApplication).takeLastCrash()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                if (crash != null) {
                    CrashReportScreen(crash) { recreate() }
                } else {
                    val vm: MainViewModel = viewModel()
                    MusicApp(vm)
                }
            }
        }
    }
}

@Composable
private fun CrashReportScreen(crash: String, onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("MusicPlayer crashed", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("Please screenshot this screen and send it to me. The crash report stays only on this device.")
            Spacer(Modifier.height(16.dp))
            Text(crash, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue) { Text("Try app again") }
        }
    }
}
