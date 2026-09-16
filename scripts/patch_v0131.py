from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

start = text.index('@Composable\nfun AudioVisualizerCanvas(')
end = text.index('\n@Composable\nfun NowPlayingScreen', start)

new_block = r'''@Composable
fun AudioVisualizerCanvas(
    vm: MainViewModel,
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val frame by AudioVisualizationBus.frame.collectAsStateWithLifecycle()
    val style by vm.visualizerStyle.collectAsStateWithLifecycle()
    VisualizerScene(
        style = style,
        frame = frame,
        showWaveform = showWaveform,
        showColorscape = showColorscape,
        modifier = modifier
    )
}

@Composable
internal fun VisualizerScene(
    style: String,
    frame: VisualizationFrame,
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "visualizerMotion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizerPhase"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension.coerceAtLeast(1f)
        val energy = frame.energy.coerceIn(0f, 1f)
        val bass = frame.bass.coerceIn(0f, 1f)
        val mids = frame.mids.coerceIn(0f, 1f)
        val treble = frame.treble.coerceIn(0f, 1f)
        val t = phase * (Math.PI / 180.0)

        if (showColorscape) {
            drawRect(Color.Black)

            val warm = Color.hsv((18f + phase * .10f + bass * 25f) % 360f, .82f, (.62f + energy * .22f).coerceAtMost(1f))
            val middle = Color.hsv((300f + phase * .07f + mids * 35f) % 360f, .76f, (.60f + energy * .20f).coerceAtMost(1f))
            val cool = Color.hsv((188f + phase * .09f + treble * 35f) % 360f, .74f, (.66f + energy * .18f).coerceAtMost(1f))
            val palette = listOf(warm, middle, cool)

            // Always render a visible source field even with silent audio. This is
            // deliberately pure Compose Canvas: no RuntimeShader / RenderEffect path.
            repeat(12) { i ->
                val a = i * .72 + t * (.18 + (i % 3) * .025)
                val r = minD * (.10f + (i % 5) * .07f)
                val p = Offset(
                    cx + kotlin.math.cos(a).toFloat() * r,
                    cy + kotlin.math.sin(a * .91).toFloat() * r
                )
                val c = palette[i % 3]
                val rr = minD * (.035f + (i % 4) * .012f + when (i % 3) { 0 -> bass; 1 -> mids; else -> treble } * .012f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .20f), c.copy(alpha = .82f), c.copy(alpha = .16f), Color.Transparent),
                        center = p,
                        radius = rr * 2.8f
                    ),
                    radius = rr * 2.8f,
                    center = p
                )
            }

            when (style) {
                "Blob" -> {
                    repeat(4) { layer ->
                        val path = Path()
                        val points = 96
                        val baseR = minD * (.14f + layer * .065f)
                        for (i in 0..points) {
                            val a = i.toFloat() / points * 6.2831853f
                            val wobble = kotlin.math.sin(a * (2.2f + layer * .55f) + phase * .012f + layer).toFloat() * (.06f + mids * .025f)
                            val wobble2 = kotlin.math.cos(a * (4.1f + layer * .25f) - phase * .008f).toFloat() * (.028f + treble * .014f)
                            val rr = baseR * (1f + wobble + wobble2)
                            val x = cx + kotlin.math.cos(a) * rr
                            val y = cy + kotlin.math.sin(a) * rr
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, palette[layer % 3].copy(alpha = .10f + layer * .055f + energy * .06f))
                    }
                }
                "Drops" -> {
                    repeat(18) { i ->
                        val a = i * .91f + phase * .006f
                        val r = minD * (.06f + (i % 7) * .055f)
                        val p = Offset(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r)
                        val c = palette[i % 3]
                        val core = minD * (.008f + (i % 3) * .003f + bass * .003f)
                        drawCircle(c.copy(alpha = .55f), core, p)
                        drawCircle(c.copy(alpha = .13f), core * 3.4f, p)
                        drawCircle(c.copy(alpha = .12f), core * (5f + (i % 4)), p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                    }
                }
                "Nebula" -> {
                    repeat(5) { i ->
                        val a = phase * (.0025f + i * .0004f) + i * 1.3f
                        val p = Offset(cx + kotlin.math.sin(a) * size.width * .18f, cy + kotlin.math.cos(a * .8f) * size.height * .16f)
                        val rr = minD * (.20f + i * .018f)
                        val c = palette[i % 3]
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha=.24f), c.copy(alpha=.07f), Color.Transparent), p, rr), rr, p)
                    }
                }
                "Radial" -> {
                    val spokes = 28
                    repeat(spokes) { i ->
                        val a = i.toFloat() / spokes * 6.2831853f + phase * .0025f
                        val c = palette[i % 3]
                        val inner = minD * .06f
                        val outer = minD * (.36f + (i % 4) * .015f + energy * .02f)
                        drawLine(c.copy(alpha=.45f), Offset(cx + kotlin.math.cos(a)*inner, cy + kotlin.math.sin(a)*inner), Offset(cx + kotlin.math.cos(a)*outer, cy + kotlin.math.sin(a)*outer), 1.5f + (i % 3))
                    }
                }
                else -> { // Kaleidoscope: true mirrored-looking repeated facets, Canvas only.
                    val sectors = 12
                    val rings = 5
                    repeat(sectors) { sector ->
                        val base = sector.toFloat() / sectors * 6.2831853f + phase * .0018f * (if (sector % 2 == 0) 1f else -1f)
                        repeat(rings) { ring ->
                            val inner = minD * (.035f + ring * .070f)
                            val outer = minD * (.10f + ring * .075f)
                            val width = .12f + ring * .012f
                            val mirror = if (sector % 2 == 0) 1f else -1f
                            val bend = kotlin.math.sin(phase * .006f + ring * .8f).toFloat() * .08f * mirror
                            val a0 = base - width + bend
                            val a1 = base + width + bend
                            val a2 = base + width * .38f - bend * .5f
                            val a3 = base - width * .38f - bend * .5f
                            val p0 = Offset(cx + kotlin.math.cos(a0)*inner, cy + kotlin.math.sin(a0)*inner)
                            val p1 = Offset(cx + kotlin.math.cos(a1)*inner, cy + kotlin.math.sin(a1)*inner)
                            val p2 = Offset(cx + kotlin.math.cos(a2)*outer, cy + kotlin.math.sin(a2)*outer)
                            val p3 = Offset(cx + kotlin.math.cos(a3)*outer, cy + kotlin.math.sin(a3)*outer)
                            val facet = Path().apply {
                                moveTo(p0.x,p0.y); lineTo(p1.x,p1.y); lineTo(p2.x,p2.y); lineTo(p3.x,p3.y); close()
                            }
                            val c = palette[(sector + ring) % 3]
                            drawPath(facet, c.copy(alpha = .24f + energy * .10f))
                            drawPath(facet, Color.White.copy(alpha=.07f + treble*.06f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=1f))
                        }
                    }
                }
            }
        } else {
            drawRect(Color.White)
        }

        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY = size.height / 2f
            val step = if (frame.waveform.size > 1) size.width / (frame.waveform.size - 1) else size.width
            frame.waveform.forEachIndexed { index, sample ->
                val x = index * step
                val amplitude = (kotlin.math.abs(sample) * .55f + energy * .08f).coerceIn(.02f, .72f)
                val half = amplitude * size.height * .36f
                val under = if (showColorscape) Color.Black.copy(alpha=.55f) else Color.White
                val top = if (showColorscape) Color.White.copy(alpha=.92f) else Color.Black.copy(alpha=.90f)
                drawLine(under, Offset(x,centerY-half), Offset(x,centerY+half), maxOf(3f,step*.36f))
                drawLine(top, Offset(x,centerY-half), Offset(x,centerY+half), maxOf(1.5f,step*.20f))
            }
        }
    }
}
'''

text = text[:start] + new_block + text[end:]
p.write_text(text)
