package com.everywhen.offlinemusic

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class VisualizationFrame(
    val waveform: List<Float> = List(48) { 0f },
    val energy: Float = 0f,
    val beat: Float = 0f,
    val sequence: Long = 0L
)

object AudioVisualizationBus {
    private val _frame = MutableStateFlow(VisualizationFrame())
    val frame: StateFlow<VisualizationFrame> = _frame

    internal fun publish(waveform: List<Float>, energy: Float, beat: Float) {
        val previous = _frame.value
        _frame.value = VisualizationFrame(
            waveform = waveform,
            energy = energy.coerceIn(0f, 1f),
            beat = beat.coerceIn(0f, 1f),
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
    private var smoothedEnergy = 0f
    private var recentPeak = 0.08f
    private var lastBeatEnergy = 0f
    private var beatEnvelope = 0f
    private val smoothedWaveform = FloatArray(48)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        this.channelCount = max(1, channelCount)
        smoothedEnergy = 0f
        recentPeak = 0.08f
        lastBeatEnergy = 0f
        beatEnvelope = 0f
        smoothedWaveform.fill(0f)
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

        samples.forEachIndexed { index, value ->
            val v = value.coerceIn(-1f, 1f)
            sumSquares += (v * v).toDouble()
            val bin = (index / perBin).coerceAtMost(bins - 1)
            if (abs(v) > abs(rawWaveform[bin])) rawWaveform[bin] = v
        }

        // Low-pass the waveform so adjacent PCM buffers blend rather than jump.
        for (i in 0 until bins) {
            smoothedWaveform[i] = smoothedWaveform[i] * 0.62f + rawWaveform[i] * 0.38f
        }

        val rms = sqrt(sumSquares / samples.size).toFloat().coerceIn(0f, 1f)
        smoothedEnergy = smoothedEnergy * 0.82f + rms * 0.18f
        recentPeak = max(smoothedEnergy, recentPeak * 0.982f).coerceAtLeast(0.035f)

        val normalizedEnergy = (smoothedEnergy / (recentPeak * 1.12f)).coerceIn(0f, 1f)
        val onset = (normalizedEnergy - lastBeatEnergy * 0.90f).coerceAtLeast(0f)
        val detectedBeat = ((onset - 0.045f) * 6.5f).coerceIn(0f, 1f)

        // Hold a musical transient for a few frames, then let it fall away smoothly.
        beatEnvelope = max(detectedBeat, beatEnvelope * 0.82f)
        lastBeatEnergy = lastBeatEnergy * 0.45f + normalizedEnergy * 0.55f

        AudioVisualizationBus.publish(smoothedWaveform.toList(), normalizedEnergy, beatEnvelope)
    }

    private fun read16BitSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 2
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 2 && i < count) {
            out[i++] = buffer.short / 32768f
        }
        return if (i == count) out else out.copyOf(i)
    }

    private fun readFloatSamples(buffer: ByteBuffer): FloatArray {
        val count = buffer.remaining() / 4
        if (count <= 0) return FloatArray(0)
        val out = FloatArray(count)
        var i = 0
        while (buffer.remaining() >= 4 && i < count) {
            out[i++] = buffer.float.coerceIn(-1f, 1f)
        }
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
        while (buffer.remaining() >= 4 && i < count) {
            out[i++] = buffer.int / 2147483648f
        }
        return if (i == count) out else out.copyOf(i)
    }
}
