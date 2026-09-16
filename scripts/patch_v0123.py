from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Touch support for shifting the source texture beneath the mirrored wedges.
if 'import androidx.compose.foundation.gestures.detectDragGestures' not in text:
    text = text.replace('import androidx.compose.foundation.', 'import androidx.compose.foundation.gestures.detectDragGestures\nimport androidx.compose.foundation.', 1)
if 'import androidx.compose.ui.input.pointer.pointerInput' not in text:
    anchor = 'import androidx.compose.ui.graphics.Path\n'
    if anchor not in text:
        raise SystemExit('Path import anchor not found')
    text = text.replace(anchor, anchor + 'import androidx.compose.ui.input.pointer.pointerInput\n', 1)

needle = '''    var musicPhase by remember { mutableFloatStateOf(0f) }
    var beatLatch by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frame.sequence) {
        val drive = (frame.energy * .012f + frame.mids * .026f + frame.treble * .010f).coerceAtLeast(0f)
        musicPhase = (musicPhase + drive) % (2f * Math.PI.toFloat())
        beatLatch = maxOf(frame.beat, beatLatch * .72f)
    }

    Canvas(modifier = modifier) {'''
replacement = '''    var musicPhase by remember { mutableFloatStateOf(0f) }
    var beatLatch by remember { mutableFloatStateOf(0f) }
    var kaleidoTouch by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(frame.sequence) {
        val drive = (frame.energy * .012f + frame.mids * .026f + frame.treble * .010f).coerceAtLeast(0f)
        musicPhase = (musicPhase + drive) % (2f * Math.PI.toFloat())
        beatLatch = maxOf(frame.beat, beatLatch * .72f)
    }

    val interactiveModifier = modifier.pointerInput(style) {
        detectDragGestures { _, dragAmount ->
            if (style == "Kaleidoscope") kaleidoTouch += dragAmount
        }
    }

    Canvas(modifier = interactiveModifier) {'''
if needle not in text:
    raise SystemExit('v0.1.22 state/canvas block not found')
text = text.replace(needle, replacement, 1)

