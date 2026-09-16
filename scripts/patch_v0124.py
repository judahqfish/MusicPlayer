from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

old_state = '''    var musicPhase by remember { mutableFloatStateOf(0f) }
    var beatLatch by remember { mutableFloatStateOf(0f) }
    var kaleidoTouch by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(frame.sequence) {
        val drive = (frame.energy * .012f + frame.mids * .026f + frame.treble * .010f).coerceAtLeast(0f)
        musicPhase = (musicPhase + drive) % (2f * Math.PI.toFloat())
        beatLatch = maxOf(frame.beat, beatLatch * .72f)
    }
'''

new_state = '''    var musicPhase by remember { mutableFloatStateOf(0f) }
    var beatLatch by remember { mutableFloatStateOf(0f) }
    var calmEnergy by remember { mutableFloatStateOf(0f) }
    var calmBass by remember { mutableFloatStateOf(0f) }
    var calmMids by remember { mutableFloatStateOf(0f) }
    var calmTreble by remember { mutableFloatStateOf(0f) }
    var kaleidoTouch by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(frame.sequence) {
        // Long, gentle envelopes: the picture should breathe with the music rather than pump.
        calmEnergy += (frame.energy - calmEnergy) * .035f
        calmBass += (frame.bass - calmBass) * .030f
        calmMids += (frame.mids - calmMids) * .035f
        calmTreble += (frame.treble - calmTreble) * .040f
        beatLatch += (frame.beat - beatLatch) * .045f
        val drive = .0045f + calmEnergy * .004f + calmMids * .008f + calmTreble * .003f
        musicPhase = (musicPhase + drive) % (2f * Math.PI.toFloat())
    }
'''

if old_state not in text:
    raise SystemExit('v0.1.23 state block not found')
text = text.replace(old_state, new_state, 1)

replacements = {
    'val musicalHit = maxOf(hit, beatLatch * beatLatch).coerceIn(0f, 1f)':
        'val musicalHit = (beatLatch * .10f + hit * .012f).coerceIn(0f, .12f)',
    'val radius = minD * (.54f + musicalHit * .04f)':
        'val radius = minD * (.54f + musicalHit * .010f)',
    'warm.copy(alpha = .12f + bass * .18f + musicalHit * .12f),':
        'warm.copy(alpha = .12f + calmBass * .10f + musicalHit * .04f),',
    'middle.copy(alpha = .10f + mids * .17f),':
        'middle.copy(alpha = .10f + calmMids * .10f),',
    'cool.copy(alpha = .08f + treble * .16f),':
        'cool.copy(alpha = .08f + calmTreble * .10f),',
    'val count = (10 + mids * 8f + treble * 5f).toInt().coerceIn(10, 23)':
        'val count = (11 + calmMids * 5f + calmTreble * 3f).toInt().coerceIn(11, 19)',
    'val band = when (item % 3) { 0 -> bass; 1 -> mids; else -> treble }':
        'val band = when (item % 3) { 0 -> calmBass; 1 -> calmMids; else -> calmTreble }',
    'val slowDrift = timeSeconds.toFloat() * (.015f + energy * .055f)':
        'val slowDrift = timeSeconds.toFloat() * (.012f + calmEnergy * .010f)',
    'val rr = (baseR * (.76f + bass * .20f + band * .16f + kotlin.math.abs(w1) * .18f + musicalHit * .13f)).coerceAtMost(radius * .93f)':
        'val rr = (baseR * (.82f + calmBass * .075f + band * .070f + kotlin.math.abs(w1) * .035f + musicalHit * .025f)).coerceAtMost(radius * .93f)',
    'val localAngle = (kotlin.math.sin(localPhase) * (.18f + mids * .12f) + w1 * .17f + touchY * .12f)':
        'val localAngle = (kotlin.math.sin(localPhase) * (.16f + calmMids * .055f) + w1 * .030f + touchY * .12f)',
    'val sz = minD * (.012f + band * .030f + kotlin.math.abs(w2) * .022f + musicalHit * .024f)':
        'val sz = minD * (.014f + band * .014f + kotlin.math.abs(w2) * .006f + musicalHit * .006f)',
    'drawPath(tri, cc.copy(alpha=(.18f + energy*.18f + band*.28f + musicalHit*.22f).coerceAtMost(.86f)))':
        'drawPath(tri, cc.copy(alpha=(.20f + calmEnergy*.10f + band*.16f + musicalHit*.05f).coerceAtMost(.72f)))',
    'drawPath(tri, Color.White.copy(alpha=.04f + treble*.13f + musicalHit*.12f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=.8f+treble*1.6f+musicalHit*2.4f))':
        'drawPath(tri, Color.White.copy(alpha=.04f + calmTreble*.08f + musicalHit*.03f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=.8f+calmTreble*.8f+musicalHit*.8f))',
    'val ringR = minD * (.10f + bass*.095f + musicalHit*.070f)':
        'val ringR = minD * (.105f + calmBass*.030f + musicalHit*.012f)',
    'middle.copy(alpha=.14f + energy*.16f + musicalHit*.20f),':
        'middle.copy(alpha=.14f + calmEnergy*.08f + musicalHit*.04f),',
    'style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.2f + bass*3f + musicalHit*4f)':
        'style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.2f + calmBass*1.1f + musicalHit*.8f)',
    'val core = minD * (.035f + bass*.030f + musicalHit*.040f)':
        'val core = minD * (.036f + calmBass*.010f + musicalHit*.006f)',
    'drawLine(Color.White.copy(alpha=.08f + musicalHit*.48f), Offset(cx,cy), p, 1f + musicalHit*3.8f)':
        'drawLine(Color.White.copy(alpha=.08f + musicalHit*.10f), Offset(cx,cy), p, 1f + musicalHit*.8f)'
}

for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'v0.1.23 smoothing target not found: {old}')
    text = text.replace(old, new, 1)

p.write_text(text)
