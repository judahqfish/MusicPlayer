from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Replace the four legacy scene blocks with slower, layered, hypnotic scenes.
blob_start = text.index('                "Blob" -> {')
drops_start = text.index('                "Drops" -> {', blob_start)
nebula_start = text.index('                "Nebula" -> {', drops_start)
radial_start = text.index('                "Radial" -> {', nebula_start)
kaleido_start = text.index('                else -> { // Kaleidoscope', radial_start)

blob = r'''                "Blob" -> {
                    drawRect(Color.Black)
                    drawRect(
                        Brush.radialGradient(
                            listOf(
                                middle.copy(alpha=.20f + calmMids*.08f),
                                warm.copy(alpha=.10f + calmBass*.05f),
                                cool.copy(alpha=.07f + calmTreble*.04f),
                                Color.Black
                            ),
                            Offset(cx, cy), maxOf(size.width,size.height)*.82f
                        )
                    )
                    repeat(5) { layer ->
                        val path = Path()
                        val points = 96
                        val baseR = minD * (.16f + layer*.052f + calmBass*.012f)
                        val layerPhase = timeSeconds.toFloat() * (.055f + layer*.011f) + musicPhase*(.18f+layer*.025f)
                        for (i in 0..points) {
                            val a = i.toFloat()/points * 6.28318f
                            val w1 = frame.waveform.getOrNull((i*3 + layer*7) % maxOf(1,frame.waveform.size)) ?: 0f
                            val harmonic = kotlin.math.sin(a*(2.2f+layer*.55f) + layerPhase).toFloat() * (.025f + calmMids*.018f)
                            val harmonic2 = kotlin.math.cos(a*(3.7f+layer*.35f) - layerPhase*.73f).toFloat() * (.018f + calmTreble*.012f)
                            val audioContour = w1 * .010f
                            val r = baseR * (1f + harmonic + harmonic2 + audioContour)
                            val driftX = kotlin.math.sin(layerPhase*.42f + layer).toFloat()*minD*.020f
                            val driftY = kotlin.math.cos(layerPhase*.36f + layer*.7f).toFloat()*minD*.018f
                            val x = cx + driftX + kotlin.math.cos(a).toFloat()*r
                            val y = cy + driftY + kotlin.math.sin(a).toFloat()*r
                            if (i==0) path.moveTo(x,y) else path.lineTo(x,y)
                        }
                        path.close()
                        val c = when(layer%3){0->warm;1->middle;else->cool}
                        drawPath(path, c.copy(alpha=.10f + layer*.035f + calmEnergy*.06f))
                        drawPath(path, Color.White.copy(alpha=.025f + calmTreble*.025f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=.8f+layer*.18f))
                    }
                }
'''

drops = r'''                "Drops" -> {
                    drawRect(Color.Black)
                    drawRect(Brush.linearGradient(listOf(cool.copy(alpha=.09f), middle.copy(alpha=.13f), warm.copy(alpha=.07f), Color.Black), Offset.Zero, Offset(size.width,size.height)))
                    repeat(18) { i ->
                        val seed = i * .6180339f
                        val speed = .010f + (i%5)*.0025f + calmEnergy*.003f
                        val progress = ((timeSeconds.toFloat()*speed + seed) % 1f)
                        val orbit = seed*6.28318f + timeSeconds.toFloat()*(.010f + (i%3)*.003f)
                        val band = when(i%3){0->calmBass;1->calmMids;else->calmTreble}
                        val c = when(i%3){0->warm;1->middle;else->cool}
                        val distance = minD*(.05f + progress*.47f)
                        val x = cx + kotlin.math.cos(orbit).toFloat()*distance
                        val y = cy + kotlin.math.sin(orbit).toFloat()*distance
                        val core = minD*(.005f + band*.007f)
                        val glow = core*(3.6f + calmEnergy*1.8f)
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha=.34f+band*.12f), c.copy(alpha=.11f), Color.Transparent), Offset(x,y), glow), glow, Offset(x,y))
                        drawCircle(c.copy(alpha=.20f+band*.12f), core, Offset(x,y))
                        val rippleR = core*(2.4f + progress*5.5f)
                        drawCircle(c.copy(alpha=(.09f*(1f-progress)+calmEnergy*.018f).coerceAtLeast(.015f)), rippleR, Offset(x,y), style=androidx.compose.ui.graphics.drawscope.Stroke(width=.7f+band*.55f))
                        val tail = minD*(.018f + calmMids*.010f)
                        val tx = x - kotlin.math.cos(orbit).toFloat()*tail
                        val ty = y - kotlin.math.sin(orbit).toFloat()*tail
                        drawLine(c.copy(alpha=.055f+band*.035f), Offset(tx,ty), Offset(x,y), .7f+band*.45f)
                    }
                }
'''