old = '''                else -> { // Kaleidoscope: music causes the geometry
                    drawRect(Color.Black)
                    val sectors = 12
                    val wedge = (2.0 * Math.PI / sectors)
                    val musicalHit = maxOf(hit, beatLatch * beatLatch).coerceIn(0f, 1f)
                    val sourceRotation = musicPhase + mids * .22f + (frame.waveform.firstOrNull() ?: 0f) * .08f
                    val sourceCount = (8 + mids * 10f + treble * 5f).toInt().coerceIn(8, 23)
                    val maxR = minD * (.43f + bass * .055f + musicalHit * .065f)

                    fun mirroredAngle(sector: Int, local: Double): Double {
                        val bounded = ((local % wedge) + wedge) % wedge
                        val reflected = if (sector % 2 == 0) bounded else wedge - bounded
                        return sector * wedge + reflected + sourceRotation
                    }

                    repeat(sourceCount) { item ->
                        val wave = frame.waveform.getOrNull((item * 7) % maxOf(1, frame.waveform.size)) ?: 0f
                        val wave2 = frame.waveform.getOrNull((item * 11 + 5) % maxOf(1, frame.waveform.size)) ?: 0f
                        val waveAbs = kotlin.math.abs(wave)
                        val band = when (item % 3) { 0 -> bass; 1 -> mids; else -> treble }

                        // Position is determined mostly by current audio, not elapsed time.
                        val localCenter = wedge * (0.50 + wave * .28 + wave2 * .11 + (mids - .5f) * .08)
                        val radialBase = maxR * (.10f + (item % 6) * .125f)
                        val radius = (radialBase * (.72f + bass * .20f + band * .16f + waveAbs * .20f + musicalHit * .12f)).coerceAtMost(maxR)
                        val angularSize = wedge * (.035 + .080 * waveAbs + .050 * mids + .030 * musicalHit)
                        val radialSize = minD * (.010f + .034f * band + .030f * musicalHit)
                        val c = when (item % 3) { 0 -> warm; 1 -> middle; else -> cool }

                        repeat(sectors) { sector ->
                            val a0 = mirroredAngle(sector, localCenter - angularSize)
                            val a1 = mirroredAngle(sector, localCenter + angularSize)
                            val a2 = mirroredAngle(sector, localCenter + angularSize * (.20 + treble * .25))
                            val r0 = (radius - radialSize).coerceAtLeast(minD * .018f)
                            val r1 = (radius + radialSize).coerceAtMost(maxR)
                            val p0 = Offset(cx + kotlin.math.cos(a0).toFloat() * r0, cy + kotlin.math.sin(a0).toFloat() * r0)
                            val p1 = Offset(cx + kotlin.math.cos(a1).toFloat() * r0, cy + kotlin.math.sin(a1).toFloat() * r0)
                            val p2 = Offset(cx + kotlin.math.cos(a2).toFloat() * r1, cy + kotlin.math.sin(a2).toFloat() * r1)
                            val triangle = Path().apply { moveTo(p0.x,p0.y); lineTo(p1.x,p1.y); lineTo(p2.x,p2.y); close() }
                            drawPath(triangle, c.copy(alpha = (.06f + energy*.20f + band*.18f + musicalHit*.26f).coerceAtMost(.82f)))
                            drawPath(triangle, Color.White.copy(alpha = .02f + treble*.14f + musicalHit*.15f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = .7f + treble*2.0f + musicalHit*3.6f))
                        }
                    }

                    // Bass opens/closes the rings. Mids twist them. Treble sharpens edges.
                    repeat(6) { ringIndex ->
                        val rw = frame.waveform.getOrNull((ringIndex * 8 + 3) % maxOf(1, frame.waveform.size)) ?: 0f
                        val localA = wedge * (.20 + .10 * ringIndex + rw * .12 + mids * .06)
                        val rA = minD * (.075f + ringIndex * .062f) * (.78f + bass*.28f + musicalHit*.18f)
                        val rB = (rA + minD * (.025f + bass*.032f + musicalHit*.020f)).coerceAtMost(maxR)
                        repeat(sectors) { sector ->
                            val a = mirroredAngle(sector, localA)
                            val b = mirroredAngle(sector, localA + wedge * (.11 + treble*.10))
                            val cA = mirroredAngle(sector, localA + wedge * (.24 + mids*.11))
                            val pa = Offset(cx + kotlin.math.cos(a).toFloat()*rA, cy + kotlin.math.sin(a).toFloat()*rA)
                            val pb = Offset(cx + kotlin.math.cos(b).toFloat()*rB, cy + kotlin.math.sin(b).toFloat()*rB)
                            val pc = Offset(cx + kotlin.math.cos(cA).toFloat()*rA, cy + kotlin.math.sin(cA).toFloat()*rA)
                            val poly = Path().apply { moveTo(pa.x,pa.y); lineTo(pb.x,pb.y); lineTo(pc.x,pc.y); close() }
                            val cc = when (ringIndex % 3) { 0 -> cool; 1 -> middle; else -> warm }
                            drawPath(poly, cc.copy(alpha = .025f + energy*.14f + musicalHit*.18f))
                        }
                    }

                    val coreR = minD * (.035f + bass*.050f + musicalHit*.055f)
                    repeat(sectors) { sector ->
                        val a = mirroredAngle(sector, wedge*.5)
                        val p = Offset(cx + kotlin.math.cos(a).toFloat()*coreR, cy + kotlin.math.sin(a).toFloat()*coreR)
                        drawLine(Color.White.copy(alpha = .05f + musicalHit*.55f), Offset(cx,cy), p, 1f + musicalHit*5f)
                    }
                }'''

