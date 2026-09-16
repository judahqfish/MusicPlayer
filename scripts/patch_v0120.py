from pathlib import Path

ui_path = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = ui_path.read_text()

# Make the whole colorscape breathe with the music, especially on kick/bass hits.
needle = '''        val warm = Color.hsv(bassHue, 0.82f, (0.48f + energy * 0.42f).coerceAtMost(1f))
        val middle = Color.hsv(midHue, 0.74f, (0.46f + mids * 0.42f).coerceAtMost(1f))
        val cool = Color.hsv(highHue, 0.70f, (0.52f + treble * 0.40f).coerceAtMost(1f))

        if (showColorscape) {'''
replacement = '''        val warm = Color.hsv(bassHue, 0.82f, (0.48f + energy * 0.42f).coerceAtMost(1f))
        val middle = Color.hsv(midHue, 0.74f, (0.46f + mids * 0.42f).coerceAtMost(1f))
        val cool = Color.hsv(highHue, 0.70f, (0.52f + treble * 0.40f).coerceAtMost(1f))

        // Beat pulse is deliberately nonlinear so ordinary passages stay fluid,
        // while a strong kick or bass transient visibly expands the scene.
        val hit = (beat * beat).coerceIn(0f, 1f)
        val pulse = 1f + hit * 0.24f + bass * hit * 0.10f
        val shimmer = 1f + treble * 0.08f

        if (showColorscape) {'''
if needle not in text:
    raise SystemExit('palette block not found')
text = text.replace(needle, replacement, 1)

# Strengthen pulse in each scene without making normal motion jerky.
text = text.replace(
    '''val baseR = minD * (0.19f + layer * 0.075f + bass * 0.045f + beat * 0.035f)''',
    '''val baseR = minD * (0.19f + layer * 0.075f + bass * 0.045f) * pulse''',
    1
)
text = text.replace(
    '''val distance = minD * (.06f + progress * .48f)''',
    '''val distance = minD * (.06f + progress * .48f) * (1f + hit * .12f)''',
    1
)
text = text.replace(
    '''val r = minD * (.008f + band*.025f + beat*.018f) * (1.15f-progress*.45f)''',
    '''val r = minD * (.008f + band*.025f + hit*.024f) * (1.15f-progress*.45f)''',
    1
)
text = text.replace(
    '''drawCircle(Brush.radialGradient(listOf(c.copy(alpha=.52f+band*.18f), c.copy(alpha=.13f), Color.Transparent), centers[i], minD*(.28f+band*.20f+beat*.05f)), minD*(.28f+band*.20f+beat*.05f), centers[i])''',
    '''drawCircle(Brush.radialGradient(listOf(c.copy(alpha=.52f+band*.18f+hit*.12f), c.copy(alpha=.13f), Color.Transparent), centers[i], minD*(.28f+band*.20f)*pulse), minD*(.28f+band*.20f)*pulse, centers[i])''',
    1
)
text = text.replace(
    '''val inner = minD*(.08f + beat*.025f)
                        val outer = minD*(.30f + band*.16f + kotlin.math.abs(wave)*.08f)''',
    '''val inner = minD*.08f*pulse
                        val outer = minD*(.30f + band*.16f + kotlin.math.abs(wave)*.08f)*pulse''',
    1
)
text = text.replace(
    '''drawCircle(cool.copy(alpha=.32f+beat*.25f), minD*(.07f+bass*.05f+beat*.045f), Offset(cx,cy))''',
    '''drawCircle(cool.copy(alpha=.32f+hit*.40f), minD*(.07f+bass*.05f)*pulse, Offset(cx,cy))''',
    1
)

# Replace the old radial-looking kaleidoscope with mirrored angular facets.
old_kaleidoscope = '''                else -> { // Kaleidoscope
                    drawRect(Brush.radialGradient(listOf(warm.copy(alpha=.42f), middle.copy(alpha=.32f), cool.copy(alpha=.24f), Color.Black), Offset(cx,cy), maxOf(size.width,size.height)*.74f))
                    val slices = 16
                    val rings = 6
                    repeat(slices) { slice ->
                        val base = slice * (2.0 * Math.PI / slices) + timeSeconds * (0.10 + mids * 0.07) * (if (slice % 2 == 0) 1.0 else -1.0)
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
                }'''
new_kaleidoscope = '''                else -> { // Kaleidoscope: mirrored, shifting geometric facets
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
if old_kaleidoscope not in text:
    raise SystemExit('old kaleidoscope block not found')
text = text.replace(old_kaleidoscope, new_kaleidoscope, 1)

# Keep waveform readable against the scene: white on the dark visualizer,
# black on the plain light background when colorscape is off.
old_waveform = '''        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY=size.height/2f
            val step=if(frame.waveform.size>1) size.width/(frame.waveform.size-1) else size.width
            frame.waveform.forEachIndexed { index,sample ->
                val x=index*step
                val amplitude=(kotlin.math.abs(sample)*.70f+energy*.18f+beat*.08f).coerceIn(.025f,1f)
                val half=amplitude*size.height*.43f
                drawLine(Color.Black.copy(alpha=.92f),Offset(x,centerY-half),Offset(x,centerY+half),maxOf(2f,step*.32f))
            }
        }'''
new_waveform = '''        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY = size.height / 2f
            val step = if (frame.waveform.size > 1) size.width / (frame.waveform.size - 1) else size.width
            val waveformColor = if (showColorscape) Color.White else Color.Black
            frame.waveform.forEachIndexed { index, sample ->
                val x = index * step
                val amplitude = (kotlin.math.abs(sample) * .70f + energy * .18f + hit * .13f).coerceIn(.025f, 1f)
                val half = amplitude * size.height * .43f
                // A subtle dark/light under-stroke keeps the waveform legible even
                // as the colorscape passes through bright patches.
                val under = if (showColorscape) Color.Black.copy(alpha = .42f) else Color.White.copy(alpha = .62f)
                drawLine(under, Offset(x, centerY - half), Offset(x, centerY + half), maxOf(3.5f, step * .40f))
                drawLine(waveformColor.copy(alpha = .96f), Offset(x, centerY - half), Offset(x, centerY + half), maxOf(2f, step * .27f))
            }
        }'''
if old_waveform not in text:
    raise SystemExit('waveform block not found')
text = text.replace(old_waveform, new_waveform, 1)

ui_path.write_text(text)
