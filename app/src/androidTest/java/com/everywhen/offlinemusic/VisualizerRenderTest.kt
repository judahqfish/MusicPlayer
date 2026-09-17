package com.everywhen.offlinemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisualizerRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun kaleidoscopeRenders() = assertStyle("Kaleidoscope")
    @Test fun blobRenders() = assertStyle("Blob")
    @Test fun dropsRender() = assertStyle("Drops")
    @Test fun nebulaRenders() = assertStyle("Nebula")
    @Test fun radialRenders() = assertStyle("Radial")

    private fun assertStyle(style: String) {
        composeRule.setContent {
            Box(Modifier.size(320.dp).background(Color.White)) {
                VisualizerScene(
                    style = style,
                    frame = VisualizationFrame(
                        energy = .45f,
                        beat = .25f,
                        bass = .55f,
                        mids = .42f,
                        treble = .38f
                    ),
                    showWaveform = false,
                    showColorscape = true,
                    modifier = Modifier.size(320.dp)
                )
            }
        }
        composeRule.waitForIdle()
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var dark = 0
        var colored = 0
        var sampled = 0
        val stepX = maxOf(1, pixels.width / 24)
        val stepY = maxOf(1, pixels.height / 24)
        for (y in 0 until pixels.height step stepY) {
            for (x in 0 until pixels.width step stepX) {
                val c = pixels[x, y]
                sampled++
                if (c.red < .20f && c.green < .20f && c.blue < .20f) dark++
                if (kotlin.math.abs(c.red - c.green) > .06f || kotlin.math.abs(c.green - c.blue) > .06f) colored++
            }
        }
        assertTrue("$style remained blank/light", dark > sampled / 8)
        assertTrue("$style did not render colored pixels", colored > sampled / 40)
    }
}
