from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

needle = '''    val timeSeconds = frameClockNanos / 1_000_000_000.0
    val breathe = ((kotlin.math.sin(timeSeconds * (2.0 * Math.PI / 5.6)) + 1.0) * 0.5).toFloat()

    Canvas(modifier = modifier) {'''
replacement = '''    val timeSeconds = frameClockNanos / 1_000_000_000.0
    val breathe = ((kotlin.math.sin(timeSeconds * (2.0 * Math.PI / 5.6)) + 1.0) * 0.5).toFloat()

    // Musical phase only advances when fresh playback audio arrives. This keeps the
    // kaleidoscope from looking like a free-running screensaver during quiet passages.
    var musicPhase by remember { mutableFloatStateOf(0f) }
    var beatLatch by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frame.sequence) {
        val drive = (frame.energy * .012f + frame.mids * .026f + frame.treble * .010f).coerceAtLeast(0f)
        musicPhase = (musicPhase + drive) % (2f * Math.PI.toFloat())
        beatLatch = maxOf(frame.beat, beatLatch * .72f)
    }

    Canvas(modifier = modifier) {'''
if needle not in text:
    raise SystemExit('visualizer clock insertion point not found')
text = text.replace(needle, replacement, 1)

old = '''                else -> { // Kaleidoscope: one procedural wedge reflected around the circle
                    drawRect(Color.Black)
                    val sectors = 12
                    val wedge = (2.0 * Math.PI / sectors)
                    val sourceRotation = timeSeconds * (0.10 + mids * 0.07)
                    val sourceCount = 15
                    val maxR = minD * .50f * pulse

                    fun mirroredAngle(sector: Int, local: Double): Double {
                        val bounded = ((local % wedge) + wedge) % wedge
                        val reflected = if (sector % 2 == 0) bounded else wedge - bounded
                        return sector * wedge + reflected + sourceRotation
                    }

                    // Build ONE animated source pattern in wedge-local coordinates.
                    // Every other sector uses wedge-local angle -> wedge-angle, which is
                    // a true axial reflection rather than another rotated copy.
                    repeat(sourceCount) { item ->
                        val wave = frame.waveform.getOrNull((item * 7) % maxOf(1, frame.waveform.size)) ?: 0f
                        val waveAbs = kotlin.math.abs(wave)
                        val band = when (item % 3) { 0 -> bass; 1 -> mids; else -> treble }
                        val phaseLocal = timeSeconds * (0.18 + (item % 5) * 0.025) + item * 0.73
                        val localCenter = wedge * (0.10 + 0.78 * ((kotlin.math.sin(phaseLocal) + 1.0) * .5))
                        val radialBase = maxR * (.12f + (item % 6) * .115f)
                        val radius = (radialBase * (0.82f + band * .22f + waveAbs * .16f)).coerceAtMost(maxR)
                        val angularSize = wedge * (0.055 + .055 * waveAbs + .035 * mids)
                        val radialSize = minD * (.018f + .030f * band + .020f * hit)
                        val c = when (item % 3) { 0 -> warm; 1 -> middle; else -> cool }

                        repeat(sectors) { sector ->
                            val a0 = mirroredAngle(sector, localCenter - angularSize)
                            val a1 = mirroredAngle(sector, localCenter + angularSize)
                            val a2 = mirroredAngle(sector, localCenter + angularSize * .32)
                            val r0 = (radius - radialSize).coerceAtLeast(minD * .025f)
                            val r1 = (radius + radialSize).coerceAtMost(maxR)

                            val p0 = Offset(cx + kotlin.math.cos(a0).toFloat() * r0, cy + kotlin.math.sin(a0).toFloat() * r0)
                            val p1 = Offset(cx + kotlin.math.cos(a1).toFloat() * r0, cy + kotlin.math.sin(a1).toFloat() * r0)
                            val p2 = Offset(cx + kotlin.math.cos(a2).toFloat() * r1, cy + kotlin.math.sin(a2).toFloat() * r1)

                            val triangle = Path().apply {
                                moveTo(p0.x, p0.y)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                close()
                            }
                            drawPath(
                                triangle,
                                c.copy(alpha = (.20f + energy * .14f + band * .18f + hit * .14f).coerceAtMost(.74f))
                            )
                            drawPath(
                                triangle,
                                Color.White.copy(alpha = .06f + treble * .10f + hit * .07f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f + treble * 1.4f + hit * 2.2f)
                            )
                        }
                    }

                    // A second source layer of mirrored connecting polygons creates
                    // the shifting stained-glass geometry associated with a real kaleidoscope.
                    repeat(6) { ringIndex ->
                        val localA = wedge * (.18 + .12 * ringIndex + .08 * kotlin.math.sin(timeSeconds * .55 + ringIndex))
                        val rA = minD * (.09f + ringIndex * .065f) * pulse
                        val rB = (rA + minD * (.045f + bass * .018f)).coerceAtMost(maxR)
                        repeat(sectors) { sector ->
                            val a = mirroredAngle(sector, localA)
                            val b = mirroredAngle(sector, localA + wedge * (.16 + treble * .05))
                            val cA = mirroredAngle(sector, localA + wedge * (.31 + mids * .04))
                            val pa = Offset(cx + kotlin.math.cos(a).toFloat() * rA, cy + kotlin.math.sin(a).toFloat() * rA)
                            val pb = Offset(cx + kotlin.math.cos(b).toFloat() * rB, cy + kotlin.math.sin(b).toFloat() * rB)
                            val pc = Offset(cx + kotlin.math.cos(cA).toFloat() * rA, cy + kotlin.math.sin(cA).toFloat() * rA)
                            val poly = Path().apply {
                                moveTo(pa.x, pa.y)
                                lineTo(pb.x, pb.y)
                                lineTo(pc.x, pc.y)
                                close()
                            }
                            val cc = when (ringIndex % 3) { 0 -> cool; 1 -> middle; else -> warm }
                            drawPath(poly, cc.copy(alpha = .12f + energy * .12f + hit * .10f))
                        }
                    }

                    val coreR = minD * (.055f + bass * .028f) * pulse
                    repeat(sectors) { sector ->
                        val a = mirroredAngle(sector, wedge * .5)
                        val p = Offset(cx + kotlin.math.cos(a).toFloat() * coreR, cy + kotlin.math.sin(a).toFloat() * coreR)
                        drawLine(Color.White.copy(alpha = .12f + hit * .32f), Offset(cx, cy), p, 1.5f + hit * 3.5f)
                    }
                }'''

new = '''                else -> { // Kaleidoscope: music causes the geometry
                    drawRect(Color.Black)
                    val sectors = 12
                    val wedge = (2.0 * Math.PI / sectors)
                    val musicalHit = maxOf(hit, beatLatch * beatLatch).coerceIn(0f, 1f)
                    val sourceRotation = musicPhase + mids * .22f + frame.waveform.firstOrNull().orZero() * .08f
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

if old not in text:
    raise SystemExit('v0.1.21 kaleidoscope block not found')
text = text.replace(old, new, 1)

# Kotlin helper-free nullable Float default in the generated expression.
text = text.replace('frame.waveform.firstOrNull().orZero()', '(frame.waveform.firstOrNull() ?: 0f)')

p.write_text(text)
