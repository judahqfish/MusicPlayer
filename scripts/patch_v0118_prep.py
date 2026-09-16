from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()
if 'import androidx.compose.animation.core.*' not in text:
    text = text.replace(
        'import androidx.compose.foundation.background\n',
        'import androidx.compose.foundation.background\nimport androidx.compose.animation.core.*\n'
    )
p.write_text(text)
