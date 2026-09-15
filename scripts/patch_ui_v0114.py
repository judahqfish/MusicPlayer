from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

if 'import androidx.compose.foundation.rememberScrollState' not in text:
    text = text.replace('import androidx.compose.foundation.gestures.detectVerticalDragGestures\n', 'import androidx.compose.foundation.gestures.detectVerticalDragGestures\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n')
if 'import androidx.compose.ui.window.Dialog' not in text:
    text = text.replace('import androidx.compose.ui.unit.dp\n', 'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n')

# Make the waveform dark/black for visibility.
text = text.replace(
'''            val lineColor = if (showColorscape) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f)''',
'''            val lineColor = Color.Black.copy(alpha = 0.90f)'''
)

# Replace the visualizer canvas with a radial, mirrored kaleidoscope-like visualization.
start = text.index('@Composable\nfun AudioVisualizerCanvas(')
end = text.index('\n@Composable\nfun NowPlayingScreen', start)
new_canvas = r'''@Composable
fun AudioVisualizerCanvas(
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val frame by AudioVisualizationBus.frame.collectAsStateWithLifecycle()
    val energy = frame.energy.coerceIn(0f, 1f)
    val beat = frame.beat.coerceIn(0f, 1f)
    val phase = ((frame.sequence % 1440L).toFloat() / 4f)

    Canvas(modifier = modifier) {
        if (showColorscape) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension * (0.47f + beat * 0.03f)
            val hueA = phase % 360f
            val hueB = (phase * 1.7f + 110f) % 360f
            val hueC = (phase * 0.7f + 220f) % 360f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsv(hueA, 0.72f, 0.28f + energy * 0.40f),
                        Color.hsv(hueB, 0.82f, 0.20f + energy * 0.38f),
                        Color.hsv(hueC, 0.78f, 0.12f + beat * 0.42f)
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(size.width, size.height) * 0.75f
                )
            )

            val slices = 12
            val spokes = 5
            for (slice in 0 until slices) {
                val base = (slice * (360f / slices) + phase * (0.18f + beat * 0.08f)) * (Math.PI / 180.0)
                for (ring in 1..spokes) {
                    val wave = frame.waveform.getOrNull((slice * 7 + ring * 11) % maxOf(1, frame.waveform.size)) ?: 0f
                    val a = kotlin.math.abs(wave).coerceIn(0f, 1f)
                    val r = maxR * (ring / spokes.toFloat()) * (0.72f + a * 0.28f)
                    val wobble = (a * 0.38f + energy * 0.16f) * if (ring % 2 == 0) 1f else -1f
                    val angle = base + wobble
                    val x = cx + kotlin.math.cos(angle).toFloat() * r
                    val y = cy + kotlin.math.sin(angle).toFloat() * r
                    val mirrorAngle = base - wobble
                    val mx = cx + kotlin.math.cos(mirrorAngle).toFloat() * r
                    val my = cy + kotlin.math.sin(mirrorAngle).toFloat() * r
                    val c = Color.hsv(
                        (hueA + slice * 19f + ring * 27f) % 360f,
                        0.68f + a * 0.22f,
                        0.58f + energy * 0.32f,
                        alpha = 0.34f + a * 0.42f
                    )
                    drawLine(c, Offset(cx, cy), Offset(x, y), strokeWidth = 1.5f + beat * 5f)
                    drawLine(c, Offset(cx, cy), Offset(mx, my), strokeWidth = 1.5f + beat * 5f)
                    drawCircle(
                        color = c.copy(alpha = 0.24f + beat * 0.36f),
                        radius = size.minDimension * (0.012f + a * 0.035f + beat * 0.018f),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.hsv((hueB + slice * 23f) % 360f, 0.72f, 0.90f, 0.18f + energy * 0.30f),
                        radius = size.minDimension * (0.009f + a * 0.024f),
                        center = Offset(mx, my)
                    )
                }
            }

            val inner = maxR * (0.15f + beat * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.42f + beat * 0.28f),
                        Color.hsv(hueC, 0.72f, 0.86f, 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = inner * 2.3f
                ),
                radius = inner * 2.3f,
                center = Offset(cx, cy)
            )
        } else {
            drawRect(color = Color.White)
        }

        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY = size.height / 2f
            val count = frame.waveform.size
            val step = if (count > 1) size.width / (count - 1) else size.width
            frame.waveform.forEachIndexed { index, sample ->
                val x = index * step
                val amplitude = (kotlin.math.abs(sample) * 0.78f + energy * 0.18f).coerceIn(0.03f, 1f)
                val half = amplitude * size.height * 0.43f
                drawLine(
                    color = Color.Black.copy(alpha = 0.90f),
                    start = Offset(x, centerY - half),
                    end = Offset(x, centerY + half),
                    strokeWidth = maxOf(2f, step * 0.32f)
                )
            }
        }
    }
}
'''
text = text[:start] + new_canvas + text[end:]

# Make Now Playing scroll so amplifier/controls cannot be hidden under the persistent player.
np_start = text.index('@Composable\nfun NowPlayingScreen')
np_end = text.index('\n@Composable\nfun QueueDialog', np_start)
block = text[np_start:np_end]
block = block.replace(
'''    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }''',
'''    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }
    var showFullScreenVisual by remember { mutableStateOf(false) }''',
1
)
block = block.replace(
'''    Column(Modifier.fillMaxSize()) {''',
'''    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {''',
1
)
block = block.replace(
'''        if (showWaveform || showColorscape) {
            AudioVisualizerCanvas(
                showWaveform = showWaveform,
                showColorscape = showColorscape,
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) {''',
'''        if (showWaveform || showColorscape) {
            AudioVisualizerCanvas(
                showWaveform = showWaveform,
                showColorscape = showColorscape,
                modifier = Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (showColorscape) {
                TextButton(
                    onClick = { showFullScreenVisual = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Fullscreen, null)
                    Text(" Full screen kaleidoscope")
                }
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) {''',
1
)
block = block.replace(
'''    if (showQueue) QueueDialog(vm, onDismiss = { showQueue = false })
}''',
'''    if (showQueue) QueueDialog(vm, onDismiss = { showQueue = false })
    if (showFullScreenVisual) {
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
    }
}''',
1
)
text = text[:np_start] + block + text[np_end:]

p.write_text(text)
