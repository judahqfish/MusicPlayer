from pathlib import Path

# ---- Playback resume rule -------------------------------------------------
vm_path = Path('app/src/main/java/com/everywhen/offlinemusic/MainViewModel.kt')
vm = vm_path.read_text()
old = 'c.setMediaItems(items, start, available[start].lastPositionMs)'
new = '''val resumePosition = if (available[start].durationMs > 10L * 60L * 1000L) {
                    available[start].lastPositionMs.coerceAtLeast(0L)
                } else {
                    0L
                }
                c.setMediaItems(items, start, resumePosition)'''
if old not in vm:
    raise SystemExit('resume position line not found')
vm = vm.replace(old, new, 1)
vm_path.write_text(vm)

# ---- Smoother continuously moving visualizer -----------------------------
ui_path = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = ui_path.read_text()

if 'import androidx.compose.animation.core.*' not in text:
    text = text.replace(
        'import androidx.compose.foundation.background\n',
        'import androidx.compose.foundation.background\nimport androidx.compose.animation.core.*\n'
    )

start = text.index('@Composable\nfun AudioVisualizerCanvas(')
end = text.index('\n@Composable\nfun NowPlayingScreen', start)

new_canvas = r'''@Composable
fun AudioVisualizerCanvas(
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val frame by AudioVisualizationBus.frame.collectAsStateWithLifecycle()

    // Audio values change whenever a PCM buffer arrives. Interpolate between those
    // updates so the visualizer moves smoothly at display-frame speed.
    val energy by animateFloatAsState(
        targetValue = frame.energy.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "visualEnergy"
    )
    val beat by animateFloatAsState(
        targetValue = frame.beat.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 85, easing = FastOutSlowInEasing),
        label = "visualBeat"
    )

    // Continuous motion keeps the kaleidoscope alive between PCM callbacks. Music
    // controls how strongly that motion is expressed rather than causing hard jumps.
    val transition = rememberInfiniteTransition(label = "kaleidoscopeMotion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "kaleidoscopePhase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kaleidoscopeBreath"
    )

    Canvas(modifier = modifier) {
        if (showColorscape) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension * 0.47f
            val maxR = baseR * (0.90f + energy * 0.08f + beat * 0.10f + breathe * 0.025f)
            val radians = phase * (Math.PI / 180.0)
            val hueA = (phase * 0.82f + energy * 75f) % 360f
            val hueB = (phase * 1.21f + 112f + beat * 48f) % 360f
            val hueC = (phase * 0.57f + 228f + energy * 36f) % 360f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsv(hueA, 0.76f, 0.24f + energy * 0.44f),
                        Color.hsv(hueB, 0.84f, 0.18f + energy * 0.38f),
                        Color.hsv(hueC, 0.80f, 0.12f + beat * 0.46f)
                    ),
                    center = Offset(
                        cx + kotlin.math.sin(radians * 0.7).toFloat() * size.width * 0.045f * energy,
                        cy + kotlin.math.cos(radians * 0.55).toFloat() * size.height * 0.035f * energy
                    ),
                    radius = maxOf(size.width, size.height) * (0.73f + beat * 0.06f)
                )
            )

            val slices = 16
            val spokes = 6
            for (slice in 0 until slices) {
                val sliceAngle = slice * (360f / slices)
                val alternating = if (slice % 2 == 0) 1f else -1f
                val musicRotation = phase * (0.24f + energy * 0.12f) * alternating
                val base = (sliceAngle + musicRotation) * (Math.PI / 180.0)

                for (ring in 1..spokes) {
                    val waveIndex = (slice * 5 + ring * 9) % maxOf(1, frame.waveform.size)
                    val wave = frame.waveform.getOrNull(waveIndex) ?: 0f
                    val a = kotlin.math.abs(wave).coerceIn(0f, 1f)
                    val musicalPulse = 0.82f + a * 0.16f + energy * 0.08f + beat * (0.05f + ring * 0.008f)
                    val r = maxR * (ring / spokes.toFloat()) * musicalPulse

                    // Continuous orbit plus audio-driven warping makes every segment move,
                    // but transients still produce clearly synchronized expansion.
                    val orbit = kotlin.math.sin(radians * (0.75 + ring * 0.05) + slice * 0.45).toFloat()
                    val wobble = orbit * (0.10f + energy * 0.10f) + wave * 0.30f + beat * 0.12f
                    val angle = base + wobble
                    val mirrorAngle = base - wobble

                    val x = cx + kotlin.math.cos(angle).toFloat() * r
                    val y = cy + kotlin.math.sin(angle).toFloat() * r
                    val mx = cx + kotlin.math.cos(mirrorAngle).toFloat() * r
                    val my = cy + kotlin.math.sin(mirrorAngle).toFloat() * r

                    val c = Color.hsv(
                        (hueA + slice * 16f + ring * 31f + a * 38f) % 360f,
                        (0.62f + a * 0.28f).coerceAtMost(0.95f),
                        (0.52f + energy * 0.32f + beat * 0.12f).coerceAtMost(1f),
                        alpha = (0.26f + a * 0.38f + energy * 0.18f).coerceAtMost(0.82f)
                    )

                    val stroke = 1.4f + energy * 2.2f + beat * 5.8f
                    drawLine(c, Offset(cx, cy), Offset(x, y), strokeWidth = stroke)
                    drawLine(c, Offset(cx, cy), Offset(mx, my), strokeWidth = stroke)

                    val dotRadius = size.minDimension * (
                        0.009f + a * 0.024f + energy * 0.008f + beat * 0.018f
                    )
                    drawCircle(c.copy(alpha = 0.28f + beat * 0.34f), dotRadius, Offset(x, y))
                    drawCircle(
                        Color.hsv(
                            (hueB + slice * 21f + ring * 14f) % 360f,
                            0.72f,
                            0.92f,
                            0.20f + energy * 0.30f
                        ),
                        dotRadius * (0.72f + breathe * 0.20f),
                        Offset(mx, my)
                    )
                }
            }

            // Central pulse responds especially strongly to transient/beat energy.
            val inner = maxR * (0.10f + energy * 0.05f + beat * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f + beat * 0.42f),
                        Color.hsv(hueC, 0.74f, 0.92f, 0.26f + energy * 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = inner * 3f
                ),
                radius = inner * 3f,
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
                val amplitude = (
                    kotlin.math.abs(sample) * 0.72f + energy * 0.20f + beat * 0.08f
                ).coerceIn(0.025f, 1f)
                val half = amplitude * size.height * 0.43f
                drawLine(
                    color = Color.Black.copy(alpha = 0.92f),
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
ui_path.write_text(text)
