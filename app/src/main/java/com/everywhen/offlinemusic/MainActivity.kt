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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crash = (application as MusicApplication).takeLastCrash()
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                if (crash != null) {
                    CrashReportScreen(versionName, crash) { recreate() }
                } else {
                    val vm: MainViewModel = viewModel()
                    Column(Modifier.fillMaxSize().systemBarsPadding()) {
                        Surface(tonalElevation = 1.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "MusicPlayer v$versionName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            MusicApp(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportScreen(versionName: String, crash: String, onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("MusicPlayer v$versionName", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
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