nebula = r'''                "Nebula" -> {
                    drawRect(Color.Black)
                    val t = timeSeconds.toFloat()
                    val centers = listOf(
                        Offset(cx + kotlin.math.sin(t*.050f).toFloat()*size.width*.17f, cy + kotlin.math.cos(t*.041f).toFloat()*size.height*.14f),
                        Offset(cx + kotlin.math.cos(t*.037f).toFloat()*size.width*.22f, cy - kotlin.math.sin(t*.054f).toFloat()*size.height*.17f),
                        Offset(cx - kotlin.math.sin(t*.031f).toFloat()*size.width*.20f, cy + kotlin.math.cos(t*.046f).toFloat()*size.height*.20f),
                        Offset(cx + kotlin.math.cos(t*.026f+1.3f).toFloat()*size.width*.13f, cy + kotlin.math.sin(t*.033f+.8f).toFloat()*size.height*.12f)
                    )
                    val colors = listOf(warm,middle,cool,middle)
                    val bands = listOf(calmBass,calmMids,calmTreble,calmEnergy)
                    centers.forEachIndexed { i, center ->
                        val band = bands[i]
                        val rr = minD*(.24f + i*.018f + band*.045f)
                        drawCircle(Brush.radialGradient(listOf(colors[i].copy(alpha=.24f+band*.10f), colors[i].copy(alpha=.07f), Color.Transparent), center, rr), rr, center)
                    }
                    repeat(30) { i ->
                        val a = i/30f*6.28318f + t*.006f*(if(i%2==0)1f else -1f)
                        val r = minD*(.09f + (i%11)/11f*.42f)
                        val x = cx + kotlin.math.cos(a).toFloat()*r
                        val y = cy + kotlin.math.sin(a).toFloat()*r
                        val sparkle = .045f + calmTreble*.055f + kotlin.math.sin(t*.15f+i).toFloat().coerceAtLeast(0f)*.025f
                        drawCircle(Color.White.copy(alpha=sparkle), .8f+calmTreble*.9f, Offset(x,y))
                    }
                }
'''

radial = r'''                "Radial" -> {
                    drawRect(Color.Black)
                    drawRect(Brush.radialGradient(listOf(middle.copy(alpha=.16f), warm.copy(alpha=.09f), Color.Black), Offset(cx,cy), minD*.72f))
                    val t = timeSeconds.toFloat()
                    val ribbons = 18
                    repeat(ribbons) { i ->
                        val baseA = i.toFloat()/ribbons*6.28318f + t*(.008f + calmMids*.004f)
                        val band = when(i%3){0->calmBass;1->calmMids;else->calmTreble}
                        val c = when(i%3){0->warm;1->middle;else->cool}
                        val inner = minD*(.10f + calmBass*.010f)
                        val outer = minD*(.34f + band*.035f)
                        val bend = kotlin.math.sin(t*.06f + i*.73f).toFloat()*(.055f+calmMids*.020f)
                        val p0 = Offset(cx+kotlin.math.cos(baseA).toFloat()*inner, cy+kotlin.math.sin(baseA).toFloat()*inner)
                        val midA = baseA + bend
                        val p1 = Offset(cx+kotlin.math.cos(midA).toFloat()*((inner+outer)*.52f), cy+kotlin.math.sin(midA).toFloat()*((inner+outer)*.52f))
                        val endA = baseA + bend*.55f
                        val p2 = Offset(cx+kotlin.math.cos(endA).toFloat()*outer, cy+kotlin.math.sin(endA).toFloat()*outer)
                        val path = Path().apply { moveTo(p0.x,p0.y); quadraticBezierTo(p1.x,p1.y,p2.x,p2.y) }
                        drawPath(path, c.copy(alpha=.14f+band*.10f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.4f+band*1.4f))
                        drawCircle(c.copy(alpha=.10f+band*.06f), 2.2f+band*1.6f, p2)
                    }
                    val halo = minD*(.075f + calmBass*.012f)
                    drawCircle(Brush.radialGradient(listOf(cool.copy(alpha=.14f), middle.copy(alpha=.07f), Color.Transparent), Offset(cx,cy), halo*2.2f), halo*2.2f, Offset(cx,cy))
                }
'''

text = text[:blob_start] + blob + drops + nebula + radial + text[kaleido_start:]
p.write_text(text)
