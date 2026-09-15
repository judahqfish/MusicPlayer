from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Drawing imports for the live visualizer.
if 'import androidx.compose.foundation.Canvas' not in text:
    text = text.replace('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.Canvas\n')
if 'import androidx.compose.ui.geometry.Offset' not in text:
    text = text.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\n')

# Mini-player visualizer toggles.
text = text.replace(
'''    var expanded by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }''',
'''    var expanded by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }''',
1
)

text = text.replace(
'''            if (expanded) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),''',
'''            if (expanded) {
                HorizontalDivider()
                AudioVisualizerControls(
                    showWaveform = showWaveform,
                    showColorscape = showColorscape,
                    onWaveform = { showWaveform = it },
                    onColorscape = { showColorscape = it }
                )
                if (showWaveform || showColorscape) {
                    AudioVisualizerCanvas(
                        showWaveform = showWaveform,
                        showColorscape = showColorscape,
                        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),''',
1
)

# Full Now Playing view also offers the same visualizer choices.
text = text.replace(
'''    var speedMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }''',
'''    var speedMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showWaveform by remember { mutableStateOf(false) }
    var showColorscape by remember { mutableStateOf(false) }''',
1
)

text = text.replace(
'''        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(pos)); Text(formatDuration(dur)) }
        Box(Modifier.align(Alignment.CenterHorizontally)) {''',
'''        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(pos)); Text(formatDuration(dur)) }
        AudioVisualizerControls(
            showWaveform = showWaveform,
            showColorscape = showColorscape,
            onWaveform = { showWaveform = it },
            onColorscape = { showColorscape = it }
        )
        if (showWaveform || showColorscape) {
            AudioVisualizerCanvas(
                showWaveform = showWaveform,
                showColorscape = showColorscape,
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) {''',
1
)

marker = '@Composable\nfun NowPlayingScreen'
helpers = '''@Composable
fun AudioVisualizerControls(
    showWaveform: Boolean,
    showColorscape: Boolean,
    onWaveform: (Boolean) -> Unit,
    onColorscape: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Visuals", style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = showWaveform,
            onClick = { onWaveform(!showWaveform) },
            label = { Text("Waveform") },
            leadingIcon = { Icon(Icons.Default.GraphicEq, null) }
        )
        FilterChip(
            selected = showColorscape,
            onClick = { onColorscape(!showColorscape) },
            label = { Text("Colorscape") },
            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
        )
    }
}

@Composable
fun AudioVisualizerCanvas(
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val frame by AudioVisualizationBus.frame.collectAsStateWithLifecycle()
    val energy = frame.energy.coerceIn(0f, 1f)
    val beat = frame.beat.coerceIn(0f, 1f)
    val phase = ((frame.sequence % 720L).toFloat() / 2f)

    Canvas(modifier = modifier) {
        if (showColorscape) {
            val hueA = phase % 360f
            val hueB = (phase + 95f + energy * 80f) % 360f
            val hueC = (phase + 205f + beat * 70f) % 360f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.hsv(hueA, 0.72f, 0.34f + energy * 0.42f),
                        Color.hsv(hueB, 0.80f, 0.28f + energy * 0.46f),
                        Color.hsv(hueC, 0.75f, 0.24f + beat * 0.58f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            val pulse = 0.16f + energy * 0.26f + beat * 0.18f
            drawCircle(
                color = Color.hsv((hueA + 45f) % 360f, 0.75f, 0.95f, alpha = 0.24f + beat * 0.32f),
                radius = size.minDimension * pulse,
                center = Offset(size.width * (0.25f + energy * 0.18f), size.height * 0.42f)
            )
            drawCircle(
                color = Color.hsv((hueB + 120f) % 360f, 0.70f, 0.92f, alpha = 0.18f + energy * 0.28f),
                radius = size.minDimension * (0.14f + energy * 0.24f),
                center = Offset(size.width * 0.73f, size.height * (0.62f - beat * 0.18f))
            )
        } else {
            drawRect(color = Color.Black.copy(alpha = 0.08f))
        }

        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY = size.height / 2f
            val count = frame.waveform.size
            val step = if (count > 1) size.width / (count - 1) else size.width
            val lineColor = if (showColorscape) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f)
            frame.waveform.forEachIndexed { index, sample ->
                val x = index * step
                val amplitude = (kotlin.math.abs(sample) * 0.78f + energy * 0.18f).coerceIn(0.03f, 1f)
                val half = amplitude * size.height * 0.43f
                drawLine(
                    color = lineColor,
                    start = Offset(x, centerY - half),
                    end = Offset(x, centerY + half),
                    strokeWidth = maxOf(2f, step * 0.32f)
                )
            }
        }
    }
}

'''
if marker not in text:
    raise SystemExit('NowPlaying marker not found')
text = text.replace(marker, helpers + marker, 1)

p.write_text(text)
