package com.everywhen.offlinemusic

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class VisualizationFrame(
    val waveform: List<Float> = List(48) { 0f },
    val energy: Float = 0f,
    val beat: Float = 0f,
    val bass: Float = 0f,
    val mids: Float = 0f,
    val treble: Float = 0f,
    val sequence: Long = 0L
)

object AudioVisualizationBus {
    private val _frame = MutableStateFlow(VisualizationFrame())
    val frame: StateFlow<VisualizationFrame> = _frame

    internal fun publish(
        waveform: List<Float>,
        energy: Float,
        beat: Float,
        bass: Float,
        mids: Float,
        treble: Float
    ) {
        val previous = _frame.value
        _frame.value = VisualizationFrame(
            waveform = waveform,
            energy = energy.coerceIn(0f, 1f),
            beat = beat.coerceIn(0f, 1f),
            bass = bass.coerceIn(0f, 1f),
            mids = mids.coerceIn(0f, 1f),
            treble = treble.coerceIn(0f, 1f),
            sequence = previous.sequence + 1
        )
    }

    internal fun clear() {
        val previous = _frame.value
        _frame.value = VisualizationFrame(sequence = previous.sequence + 1)
    }
}

class VisualizationAudioSink : TeeAudioProcessor.AudioBufferSink {
    private var encoding: Int = C.ENCODING_PCM_16BIT
    private var channelCount: Int = 2
    private var sampleRateHz: Int = 44100

    private var smoothedEnergy = 0f
    private var recentPeak = 0.08f
    private var lastBeatEnergy = 0f
    private var beatEnvelope = 0f
    private val smoothedWaveform = FloatArray(48)

    private var lowState = 0f
    private var upperMidState = 0f
    private var bassSmooth = 0f
    private var midsSmooth = 0f
    private var trebleSmooth = 0f
    private var bassPeak = 0.04f
    private var midsPeak = 0.04f
    private var treblePeak = 0.04f

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz.coerceAtLeast(8000)
        this.encoding = encoding
        this.channelCount = max(1, channelCount)
        smoothedEnergy = 0f
        recentPeak = 0.08f
        lastBeatEnergy = 0f
        beatEnvelope = 0f
        smoothedWaveform.fill(0f)
        lowState = 0f
        upperMidState = 0f
        bassSmooth = 0f
        midsSmooth = 0f
        trebleSmooth = 0f
        bassPeak = 0.04f
        midsPeak = 0.04f
        treblePeak = 0.04f
        AudioVisualizationBus.clear()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val copy = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        if (!copy.hasRemaining()) return

        val samples = when (encoding) {
            C.ENCODING_PCM_FLOAT -> readFloatSamples(copy)
            C.ENCODING_PCM_16BIT -> read16BitSamples(copy)
            C.ENCODING_PCM_24BIT -> read24BitSamples(copy)
            C.ENCODING_PCM_32BIT -> read32BitSamples(copy)
            else -> return
        }
        if (samples.isEmpty()) return

        val bins = 48
        val rawWaveform = FloatArray(bins)
        val perBin = max(1, samples.size / bins)
        var sumSquares = 0.0
        var bassSquares = 0.0
        var midSquares = 0.0
        var trebleSquares = 0.0

        // Two inexpensive one-pole low-pass filters split the signal into useful
        // perceptual bands: <~220 Hz bass, ~220 Hz–4 kHz mids, >~4 kHz treble.
        val lowAlpha = (1.0 - exp(-2.0 * PI * 220.0 / sampleRateHz)).toFloat()
        val upperAlpha = (1.0 - exp(-2.0 * PI * 4000.0 / sampleRateHz)).toFloat()

        samples.forEachIndexed { index, value ->
            val v = value.coerceIn(-1f, 1f)
            sumSquares += (v * v).toDouble()

            lowState += lowAlpha * (v - lowState)
            upperMidState += upperAlpha * (v - upperMidState)
            val low = lowState
            val mid = upperMidState - lowState
            val high = v - upperMidState
            bassSquares += (low * low).toDouble()
            midSquares += (mid * mid).toDouble()
            trebleSquares += (high * high).toDouble()

            val bin = (index / perBin).coerceAtMost(bins - 1)
            if (abs(v) > abs(rawWaveform[bin])) rawWaveform[bin] = v
        }

        for (i in 0 until bins) {
            smoothedWaveform[i] = smoothedWaveform[i] * 0.62f + rawWaveform[i] * 0.38f
        }

        val rms = sqrt(sumSquares / samples.size).toFloat().coerceIn(0f, 1f)
        smoothedEnergy = smoothedEnergy * 0.82f + rms * 0.18f
        recentPeak = max(smoothedEnergy, recentPeak * 0.982f).coerceAtLeast(0.035f)
        val normalizedEnergy = (smoothedEnergy / (recentPeak * 1.12f)).coerceIn(0f, 1f)

        val bassRaw = sqrt(bassSquares / samples.size).toFloat()
        val midsRaw = sqrt(midSquares / samples.size).toFloat()
        val trebleRaw = sqrt(trebleSquares / samples.size).toFloat()
        bassSmooth = bassSmooth * 0.78f + bassRaw * 0.22f
        midsSmooth = midsSmooth * 0.80f + midsRaw * 0.20f
        trebleSmooth = trebleSmooth * 0.82f + trebleRaw * 0.18f
        bassPeak = max(bassSmooth, bassPeak * 0.985f).coerceAtLeast(0.015f)
        midsPeak = max(midsSmooth, midsPeak * 0.985f).coerceAtLeast(0.015f)
        treblePeak = max(trebleSmooth, treblePeak * 0.985f).coerceAtLeast(0.012f)
        val bass = (bassSmooth / (bassPeak * 1.08f)).coerceIn(0f, 1f)
        val mids = (midsSmooth / (midsPeak * 1.08f)).coerceIn(0f, 1f)
        val treble = (trebleSmooth / (treblePeak * 1.08f)).coerceIn(0f, 1f)

        val onset = (normalizedEnergy - lastBeatEnergy * 0.90f).coerceAtLeast(0f)
        val bassPunch = (bass - 0.58f).coerceAtLeast(0f) * 0.7f
        val detectedBeat = max(((onset - 0.045f) * 6.5f).coerceIn(0f, 1f), bassPunch.coerceIn(0f, 1f))
        beatEnvelope = max(detectedBeat, beatEnvelope * 0.82f)
        lastBeatEnergy = lastBeatEnergy * 0.45f + normalizedEnergy * 0.55f

        AudioVisualizationBus.publish(
            smoothedWaveform.toList(),
            normalizedEnergy,
            beatEnvelope,
            bass,
            mids,
            treble
        )
    }

    private fun read16BitSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 2
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 2 && i < count) out[i++] = buffer.short / 32768f
        return if (i == count) out else out.copyOf(i)
    }

    private fun readFloatSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 4
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 4 && i < count) out[i++] = buffer.float.coerceIn(-1f, 1f)
        return if (i == count) out else out.copyOf(i)
    }

    private fun read24BitSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 3
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 3 && i < count) {
            val b0 = buffer.get().toInt() and 0xff
            val b1 = buffer.get().toInt() and 0xff
            val b2 = buffer.get().toInt()
            val value = b0 or (b1 shl 8) or (b2 shl 16)
            out[i++] = value / 8388608f
        }
        return if (i == count) out else out.copyOf(i)
    }

    private fun read32BitSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 4
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 4 && i < count) out[i++] = buffer.int / 2147483648f
        return if (i == count) out else out.copyOf(i)
    }
}
