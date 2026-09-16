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

    @Test
    fun kaleidoscopeRendersNonBlankPixels() {
        composeRule.setContent {
            Box(Modifier.size(320.dp).background(Color.White)) {
                VisualizerScene(
                    style = "Kaleidoscope",
                    frame = VisualizationFrame(),
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
        val stepX = maxOf(1, pixels.width / 24)
        val stepY = maxOf(1, pixels.height / 24)
        var sampled = 0
        for (y in 0 until pixels.height step stepY) {
            for (x in 0 until pixels.width step stepX) {
                val c = pixels[x, y]
                sampled++
                if (c.red < .20f && c.green < .20f && c.blue < .20f) dark++
                if (kotlin.math.abs(c.red - c.green) > .08f || kotlin.math.abs(c.green - c.blue) > .08f) colored++
            }
        }
        assertTrue("Visualizer remained blank/light", dark > sampled / 5)
        assertTrue("Visualizer did not render colored geometry", colored > sampled / 30)
    }
}
