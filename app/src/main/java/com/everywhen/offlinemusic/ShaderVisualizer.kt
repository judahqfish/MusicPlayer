package com.everywhen.offlinemusic

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val INPUT_SHADER = "inputShader"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ShaderVisualizerSurface(
    style: String,
    frame: VisualizationFrame,
    showWaveform: Boolean,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    val startNanos = remember { System.nanoTime() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }

    var energy by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var mids by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }
    var beat by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(frame.sequence) {
        energy += (frame.energy - energy) * .028f
        bass += (frame.bass - bass) * .024f
        mids += (frame.mids - mids) * .030f
        treble += (frame.treble - treble) * .034f
        beat += (frame.beat - beat) * .020f
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                elapsed = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    val shaderCode = remember(style) { shaderFor(style) }
    val shader = remember(shaderCode) { runCatching { RuntimeShader(shaderCode) }.getOrNull() }
    val shaderEffect = remember(shader) {
        shader?.let { RenderEffect.createRuntimeShaderEffect(it, INPUT_SHADER).asComposeRenderEffect() }
    }

    if (shader != null && viewport.width > 0 && viewport.height > 0) {
        shader.setFloatUniform("uSize", floatArrayOf(viewport.width.toFloat(), viewport.height.toFloat()))
        shader.setFloatUniform("uTime", elapsed)
        shader.setFloatUniform("uEnergy", energy)
        shader.setFloatUniform("uBass", bass)
        shader.setFloatUniform("uMids", mids)
        shader.setFloatUniform("uTreble", treble)
        shader.setFloatUniform("uBeat", beat)
        shader.setFloatUniform(
            "uTouch",
            floatArrayOf(
                if (viewport.width > 0) touch.x / viewport.width else 0f,
                if (viewport.height > 0) touch.y / viewport.height else 0f
            )
        )
    }

    val interactive = modifier
        .onSizeChanged { viewport = it }
        .pointerInput(style, viewport) {
            detectDragGestures { _, delta ->
                touch = Offset(
                    (touch.x + delta.x).coerceIn(-size.width.toFloat(), size.width.toFloat()),
                    (touch.y + delta.y).coerceIn(-size.height.toFloat(), size.height.toFloat())
                )
            }
        }

    Box(interactive) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { renderEffect = shaderEffect }
        ) {
            VisualizerSourceTexture(
                style = style,
                time = elapsed,
                energy = energy,
                bass = bass,
                mids = mids,
                treble = treble
            )
        }

        if (showWaveform && frame.waveform.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val step = if (frame.waveform.size > 1) size.width / (frame.waveform.size - 1) else size.width
                frame.waveform.forEachIndexed { index, sample ->
                    val x = index * step
                    val amplitude = (abs(sample) * .48f + energy * .08f).coerceIn(.018f, .72f)
                    val half = amplitude * size.height * .34f
                    drawLine(Color.Black.copy(alpha = .55f), Offset(x, centerY - half), Offset(x, centerY + half), max(2.6f, step * .32f))
                    drawLine(Color.White.copy(alpha = .88f), Offset(x, centerY - half), Offset(x, centerY + half), max(1.2f, step * .17f))
                }
            }
        }
    }
}

