from pathlib import Path
import re

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

if 'import androidx.compose.ui.graphics.Path' not in text:
    text = text.replace('import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Path\n')

# Pass the VM into visualizer controls/canvas so the selected scene can be persisted.
text = re.sub(
    r'AudioVisualizerControls\(\n(\s*)showWaveform',
    r'AudioVisualizerControls(\n\1vm = vm,\n\1showWaveform',
    text
)
text = re.sub(
    r'AudioVisualizerCanvas\(\n(\s*)showWaveform',
    r'AudioVisualizerCanvas(\n\1vm = vm,\n\1showWaveform',
    text
)

# Replace controls with scene selector.
controls_start = text.index('@Composable\nfun AudioVisualizerControls(')
canvas_start = text.index('\n@Composable\nfun AudioVisualizerCanvas(', controls_start)
new_controls = r'''@Composable
fun AudioVisualizerControls(
    vm: MainViewModel,
    showWaveform: Boolean,
    showColorscape: Boolean,
    onWaveform: (Boolean) -> Unit,
    onColorscape: (Boolean) -> Unit
) {
    val style by vm.visualizerStyle.collectAsStateWithLifecycle()
    var styleMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
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
        if (showColorscape) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Style", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { styleMenu = true }) {
                        Text(style)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                        listOf("Radial", "Kaleidoscope", "Blob", "Drops", "Nebula").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    vm.setVisualizerStyle(option)
                                    styleMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
'''
text = text[:controls_start] + new_controls + text[canvas_start:]

