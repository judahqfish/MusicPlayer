from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Exact-line checks are important here: detectDragGesturesAfterLongPress contains
# the shorter function name as a substring, but does not import detectDragGestures.
lines = text.splitlines()
gesture_import = 'import androidx.compose.foundation.gestures.detectDragGestures'
gesture_anchor = 'import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n'
if gesture_anchor not in text:
    raise SystemExit('gesture import anchor not found')
if gesture_import not in lines:
    text = text.replace(gesture_anchor, gesture_anchor + gesture_import + '\n', 1)

for imp in [
    'import androidx.compose.ui.graphics.drawscope.clipPath',
    'import androidx.compose.ui.graphics.drawscope.withTransform',
]:
    if imp not in text.splitlines():
        graphics_anchor = 'import androidx.compose.ui.graphics.Path\n'
        if graphics_anchor not in text:
            raise SystemExit('graphics Path import anchor not found')
        text = text.replace(graphics_anchor, graphics_anchor + imp + '\n', 1)

p.write_text(text)
