from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()
start = text.index('@Composable\ninternal fun VisualizerScene(')
end = text.index('\n@Composable\nfun NowPlayingScreen', start)
new_block = r'''@Composable
internal fun VisualizerScene(
    style: String,
    frame: VisualizationFrame,
    showWaveform: Boolean,
    showColorscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showColorscape) {
        Canvas(modifier) { drawRect(Color.White) }
        return
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ProductionVisualizerSurface(
            style = style,
            frame = frame,
            showWaveform = showWaveform,
            modifier = modifier
        )
    } else {
        // Static fallback for pre-Android 13 devices. Current target devices use AGSL.
        Canvas(modifier) {
            drawRect(Color.Black)
            drawCircle(
                color = Color(0xFF7A4DFF),
                radius = size.minDimension * .22f,
                center = center
            )
        }
    }
}
'''
text = text[:start] + new_block + text[end:]
p.write_text(text)
