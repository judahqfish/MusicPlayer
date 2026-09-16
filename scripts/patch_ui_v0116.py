from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

if 'import androidx.compose.ui.platform.LocalView' not in text:
    text = text.replace('import androidx.compose.ui.platform.LocalDensity\n', 'import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalView\n')

# Mini-player: expose full-screen kaleidoscope from the controls the user actually sees.
text = text.replace(
'''    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableFloatStateOf(0f) }''',
'''    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }
    var showFullScreenVisual by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableFloatStateOf(0f) }''',
1
)

text = text.replace(
'''                if (showWaveform || showColorscape) {
                    AudioVisualizerCanvas(
                        showWaveform = showWaveform,
                        showColorscape = showColorscape,
                        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(''',
'''                if (showWaveform || showColorscape) {
                    AudioVisualizerCanvas(
                        showWaveform = showWaveform,
                        showColorscape = showColorscape,
                        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (showColorscape) {
                        TextButton(
                            onClick = { showFullScreenVisual = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Fullscreen, null)
                            Text(" Full screen")
                        }
                    }
                }
                Row(''',
1
)

# Use stored metadata duration until ExoPlayer has finished probing the local file.
text = text.replace(
'''            duration = controller?.duration?.takeIf { it > 0L } ?: 0L''',
'''            val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
            val storedDuration = vm.tracks.value.firstOrNull { it.id == currentId }?.durationMs ?: 0L
            duration = controller?.duration?.takeIf { it > 0L } ?: storedDuration''',
1
)
text = text.replace(
'''            dur = controller?.duration?.takeIf { it > 0 } ?: 0''',
'''            val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
            val storedDuration = vm.tracks.value.firstOrNull { it.id == currentId }?.durationMs ?: 0L
            dur = controller?.duration?.takeIf { it > 0 } ?: storedDuration''',
1
)

# Open full-screen visualizer from MiniPlayer.
needle = '''    }
}

@Composable
fun AudioVisualizerControls'''
replacement = '''    }
    if (showFullScreenVisual) {
        FullScreenVisualizerDialog(
            vm = vm,
            showWaveform = showWaveform,
            playing = playing,
            onDismiss = { showFullScreenVisual = false }
        )
    }
}

@Composable
fun AudioVisualizerControls'''
if needle not in text:
    raise SystemExit('MiniPlayer ending not found')
text = text.replace(needle, replacement, 1)

# Replace the older Now Playing full-screen dialog with the shared one that supports screensaver mode.
old_dialog = '''    if (showFullScreenVisual) {
        Dialog(
            onDismissRequest = { showFullScreenVisual = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    AudioVisualizerCanvas(
                        showWaveform = showWaveform,
                        showColorscape = true,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { showFullScreenVisual = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, "Exit full screen", tint = Color.White)
                    }
                }
            }
        }
    }'''
new_dialog = '''    if (showFullScreenVisual) {
        FullScreenVisualizerDialog(
            vm = vm,
            showWaveform = showWaveform,
            playing = playing,
            onDismiss = { showFullScreenVisual = false }
        )
    }'''
if old_dialog not in text:
    raise SystemExit('Now Playing full-screen dialog not found')
text = text.replace(old_dialog, new_dialog, 1)

# Shared full-screen kaleidoscope. "Screensaver" means keep the display awake while playback continues;
# Android still honors a deliberate press of the power button.
marker = '@Composable\nfun AudioVisualizerControls'
helper = '''@Composable
fun FullScreenVisualizerDialog(
    vm: MainViewModel,
    showWaveform: Boolean,
    playing: Boolean,
    onDismiss: () -> Unit
) {
    val screensaver by vm.visualizerScreensaver.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view, screensaver, playing) {
            val previous = view.keepScreenOn
            view.keepScreenOn = screensaver && playing
            onDispose { view.keepScreenOn = previous }
        }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AudioVisualizerCanvas(
                    showWaveform = showWaveform,
                    showColorscape = true,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.48f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Screensaver", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Switch(
                            checked = screensaver,
                            onCheckedChange = vm::setVisualizerScreensaver
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Exit full screen", tint = Color.White)
                        }
                    }
                }
                if (screensaver) {
                    Text(
                        if (playing) "Screen stays on while music plays" else "Starts keeping screen on when playback resumes",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                    )
                }
            }
        }
    }
}

'''
if marker not in text:
    raise SystemExit('Visualizer controls marker not found')
text = text.replace(marker, helper + marker, 1)

p.write_text(text)
