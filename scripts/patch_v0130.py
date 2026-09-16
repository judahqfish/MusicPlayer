from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Route the visualizer through the v0.1.30 renderer whose core shader mappings
# are adapted from the working MIT-licensed Android-AGSL-Shader-Playground.
marker = '    Canvas(modifier = interactiveModifier) {'
start = text.index('@Composable\nfun AudioVisualizerCanvas(')
pos = text.find(marker, start)
if pos == -1:
    raise SystemExit('AudioVisualizerCanvas interactive Canvas marker not found')

replacement = '''    if (showColorscape && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ProvenVisualizerSurface(
            style = style,
            frame = frame,
            showWaveform = showWaveform,
            modifier = modifier
        )
        return
    }

    Canvas(modifier = interactiveModifier) {'''

text = text[:pos] + text[pos:].replace(marker, replacement, 1)
p.write_text(text)
