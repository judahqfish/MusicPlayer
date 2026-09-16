package com.everywhen.offlinemusic

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * v0.1.30 visualizer renderer.
 *
 * The three core AGSL mappings below are adapted from the MIT-licensed
 * Android-AGSL-Shader-Playground by Mejdi Hafiene:
 * https://github.com/mejdi14/Android-AGSL-Shader-Playground
 *
 * In particular, the kaleidoscope mapping, liquid-chrome domain warp and
 * continuous wave mapping follow that project's working RuntimeShader effects.
 * We only adapt the size uniform to the actual visualizer viewport and feed our
 * player audio envelope into their exposed parameters.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ProvenVisualizerSurface(
    style: String,
    frame: VisualizationFrame,
    showWaveform: Boolean,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    val startNanos = remember { System.nanoTime() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> elapsed = (now - startNanos) / 1_000_000_000f }
        }
    }

    val shaderCode = remember(style) {
        when (style) {
            "Blob", "Nebula" -> LIQUID_CHROME_SHADER
            "Drops" -> COMPLEX_WAVE_SHADER
            else -> KALEIDOSCOPE_SHADER
        }
    }
    val shader = remember(shaderCode) { RuntimeShader(shaderCode) }
    val effect = remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, INPUT_SHADER).asComposeRenderEffect()
    }

    if (viewport.width > 0 && viewport.height > 0) {
        shader.setFloatUniform("uSize", floatArrayOf(viewport.width.toFloat(), viewport.height.toFloat()))
        shader.setFloatUniform("uTime", elapsed)
        when (style) {
            "Blob" -> {
                shader.setFloatUniform("uStrength", 9f + frame.energy * 7f)
                shader.setFloatUniform("uScale", 5.5f + frame.mids * 2.4f)
                shader.setFloatUniform("uSpeed", .16f + frame.treble * .09f)
            }
            "Nebula" -> {
                shader.setFloatUniform("uStrength", 6f + frame.energy * 5f)
                shader.setFloatUniform("uScale", 3.8f + frame.mids * 1.7f)
                shader.setFloatUniform("uSpeed", .10f + frame.treble * .06f)
            }
            "Drops" -> {
                shader.setFloatUniform("uSpeed", .10f + frame.energy * .08f)
                shader.setFloatUniform("uStrength", 4f + frame.bass * 7f)
                shader.setFloatUniform("uFrequency", 5f + frame.treble * 3f)
            }
            "Radial" -> {
                shader.setFloatUniform("uSegments", 12f)
                shader.setFloatUniform("uRotationSpeed", .035f + frame.mids * .020f)
            }
            else -> {
                shader.setFloatUniform("uSegments", 8f)
                shader.setFloatUniform("uRotationSpeed", .055f + frame.mids * .030f)
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { viewport = it }
            .graphicsLayer {
                renderEffect = if (viewport.width > 0 && viewport.height > 0) effect else null
            }
    ) {
        ProvenSourceTexture(style = style, frame = frame, elapsed = elapsed)

        if (showWaveform && frame.waveform.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val step = if (frame.waveform.size > 1) size.width / (frame.waveform.size - 1) else size.width
                frame.waveform.forEachIndexed { index, sample ->
                    val x = index * step
                    val amp = (abs(sample) * .50f + frame.energy * .08f).coerceIn(.02f, .72f)
                    val half = amp * size.height * .35f
                    drawLine(Color.Black.copy(alpha = .55f), Offset(x, centerY - half), Offset(x, centerY + half), max(2.6f, step * .32f))
                    drawLine(Color.White.copy(alpha = .92f), Offset(x, centerY - half), Offset(x, centerY + half), max(1.2f, step * .17f))
                }
            }
        }
    }
}

