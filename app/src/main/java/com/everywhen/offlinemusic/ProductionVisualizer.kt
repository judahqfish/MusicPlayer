package com.everywhen.offlinemusic

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max

/**
 * v0.1.34 production visualizer.
 *
 * Rendering follows the modern Android AGSL pattern used by public Compose shader
 * projects such as drinkthestars/shady and AndroidPoet/mirage: a source-less
 * RuntimeShader is drawn directly through ShaderBrush. This avoids the previous
 * RenderEffect/input-texture path that rendered blank on some devices.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ProductionVisualizerSurface(
    style: String,
    frame: VisualizationFrame,
    showWaveform: Boolean,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    val start = remember { System.nanoTime() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }

    val source = remember(style) {
        when (style) {
            "Blob" -> BLOB_SHADER
            "Drops" -> DROPS_SHADER
            "Nebula" -> NEBULA_SHADER
            "Radial" -> RADIAL_SHADER
            else -> KALEIDOSCOPE_SHADER
        }
    }
    val shader = remember(source) { RuntimeShader(source) }
    val brush = remember(shader) { ShaderBrush(shader) }

    if (viewport.width > 0 && viewport.height > 0) {
        shader.setFloatUniform("uSize", viewport.width.toFloat(), viewport.height.toFloat())
        shader.setFloatUniform("uTime", elapsed)
        shader.setFloatUniform("uEnergy", frame.energy.coerceIn(0f, 1f))
        shader.setFloatUniform("uBass", frame.bass.coerceIn(0f, 1f))
        shader.setFloatUniform("uMids", frame.mids.coerceIn(0f, 1f))
        shader.setFloatUniform("uTreble", frame.treble.coerceIn(0f, 1f))
        shader.setFloatUniform("uBeat", frame.beat.coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { viewport = it }
            .fillMaxSize()
    ) {
        drawRect(Color.Black)
        if (viewport.width > 0 && viewport.height > 0) drawRect(brush)

        if (showWaveform && frame.waveform.isNotEmpty()) {
            val centerY = size.height * .5f
            val step = if (frame.waveform.size > 1) size.width / (frame.waveform.size - 1) else size.width
            frame.waveform.forEachIndexed { index, sample ->
                val x = index * step
                val amp = (abs(sample) * .52f + frame.energy * .06f).coerceIn(.02f, .70f)
                val half = amp * size.height * .32f
                drawLine(Color.Black.copy(alpha = .48f), Offset(x, centerY - half), Offset(x, centerY + half), max(3f, step * .30f))
                drawLine(Color.White.copy(alpha = .86f), Offset(x, centerY - half), Offset(x, centerY + half), max(1.2f, step * .14f))
            }
        }
    }
}

private const val COMMON = """
uniform float2 uSize;
uniform float uTime;
uniform float uEnergy;
uniform float uBass;
uniform float uMids;
uniform float uTreble;
uniform float uBeat;

float3 palette(float x) {
    float3 a = float3(0.50, 0.50, 0.50);
    float3 b = float3(0.50, 0.50, 0.50);
    float3 c = float3(1.00, 1.00, 1.00);
    float3 d = float3(0.00, 0.33, 0.67);
    return a + b * cos(6.2831853 * (c * x + d));
}
"""

private val KALEIDOSCOPE_SHADER = (COMMON + """
half4 main(float2 fragCoord) {
    float m = min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5 * uSize) / m;
    float r = length(p);
    float a = atan(p.y, p.x) + uTime * (0.055 + 0.030 * uMids);
    float seg = 6.2831853 / 12.0;
    a = mod(a + 6.2831853, seg);
    a = abs(a - 0.5 * seg);
    float2 q = float2(cos(a), sin(a)) * r;
    q += 0.035 * float2(
        sin(q.y * 19.0 + uTime * 0.27),
        cos(q.x * 17.0 - uTime * 0.23)
    );
    float bands = sin(q.x * 25.0 + sin(q.y * 9.0 + uTime * 0.31) * 3.0)
                + cos(q.y * 21.0 - cos(q.x * 7.0 - uTime * 0.24) * 2.5);
    float petals = cos(18.0 * atan(q.y, q.x) + sin(r * 28.0 - uTime * 0.35));
    float v = 0.5 + 0.24 * bands + 0.18 * petals + 0.12 * uBass + 0.08 * uBeat;
    float glow = exp(-2.6 * r) + 0.22 * exp(-18.0 * abs(r - 0.30 - 0.025 * sin(uTime * 0.2)));
    float3 col = palette(v + uTime * 0.015);
    col *= 0.28 + 0.95 * glow;
    col += 0.16 * palette(v + 0.35) * smoothstep(0.75, 0.0, r);
    return half4(half3(col), 1.0);
}
""").trimIndent()

private val BLOB_SHADER = (COMMON + """
float ball(float2 p, float2 c, float s) {
    float2 d = p - c;
    return s / (dot(d, d) + 0.018);
}

half4 main(float2 fragCoord) {
    float m = min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5 * uSize) / m;
    float t = uTime * (0.18 + 0.06 * uEnergy);
    float f = 0.0;
    f += ball(p, float2(0.22*sin(t), 0.18*cos(t*0.83)), 0.016 + 0.006*uBass);
    f += ball(p, float2(0.24*cos(t*0.71+1.2), 0.20*sin(t*0.62+0.5)), 0.014 + 0.005*uMids);
    f += ball(p, float2(0.18*sin(t*0.56+2.0), 0.25*cos(t*0.74+1.7)), 0.013 + 0.004*uTreble);
    f += ball(p, float2(0.31*cos(t*0.39+2.8), 0.10*sin(t*0.91)), 0.011);
    f += ball(p, float2(0.09*cos(t*1.07+4.0), 0.30*sin(t*0.43+3.0)), 0.010);
    float body = smoothstep(0.72, 1.65, f);
    float edge = smoothstep(0.78, 1.02, f) - smoothstep(1.34, 1.80, f);
    float3 col = palette(0.08*uTime + f*0.10 + 0.10*uMids);
    col *= body * (0.55 + 0.55*uEnergy);
    col += edge * (0.45 + 0.35*uBeat) * float3(0.9, 0.75, 1.0);
    float haze = exp(-5.0 * length(p));
    col += haze * 0.08 * palette(0.3 + 0.03*uTime);
    return half4(half3(col), 1.0);
}
""").trimIndent()

private val DROPS_SHADER = (COMMON + """
float ripple(float2 p, float2 c, float phase) {
    float d = length(p - c);
    float wave = sin(34.0*d - phase);
    return exp(-7.0*d) * pow(max(wave, 0.0), 5.0);
}

half4 main(float2 fragCoord) {
    float m = min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5 * uSize) / m;
    float t = uTime * (1.1 + 0.25*uEnergy);
    float v = 0.0;
    v += ripple(p, float2(0.22*sin(t*0.37), 0.18*cos(t*0.29)), t*2.0);
    v += ripple(p, float2(-0.26*cos(t*0.31+1.0), 0.17*sin(t*0.41+0.3)), t*1.7 + 2.0);
    v += ripple(p, float2(0.12*cos(t*0.53+2.4), -0.27*sin(t*0.35+1.1)), t*2.3 + 4.0);
    v += ripple(p, float2(-0.08*sin(t*0.61), -0.08*cos(t*0.47)), t*1.4 + 1.0);
    float shimmer = 0.5 + 0.5*sin(10.0*p.x + 8.0*p.y - t*0.55);
    float3 col = palette(0.22 + 0.12*t + shimmer*0.18 + uTreble*0.10);
    col *= 0.10 + v*(1.35 + 0.7*uBass);
    col += 0.06 * exp(-3.2*length(p)) * palette(0.65 + 0.02*t);
    return half4(half3(col), 1.0);
}
""").trimIndent()

private val NEBULA_SHADER = (COMMON + """
float field(float2 p, float t) {
    float s = 0.0;
    s += sin(p.x*7.0 + t + sin(p.y*4.0-t*0.7));
    s += cos(p.y*8.5 - t*0.8 + cos(p.x*5.0+t*0.4));
    s += sin((p.x+p.y)*12.0 + t*0.35) * 0.5;
    return s;
}

half4 main(float2 fragCoord) {
    float m = min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5*uSize) / m;
    float t = uTime*(0.17 + 0.05*uEnergy);
    float n = field(p, t);
    float wisps = exp(-2.2*abs(n)) + 0.45*exp(-5.0*abs(field(p*1.7+0.1, -t*0.8)));
    float3 col = palette(0.58 + 0.12*n + 0.025*uTime + 0.10*uTreble);
    col *= wisps*(0.35 + 0.75*uMids);
    col += 0.10*exp(-3.0*length(p))*float3(0.15,0.35,0.8);
    return half4(half3(col),1.0);
}
""").trimIndent()

private val RADIAL_SHADER = (COMMON + """
half4 main(float2 fragCoord) {
    float m = min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5*uSize) / m;
    float r = length(p);
    float a = atan(p.y,p.x);
    float t = uTime*(0.22 + 0.05*uEnergy);
    float spokes = 0.5 + 0.5*cos(a*28.0 + 2.4*sin(r*9.0-t));
    float rings = 0.5 + 0.5*cos(r*54.0 - t*2.0 - 2.0*uBass);
    float v = pow(spokes,4.0)*(0.35 + 0.75*rings);
    float3 col = palette(a/6.2831853 + 0.025*uTime + 0.12*uTreble);
    col *= v*(0.45 + 0.9*uEnergy) + 0.05*exp(-4.0*r);
    return half4(half3(col),1.0);
}
""").trimIndent()