@Composable
private fun VisualizerSourceTexture(
    style: String,
    time: Float,
    energy: Float,
    bass: Float,
    mids: Float,
    treble: Float
) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension
        drawRect(Color.Black)

        val hueDrift = (time * 2.2f) % 360f
        val warm = Color.hsv((18f + hueDrift + bass * 24f) % 360f, .78f, .62f + energy * .22f)
        val middle = Color.hsv((292f + hueDrift * .70f + mids * 42f) % 360f, .72f, .58f + energy * .24f)
        val cool = Color.hsv((188f + hueDrift * .92f + treble * 36f) % 360f, .68f, .64f + energy * .22f)
        val colors = listOf(warm, middle, cool, middle, warm, cool)

        repeat(6) { i ->
            val phase = time * (.030f + i * .004f) + i * 1.17f
            val center = Offset(
                cx + sin(phase) * size.width * (.10f + (i % 3) * .035f),
                cy + cos(phase * .83f) * size.height * (.08f + (i % 2) * .045f)
            )
            val radius = minD * (.26f + i * .025f + energy * .025f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors[i].copy(alpha = .24f), colors[i].copy(alpha = .07f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        when (style) {
            "Blob" -> {
                repeat(4) { layer ->
                    val path = Path()
                    val points = 96
                    val phase = time * (.045f + layer * .010f)
                    val baseR = minD * (.15f + layer * .065f)
                    for (i in 0..points) {
                        val a = i.toFloat() / points * (2f * PI.toFloat())
                        val wobble = sin(a * (2.4f + layer * .55f) + phase) * (.055f + mids * .020f) +
                            cos(a * (4.1f + layer * .30f) - phase * .73f) * (.025f + treble * .012f)
                        val r = baseR * (1f + wobble)
                        val x = cx + cos(a) * r
                        val y = cy + sin(a) * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    val c = colors[layer]
                    drawPath(path, c.copy(alpha = .10f + layer * .025f + energy * .045f))
                    drawPath(path, Color.White.copy(alpha = .022f), style = Stroke(width = .8f + layer * .25f))
                }
            }
            "Drops" -> {
                repeat(16) { i ->
                    val seed = i * .6180339f
                    val orbit = seed * 6.28318f + time * (.018f + (i % 4) * .002f)
                    val distance = minD * (.08f + ((seed + time * .006f) % 1f) * .42f)
                    val p = Offset(cx + cos(orbit) * distance, cy + sin(orbit) * distance)
                    val c = colors[i % colors.size]
                    val r = minD * (.010f + (i % 3) * .003f + energy * .004f)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .42f), c.copy(alpha = .10f), Color.Transparent), p, r * 4f), r * 4f, p)
                    drawCircle(c.copy(alpha = .18f), r * (2.6f + (seed * 2f)), p, style = Stroke(width = .7f))
                }
            }
            "Nebula" -> {
                repeat(34) { i ->
                    val a = i / 34f * 6.28318f + time * .004f
                    val r = minD * (.10f + (i % 13) / 13f * .43f)
                    val p = Offset(cx + cos(a) * r, cy + sin(a) * r)
                    val twinkle = .045f + treble * .040f + ((sin(time * .18f + i) + 1f) * .5f) * .020f
                    drawCircle(Color.White.copy(alpha = twinkle), .7f + treble * .7f, p)
                }
            }
            else -> {
                repeat(14) { i ->
                    val a = i / 14f * 6.28318f + time * .006f
                    val bend = sin(time * .045f + i * .81f) * (.13f + mids * .025f)
                    val inner = minD * .06f
                    val outer = minD * (.38f + (i % 3) * .018f)
                    val p0 = Offset(cx + cos(a) * inner, cy + sin(a) * inner)
                    val p1 = Offset(cx + cos(a + bend) * minD * .23f, cy + sin(a + bend) * minD * .23f)
                    val p2 = Offset(cx + cos(a + bend * .55f) * outer, cy + sin(a + bend * .55f) * outer)
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        quadraticBezierTo(p1.x, p1.y, p2.x, p2.y)
                    }
                    drawPath(path, colors[i % colors.size].copy(alpha = .12f + energy * .035f), style = Stroke(width = 1.2f + (i % 3) * .55f))
                }
            }
        }
    }
}

private fun shaderFor(style: String): String = when (style) {
    "Blob" -> LIQUID_SHADER
    "Drops" -> DROPS_SHADER
    "Nebula" -> NEBULA_SHADER
    "Radial" -> RADIAL_SHADER
    else -> KALEIDOSCOPE_SHADER
}

private val COMMON = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float2 uTouch;
    uniform float uTime;
    uniform float uEnergy;
    uniform float uBass;
    uniform float uMids;
    uniform float uTreble;
    uniform float uBeat;
""".trimIndent()

private val KALEIDOSCOPE_SHADER = COMMON + """

