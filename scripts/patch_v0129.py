from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# v0.1.28 routed the visualizer through an Android RuntimeShader RenderEffect.
# On some devices that path can leave the visualizer area blank even though the
# Visualizer chip is selected. Keep the shader engine in the project for later
# testing, but use the proven Compose Canvas visualizer for the in-app renderer.
shader_route = '''    if (showColorscape && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ShaderVisualizerSurface(
            style = style,
            frame = frame,
            showWaveform = showWaveform,
            modifier = modifier
        )
        return
    }

'''
if shader_route not in text:
    raise SystemExit('v0.1.28 shader routing block not found')
text = text.replace(shader_route, '', 1)

# Rename the user-facing Colorscape control. Internal variable names stay the
# same so the accumulated patch chain remains stable.
text = text.replace('label = { Text("Colorscape") }', 'label = { Text("Visualizer") }')

p.write_text(text)
