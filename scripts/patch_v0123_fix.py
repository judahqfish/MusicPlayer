from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

imports = [
    'import androidx.compose.foundation.gestures.detectDragGestures',
    'import androidx.compose.ui.graphics.drawscope.clipPath',
    'import androidx.compose.ui.graphics.drawscope.withTransform',
]

anchor = 'import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n'
if anchor not in text:
    raise SystemExit('gesture import anchor not found')
if imports[0] not in text:
    text = text.replace(anchor, anchor + imports[0] + '\n', 1)

graphics_anchor = 'import androidx.compose.ui.graphics.Path\n'
if graphics_anchor not in text:
    raise SystemExit('graphics Path import anchor not found')
for imp in imports[1:]:
    if imp not in text:
        text = text.replace(graphics_anchor, graphics_anchor + imp + '\n', 1)

p.write_text(text)