half4 main(float2 fragCoord) {
    float2 center = uSize * 0.5;
    float2 p = fragCoord - center;
    float t = uTime * (0.10 + uEnergy * 0.03);
    p += float2(
        sin(p.y * 0.010 + t) * (4.0 + uMids * 5.0),
        cos(p.x * 0.009 - t * 0.8) * (3.0 + uTreble * 4.0)
    );
    float r = length(p);
    float a = atan(p.y, p.x) + t * 0.18 + uTouch.x * 0.55;
    float seg = 6.2831853 / 8.0;
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    float breathe = 1.0 + uBass * 0.025 + 0.010 * sin(uTime * 0.15);
    float2 q = center + float2(cos(a), sin(a)) * r * breathe;
    q += float2(uTouch.x, uTouch.y) * uSize * 0.035;
    q = clamp(q, float2(0.0), uSize);
    return inputShader.eval(q);
}
""".trimIndent()

private val LIQUID_SHADER = COMMON + """

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime * (0.22 + uEnergy * 0.05);
    float wx = sin(uv.y * (5.0 + uMids * 1.2) + t) + 0.5 * sin(uv.y * 11.0 - t * 1.4);
    float wy = cos(uv.x * (4.6 + uBass) - t * 0.9) + 0.5 * cos(uv.x * 9.5 + t * 1.1);
    float strength = 7.0 + uEnergy * 7.0 + uBass * 3.0;
    float2 q = fragCoord + float2(wx, wy) * strength;
    q += float2(uTouch.x, uTouch.y) * uSize * 0.025;
    return inputShader.eval(clamp(q, float2(0.0), uSize));
}
""".trimIndent()

private val DROPS_SHADER = COMMON + """

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime * 0.20;
    float2 c1 = float2(0.28 + 0.08 * sin(t * 0.7), 0.34 + 0.07 * cos(t * 0.6));
    float2 c2 = float2(0.70 + 0.07 * cos(t * 0.5), 0.60 + 0.08 * sin(t * 0.8));
    float2 c3 = float2(0.52 + 0.10 * sin(t * 0.4 + 1.2), 0.24 + 0.06 * cos(t * 0.9));
    float d1 = length(uv - c1);
    float d2 = length(uv - c2);
    float d3 = length(uv - c3);
    float ripple = sin(d1 * 38.0 - t * 3.0) * exp(-d1 * 4.2)
                 + sin(d2 * 34.0 - t * 2.6) * exp(-d2 * 4.0)
                 + sin(d3 * 42.0 - t * 3.4) * exp(-d3 * 4.6);
    float2 dir = normalize((uv - c1) + float2(0.0001));
    float amount = (1.5 + uEnergy * 2.5 + uTreble * 1.2) * ripple;
    float2 q = fragCoord + dir * amount * 5.0;
    q += float2(uTouch.x, uTouch.y) * uSize * 0.020;
    return inputShader.eval(clamp(q, float2(0.0), uSize));
}
""".trimIndent()

private val NEBULA_SHADER = COMMON + """

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime * 0.08;
    float2 drift = float2(
        sin(uv.y * 5.0 + t) + 0.45 * sin(uv.y * 11.0 - t * 1.7),
        cos(uv.x * 4.2 - t * 0.8) + 0.45 * cos(uv.x * 9.0 + t * 1.3)
    );
    float2 q = fragCoord + drift * (5.0 + uEnergy * 5.0);
    q += float2(uTouch.x, uTouch.y) * uSize * 0.018;
    return inputShader.eval(clamp(q, float2(0.0), uSize));
}
""".trimIndent()

private val RADIAL_SHADER = COMMON + """

half4 main(float2 fragCoord) {
    float2 center = uSize * 0.5;
    float2 p = fragCoord - center;
    float r = length(p);
    float a = atan(p.y, p.x);
    float t = uTime * (0.08 + uEnergy * 0.02);
    a += sin(r * 0.018 - t) * (0.08 + uMids * 0.06);
    a += uTouch.x * 0.35;
    r *= 1.0 + 0.018 * sin(a * 6.0 + t * 1.2) + uBass * 0.012;
    float2 q = center + float2(cos(a), sin(a)) * r;
    q += float2(uTouch.x, uTouch.y) * uSize * 0.020;
    return inputShader.eval(clamp(q, float2(0.0), uSize));
}
""".trimIndent()
