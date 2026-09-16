from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

old = '''                else -> { // Kaleidoscope: mirrored, shifting geometric facets
                    drawRect(Color.Black)
                    val sectors = 12
                    val sectorAngle = (2.0 * Math.PI / sectors)
                    val rings = 5
                    val rotation = timeSeconds * (0.055 + mids * 0.045)
                    val twist = kotlin.math.sin(timeSeconds * 0.63).toFloat() * (0.10f + mids * 0.11f)

                    repeat(sectors) { sector ->
                        val mirror = if (sector % 2 == 0) 1f else -1f
                        val centerAngle = sector * sectorAngle + rotation * mirror
                        repeat(rings) { r0 ->
                            val ring = r0 + 1
                            val innerR = minD * (.055f + (ring - 1) * .075f) * pulse
                            val outerR = minD * (.12f + ring * .075f) * pulse
                            val wave = frame.waveform.getOrNull((sector * 7 + ring * 11) % maxOf(1, frame.waveform.size)) ?: 0f
                            val waveAbs = kotlin.math.abs(wave)
                            val half = sectorAngle * (0.31 + 0.08 * kotlin.math.sin(timeSeconds * (0.48 + ring * .07) + ring).toFloat())
                            val skew = (wave * (0.18f + mids * 0.13f) + twist * mirror) * shimmer

                            val a0 = centerAngle - half + skew
                            val a1 = centerAngle + half + skew
                            val a2 = centerAngle + half * .34 - skew * .55
                            val a3 = centerAngle - half * .34 - skew * .55

                            val p0 = Offset(cx + kotlin.math.cos(a0).toFloat() * innerR, cy + kotlin.math.sin(a0).toFloat() * innerR)
                            val p1 = Offset(cx + kotlin.math.cos(a1).toFloat() * innerR, cy + kotlin.math.sin(a1).toFloat() * innerR)
                            val p2 = Offset(cx + kotlin.math.cos(a2).toFloat() * outerR, cy + kotlin.math.sin(a2).toFloat() * outerR)
                            val p3 = Offset(cx + kotlin.math.cos(a3).toFloat() * outerR, cy + kotlin.math.sin(a3).toFloat() * outerR)

                            val facet = Path().apply {
                                moveTo(p0.x, p0.y)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                lineTo(p3.x, p3.y)
                                close()
                            }
                            val c = when ((sector + ring) % 3) { 0 -> warm; 1 -> middle; else -> cool }
                            drawPath(
                                facet,
                                c.copy(alpha = (.18f + waveAbs * .24f + energy * .16f + hit * .16f).coerceAtMost(.78f))
                            )
                            drawPath(
                                facet,
                                Color.White.copy(alpha = .08f + treble * .10f + hit * .08f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f + treble * 1.8f + hit * 2.6f)
                            )
                        }
                    }

                    // Central mirrored diamond gives a clear pulse on strong beats.
                    val coreR = minD * (.075f + bass * .025f) * pulse
                    val core = Path().apply {
                        moveTo(cx, cy - coreR)
                        lineTo(cx + coreR, cy)
                        lineTo(cx, cy + coreR)
                        lineTo(cx - coreR, cy)
                        close()
                    }
                    drawPath(core, Color.White.copy(alpha = .12f + hit * .34f))
                    drawPath(core, cool.copy(alpha = .38f + hit * .28f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f + hit * 4f))
                }'''

new = '''                else -> { // Kaleidoscope: one procedural wedge reflected around the circle
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

if old not in text:
    raise SystemExit('v0.1.20 kaleidoscope block not found')
text = text.replace(old, new, 1)
p.write_text(text)