# Replace the canvas with five scenes driven by the same bass/mid/treble engine.
canvas_start = text.index('@Composable\nfun AudioVisualizerCanvas(')
canvas_end = text.index('\n@Composable\nfun NowPlayingScreen', canvas_start)
new_canvas = r'''@Composable
fun AudioVisualizerCanvas(
    vm: MainViewModel,
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    val frame by AudioVisualizationBus.frame.collectAsStateWithLifecycle()
    val style by vm.visualizerStyle.collectAsStateWithLifecycle()

    val energy by animateFloatAsState(frame.energy.coerceIn(0f, 1f), tween(110, easing = LinearOutSlowInEasing), label = "visualEnergy")
    val beat by animateFloatAsState(frame.beat.coerceIn(0f, 1f), tween(85, easing = FastOutSlowInEasing), label = "visualBeat")
    val bass by animateFloatAsState(frame.bass.coerceIn(0f, 1f), tween(130, easing = LinearOutSlowInEasing), label = "visualBass")
    val mids by animateFloatAsState(frame.mids.coerceIn(0f, 1f), tween(145, easing = LinearOutSlowInEasing), label = "visualMids")
    val treble by animateFloatAsState(frame.treble.coerceIn(0f, 1f), tween(160, easing = LinearOutSlowInEasing), label = "visualTreble")

    val transition = rememberInfiniteTransition(label = "colorscapeMotion")
    val phase by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "colorscapePhase"
    )
    val breathe by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "colorscapeBreath"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension
        val rad = phase * (Math.PI / 180.0)

        // Frequency-linked palette: lows are warm, mids lean magenta/green,
        // highs lean cyan/blue. Time only drifts each family gently.
        val bassHue = (12f + bass * 36f + phase * 0.035f) % 360f
        val midHue = (285f + mids * 62f + phase * 0.045f) % 360f
        val highHue = (185f + treble * 48f + phase * 0.060f) % 360f
        val warm = Color.hsv(bassHue, 0.82f, (0.48f + energy * 0.42f).coerceAtMost(1f))
        val middle = Color.hsv(midHue, 0.74f, (0.46f + mids * 0.42f).coerceAtMost(1f))
        val cool = Color.hsv(highHue, 0.70f, (0.52f + treble * 0.40f).coerceAtMost(1f))

        if (showColorscape) {
            when (style) {
                "Blob" -> {
                    drawRect(Brush.radialGradient(listOf(middle.copy(alpha=.42f), warm.copy(alpha=.24f), Color.Black), Offset(cx, cy), maxOf(size.width,size.height)*.8f))
                    repeat(3) { layer ->
                        val path = Path()
                        val points = 72
                        val baseR = minD * (0.19f + layer * 0.075f + bass * 0.045f + beat * 0.035f)
                        for (i in 0..points) {
                            val a = i.toFloat() / points * (Math.PI * 2.0)
                            val wave = frame.waveform.getOrNull((i + layer * 13) % maxOf(1, frame.waveform.size)) ?: 0f
                            val wobble = 1f + wave * (0.13f + mids * 0.10f) + kotlin.math.sin(a * (3 + layer) + rad * (1.2 + layer*.2)).toFloat() * (0.06f + treble*.06f)
                            val r = baseR * wobble
                            val x = cx + kotlin.math.cos(a).toFloat() * r
                            val y = cy + kotlin.math.sin(a).toFloat() * r
                            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                        }
                        path.close()
                        val c = when(layer){0->warm;1->middle;else->cool}.copy(alpha=.34f + layer*.12f + beat*.10f)
                        drawPath(path, c)
                    }
                }
                "Drops" -> {
                    drawRect(Brush.linearGradient(listOf(Color.Black, middle.copy(alpha=.35f), Color.Black), Offset.Zero, Offset(size.width,size.height)))
                    repeat(22) { i ->
                        val seed = i * 0.6180339f
                        val progress = ((phase / 360f * (0.7f + (i%5)*.08f) + seed) % 1f)
                        val angle = seed * 6.28318f + rad.toFloat() * (if(i%2==0) .18f else -.12f)
                        val distance = minD * (.06f + progress * .48f)
                        val x = cx + kotlin.math.cos(angle).toFloat()*distance
                        val y = cy + kotlin.math.sin(angle).toFloat()*distance
                        val band = when(i%3){0->bass;1->mids;else->treble}
                        val c = when(i%3){0->warm;1->middle;else->cool}
                        val r = minD * (.008f + band*.025f + beat*.018f) * (1.15f-progress*.45f)
                        drawCircle(c.copy(alpha=(.28f + band*.42f).coerceAtMost(.82f)), r, Offset(x,y))
                        if (beat > .25f && i%4==0) drawCircle(c.copy(alpha=.18f), r*(2.2f+beat), Offset(x,y), style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.5f+beat*3f))
                    }
                }
                "Nebula" -> {
                    drawRect(Color.Black)
                    val centers = listOf(
                        Offset(cx + kotlin.math.sin(rad*.7).toFloat()*size.width*.22f, cy + kotlin.math.cos(rad*.5).toFloat()*size.height*.18f),
                        Offset(cx + kotlin.math.cos(rad*.45).toFloat()*size.width*.28f, cy - kotlin.math.sin(rad*.8).toFloat()*size.height*.21f),
                        Offset(cx - kotlin.math.sin(rad*.35).toFloat()*size.width*.25f, cy + kotlin.math.cos(rad*.65).toFloat()*size.height*.24f)
                    )
                    listOf(warm,middle,cool).forEachIndexed { i,c ->
                        val band = listOf(bass,mids,treble)[i]
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha=.52f+band*.18f), c.copy(alpha=.13f), Color.Transparent), centers[i], minD*(.28f+band*.20f+beat*.05f)), minD*(.28f+band*.20f+beat*.05f), centers[i])
                    }
                    repeat(18) { i ->
                        val a = i/18f*6.28318f + rad.toFloat()*.12f
                        val r = minD*(.12f + (i%7)/10f*.35f)
                        drawCircle(Color.White.copy(alpha=.10f+treble*.22f), 1.2f+treble*2.5f, Offset(cx+kotlin.math.cos(a)*r,cy+kotlin.math.sin(a)*r))
                    }
                }
                "Radial" -> {
                    drawRect(Brush.radialGradient(listOf(warm.copy(alpha=.34f), middle.copy(alpha=.22f), Color.Black), Offset(cx,cy), minD*.72f))
                    val spokes = 36
                    repeat(spokes) { i ->
                        val a = i.toFloat()/spokes*6.28318f + rad.toFloat()*(.08f + mids*.08f)
                        val wave = frame.waveform.getOrNull(i % maxOf(1,frame.waveform.size)) ?: 0f
                        val band = when(i%3){0->bass;1->mids;else->treble}
                        val c = when(i%3){0->warm;1->middle;else->cool}
                        val inner = minD*(.08f + beat*.025f)
                        val outer = minD*(.30f + band*.16f + kotlin.math.abs(wave)*.08f)
                        drawLine(c.copy(alpha=.35f+band*.45f), Offset(cx+kotlin.math.cos(a)*inner,cy+kotlin.math.sin(a)*inner), Offset(cx+kotlin.math.cos(a)*outer,cy+kotlin.math.sin(a)*outer), 1.2f+band*4f+beat*3f)
                    }
                    drawCircle(cool.copy(alpha=.32f+beat*.25f), minD*(.07f+bass*.05f+beat*.045f), Offset(cx,cy))
                }
                else -> { // Kaleidoscope
                    drawRect(Brush.radialGradient(listOf(warm.copy(alpha=.42f), middle.copy(alpha=.32f), cool.copy(alpha=.24f), Color.Black), Offset(cx,cy), maxOf(size.width,size.height)*.74f))
                    val slices = 16
                    val rings = 6
                    repeat(slices) { slice ->
                        val base = (slice*(360f/slices) + phase*(.18f + mids*.10f)*(if(slice%2==0)1f else -1f)) * (Math.PI/180.0)
                        repeat(rings) { r0 ->
                            val ring = r0+1
                            val wave = frame.waveform.getOrNull((slice*5+ring*9)%maxOf(1,frame.waveform.size)) ?: 0f
                            val a = kotlin.math.abs(wave)
                            val radius = minD*.46f*(ring/rings.toFloat())*(.80f+bass*.10f+a*.14f+beat*.08f)
                            val wobble = wave*(.26f+mids*.12f) + kotlin.math.sin(rad*(.8+ring*.07)+slice*.4).toFloat()*(.08f+treble*.07f)
                            val a1=base+wobble; val a2=base-wobble
                            val p1=Offset(cx+kotlin.math.cos(a1).toFloat()*radius,cy+kotlin.math.sin(a1).toFloat()*radius)
                            val p2=Offset(cx+kotlin.math.cos(a2).toFloat()*radius,cy+kotlin.math.sin(a2).toFloat()*radius)
                            val c=when((slice+ring)%3){0->warm;1->middle;else->cool}.copy(alpha=.30f+a*.32f+energy*.18f)
                            val stroke=1.2f+energy*2f+beat*5f
                            drawLine(c,Offset(cx,cy),p1,stroke); drawLine(c,Offset(cx,cy),p2,stroke)
                            drawCircle(c.copy(alpha=.28f+beat*.30f),minD*(.006f+a*.020f+beat*.012f),p1)
                        }
                    }
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.30f+beat*.40f),cool.copy(alpha=.18f),Color.Transparent),Offset(cx,cy),minD*(.16f+beat*.12f)),minD*(.16f+beat*.12f),Offset(cx,cy))
                }
            }
        } else {
            drawRect(Color.White)
        }

        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY=size.height/2f
            val step=if(frame.waveform.size>1) size.width/(frame.waveform.size-1) else size.width
            frame.waveform.forEachIndexed { index,sample ->
                val x=index*step
                val amplitude=(kotlin.math.abs(sample)*.70f+energy*.18f+beat*.08f).coerceIn(.025f,1f)
                val half=amplitude*size.height*.43f
                drawLine(Color.Black.copy(alpha=.92f),Offset(x,centerY-half),Offset(x,centerY+half),maxOf(2f,step*.32f))
            }
        }
    }
}
'''
text = text[:canvas_start] + new_canvas + text[canvas_end:]

p.write_text(text)