new = '''                else -> { // Kaleidoscope: literal clipped wedge + reflected canvas transforms
                    drawRect(Color.Black)
                    val sectors = 8
                    val sectorDegrees = 360f / sectors
                    val halfAngle = Math.toRadians((sectorDegrees / 2f).toDouble())
                    val musicalHit = maxOf(hit, beatLatch * beatLatch).coerceIn(0f, 1f)
                    val radius = minD * (.54f + musicalHit * .04f)
                    val touchX = (kaleidoTouch.x / maxOf(1f, size.width)).coerceIn(-2f, 2f)
                    val touchY = (kaleidoTouch.y / maxOf(1f, size.height)).coerceIn(-2f, 2f)

                    // One triangular source wedge centered on +X. Everything below is drawn
                    // once in this coordinate system, then that exact wedge is rotated and
                    // every adjacent sector is reflected with a real canvas scale transform.
                    val wedgeClip = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx + kotlin.math.cos(-halfAngle).toFloat() * radius, cy + kotlin.math.sin(-halfAngle).toFloat() * radius)
                        lineTo(cx + kotlin.math.cos(halfAngle).toFloat() * radius, cy + kotlin.math.sin(halfAngle).toFloat() * radius)
                        close()
                    }

                    repeat(sectors) { sector ->
                        withTransform({
                            rotate(degrees = sector * sectorDegrees, pivot = Offset(cx, cy))
                            if (sector % 2 == 1) scale(scaleX = 1f, scaleY = -1f, pivot = Offset(cx, cy))
                        }) {
                            clipPath(wedgeClip) {
                                // Dark colored glass underneath the shapes.
                                drawRect(
                                    Brush.radialGradient(
                                        listOf(
                                            warm.copy(alpha = .12f + bass * .18f + musicalHit * .12f),
                                            middle.copy(alpha = .10f + mids * .17f),
                                            cool.copy(alpha = .08f + treble * .16f),
                                            Color.Black
                                        ),
                                        center = Offset(cx + radius * (.22f + touchX * .08f), cy + radius * touchY * .08f),
                                        radius = radius
                                    )
                                )

                                val count = (10 + mids * 8f + treble * 5f).toInt().coerceIn(10, 23)
                                repeat(count) { item ->
                                    val w1 = frame.waveform.getOrNull((item * 5 + 1) % maxOf(1, frame.waveform.size)) ?: 0f
                                    val w2 = frame.waveform.getOrNull((item * 9 + 4) % maxOf(1, frame.waveform.size)) ?: 0f
                                    val band = when (item % 3) { 0 -> bass; 1 -> mids; else -> treble }
                                    val slowDrift = timeSeconds.toFloat() * (.015f + energy * .055f)
                                    val localPhase = musicPhase * (.55f + (item % 4) * .11f) + slowDrift + item * .61f + touchX * 1.8f
                                    val baseR = radius * (.10f + (item % 7) * .105f)
                                    val rr = (baseR * (.76f + bass * .20f + band * .16f + kotlin.math.abs(w1) * .18f + musicalHit * .13f)).coerceAtMost(radius * .93f)
                                    val localAngle = (kotlin.math.sin(localPhase) * (.18f + mids * .12f) + w1 * .17f + touchY * .12f)
                                    val px = cx + kotlin.math.cos(localAngle) * rr
                                    val py = cy + kotlin.math.sin(localAngle) * rr
                                    val sz = minD * (.012f + band * .030f + kotlin.math.abs(w2) * .022f + musicalHit * .024f)
                                    val cc = when (item % 3) { 0 -> warm; 1 -> middle; else -> cool }

                                    if (item % 3 == 0) {
                                        drawCircle(
                                            Brush.radialGradient(listOf(cc.copy(alpha=.68f), cc.copy(alpha=.15f), Color.Transparent), Offset(px,py), sz*2.5f),
                                            sz*2.5f,
                                            Offset(px,py)
                                        )
                                    } else {
                                        val tri = Path().apply {
                                            moveTo(px + sz, py)
                                            lineTo(px - sz*.55f, py + sz*(.70f + w2*.35f))
                                            lineTo(px - sz*.55f, py - sz*(.70f - w2*.35f))
                                            close()
                                        }
                                        drawPath(tri, cc.copy(alpha=(.18f + energy*.18f + band*.28f + musicalHit*.22f).coerceAtMost(.86f)))
                                        drawPath(tri, Color.White.copy(alpha=.04f + treble*.13f + musicalHit*.12f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=.8f+treble*1.6f+musicalHit*2.4f))
                                    }
                                }

                                // Bass opens the central glass ring; onset makes it snap outward.
                                val ringR = minD * (.10f + bass*.095f + musicalHit*.070f)
                                drawCircle(
                                    middle.copy(alpha=.14f + energy*.16f + musicalHit*.20f),
                                    ringR,
                                    Offset(cx + touchX*minD*.025f, cy + touchY*minD*.025f),
                                    style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.2f + bass*3f + musicalHit*4f)
                                )
                            }
                        }
                    }

                    // Crisp center star makes the eight reflected joins visually obvious.
                    val core = minD * (.035f + bass*.030f + musicalHit*.040f)
                    repeat(sectors) { i ->
                        val a = Math.toRadians((i * sectorDegrees).toDouble())
                        val p = Offset(cx + kotlin.math.cos(a).toFloat()*core, cy + kotlin.math.sin(a).toFloat()*core)
                        drawLine(Color.White.copy(alpha=.08f + musicalHit*.48f), Offset(cx,cy), p, 1f + musicalHit*3.8f)
                    }
                }'''

if old not in text:
    raise SystemExit('v0.1.22 kaleidoscope block not found')
text = text.replace(old, new, 1)
p.write_text(text)