@Composable
private fun ProvenSourceTexture(style: String, frame: VisualizationFrame, elapsed: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension
        drawRect(Color.Black)

        val drift = (elapsed * 1.7f) % 360f
        val warm = Color.hsv((18f + drift + frame.bass * 20f) % 360f, .82f, (.60f + frame.energy * .22f).coerceAtMost(1f))
        val mid = Color.hsv((300f + drift * .55f + frame.mids * 30f) % 360f, .75f, (.58f + frame.energy * .24f).coerceAtMost(1f))
        val cool = Color.hsv((188f + drift * .78f + frame.treble * 30f) % 360f, .74f, (.64f + frame.energy * .20f).coerceAtMost(1f))
        val palette = listOf(warm, mid, cool, warm, cool, mid)

        // A slow, colorful source image. The proven shaders above perform the
        // mirroring/domain-warping; these shapes only give them material to sample.
        repeat(9) { i ->
            val a = i * .91f + elapsed * (.020f + (i % 3) * .004f)
            val orbit = minD * (.08f + (i % 5) * .075f)
            val center = Offset(cx + cos(a) * orbit, cy + sin(a * .83f) * orbit)
            val band = when (i % 3) { 0 -> frame.bass; 1 -> frame.mids; else -> frame.treble }
            val radius = minD * (.035f + (i % 4) * .018f + band * .018f)
            val c = palette[i % palette.size]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .18f + band * .08f), c.copy(alpha = .82f), c.copy(alpha = .18f), Color.Transparent),
                    center = center,
                    radius = radius * 2.5f
                ),
                radius = radius * 2.5f,
                center = center
            )
        }

        if (style == "Drops") {
            repeat(11) { i ->
                val p = i / 10f
                val x = size.width * (.08f + .84f * p)
                val y = cy + sin(i * 1.31f + elapsed * .05f) * size.height * .24f
                val r = minD * (.010f + (i % 3) * .004f + frame.bass * .004f)
                val c = palette[i % palette.size]
                drawCircle(c.copy(alpha = .55f), r, Offset(x, y))
                drawCircle(c.copy(alpha = .16f), r * 3.2f, Offset(x, y))
            }
        }
    }
}

private const val INPUT_SHADER = "inputShader"

// Directly adapted from Android-AGSL-Shader-Playground/KaleidoscopeEffect.kt.
private val KALEIDOSCOPE_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uTime;
    uniform float uSegments;
    uniform float uRotationSpeed;

    half4 main(float2 fragCoord) {
        float2 center = uSize * 0.5;
        float2 p = fragCoord - center;
        float r = length(p);
        float a = atan(p.y, p.x) + uTime * uRotationSpeed;
        float seg = 6.28318530718 / uSegments;
        a = mod(a, seg);
        a = abs(a - seg * 0.5);
        float2 sampleCoord = center + float2(cos(a), sin(a)) * r;
        sampleCoord = clamp(sampleCoord, float2(0.0), uSize);
        return inputShader.eval(sampleCoord);
    }
""".trimIndent()

// Directly adapted from Android-AGSL-Shader-Playground/LiquidChromeEffect.kt.
private val LIQUID_CHROME_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uTime;
    uniform float uStrength;
    uniform float uScale;
    uniform float uSpeed;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize;
        float t = uTime * uSpeed;
        float wx = sin(uv.y * uScale + t) + 0.5 * sin(uv.y * uScale * 2.3 - t * 1.7);
        float wy = cos(uv.x * uScale - t) + 0.5 * cos(uv.x * uScale * 1.9 + t * 1.3);
        float2 offset = float2(wx, wy) * uStrength;
        half4 color = inputShader.eval(fragCoord + offset);
        float sheen = 0.5 + 0.5 * sin((uv.x + uv.y) * uScale + t * 2.0);
        color.rgb += half3(0.18 * sheen) * color.a;
        return color;
    }
""".trimIndent()

// Directly adapted from Android-AGSL-Shader-Playground/ComplexWaveEffect.kt.
private val COMPLEX_WAVE_SHADER = """
    uniform shader inputShader;
    uniform float uTime;
    uniform float2 uSize;
    uniform float uSpeed;
    uniform float uStrength;
    uniform float uFrequency;

    half4 main(float2 fragCoord) {
        float2 normalizedPosition = fragCoord / uSize;
        float moveAmount = uTime * uSpeed;
        float2 newPosition = fragCoord;
        newPosition.x += sin((normalizedPosition.x + moveAmount) * uFrequency) * uStrength;
        newPosition.y += cos((normalizedPosition.y + moveAmount) * uFrequency) * uStrength;
        return inputShader.eval(newPosition);
    }
""".trimIndent()
