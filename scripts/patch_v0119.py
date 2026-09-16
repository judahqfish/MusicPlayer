from pathlib import Path

# --- Fast local playback ----------------------------------------------------
repo_path = Path('app/src/main/java/com/everywhen/offlinemusic/MusicRepository.kt')
repo = repo_path.read_text()

repo = repo.replace(
'''        input.forEach { supplied ->
            val latest = dao.track(supplied.id) ?: supplied
            val result = runCatching {''',
'''        input.forEach { supplied ->
            // Already-imported local tracks are the common case. Avoid a database
            // lookup and metadata probe on every tap; those were adding noticeable
            // startup latency for large queues.
            val suppliedUri = Uri.parse(supplied.uri)
            val latest = if (
                suppliedUri.scheme == "file" &&
                supplied.metadataScanned &&
                supplied.durationMs > 0L
            ) supplied else (dao.track(supplied.id) ?: supplied)
            val result = runCatching {''',
1
)

repo = repo.replace(
'''                    current.scheme == "file" -> repairTrack(latest)
                    else -> latest''',
'''                    current.scheme == "file" -> {
                        val file = File(current.path ?: error("Missing local file path"))
                        require(file.exists() && file.length() > 0L) { "Offline copy is missing" }
                        val extensionReady = !isManagedCopy(current) || file.extension.lowercase() in audioExtensions
                        if (latest.metadataScanned && latest.durationMs > 0L && extensionReady) {
                            latest.copy(unavailable = false)
                        } else {
                            repairTrack(latest)
                        }
                    }
                    else -> latest''',
1
)

repo_path.write_text(repo)

# --- Continuous visualizer time --------------------------------------------
ui_path = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = ui_path.read_text()

old_clock = '''    val transition = rememberInfiniteTransition(label = "colorscapeMotion")
    val phase by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "colorscapePhase"
    )
    val breathe by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "colorscapeBreath"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension
        val rad = phase * (Math.PI / 180.0)

        // Frequency-linked palette: lows are warm, mids lean magenta/green,
        // highs lean cyan/blue. Time only drifts each family gently.
        val bassHue = (12f + bass * 36f + phase * 0.035f) % 360f
        val midHue = (285f + mids * 62f + phase * 0.045f) % 360f
        val highHue = (185f + treble * 48f + phase * 0.060f) % 360f'''

new_clock = '''    // Use the display frame clock directly instead of a short repeating animation.
    // This removes the visible ~9-10 second "start over" point.
    var frameClockNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameClockNanos = it }
        }
    }
    val timeSeconds = frameClockNanos / 1_000_000_000.0
    val breathe = ((kotlin.math.sin(timeSeconds * (2.0 * Math.PI / 5.6)) + 1.0) * 0.5).toFloat()

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minD = size.minDimension
        val rad = timeSeconds * 0.42

        // Frequency-linked palette: lows are warm, mids lean magenta/green,
        // highs lean cyan/blue. Hue drift is continuous and never restarts as a scene.
        val bassHue = ((12.0 + bass * 36.0 + timeSeconds * 1.35) % 360.0).toFloat()
        val midHue = ((285.0 + mids * 62.0 + timeSeconds * 1.72) % 360.0).toFloat()
        val highHue = ((185.0 + treble * 48.0 + timeSeconds * 2.18) % 360.0).toFloat()'''

if old_clock not in text:
    raise SystemExit('v0.1.18 visualizer clock block not found')
text = text.replace(old_clock, new_clock, 1)

text = text.replace(
'''                        val progress = ((phase / 360f * (0.7f + (i%5)*.08f) + seed) % 1f)
                        val angle = seed * 6.28318f + rad.toFloat() * (if(i%2==0) .18f else -.12f)''',
'''                        val progress = ((timeSeconds * (0.075 + (i % 5) * 0.011) + seed) % 1.0).toFloat()
                        val angle = seed * 6.28318f + (timeSeconds * (if (i % 2 == 0) 0.17 else -0.11)).toFloat()''',
1
)

text = text.replace(
'''                        val a = i/18f*6.28318f + rad.toFloat()*.12f''',
'''                        val a = i/18f*6.28318f + (timeSeconds * 0.055).toFloat()''',
1
)

text = text.replace(
'''                        val a = i.toFloat()/spokes*6.28318f + rad.toFloat()*(.08f + mids*.08f)''',
'''                        val a = i.toFloat()/spokes*6.28318f + (timeSeconds * (0.055 + mids * 0.045)).toFloat()''',
1
)

text = text.replace(
'''                        val base = (slice*(360f/slices) + phase*(.18f + mids*.10f)*(if(slice%2==0)1f else -1f)) * (Math.PI/180.0)''',
'''                        val base = slice * (2.0 * Math.PI / slices) + timeSeconds * (0.10 + mids * 0.07) * (if (slice % 2 == 0) 1.0 else -1.0)''',
1
)

ui_path.write_text(text)
