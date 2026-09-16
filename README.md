# Offline Music Player

Current development version: **v0.1.20**.

Android music player focused on reliable local/offline playback, simple library management, playlists, and music-reactive visuals.

## What the app does

- Imports audio files and folders through Android's Storage Access Framework.
- Keeps imported music available for offline playback.
- Supports playlists, tags, favorites, search, queue management, shuffle, repeat, speed control, and per-track playback position.
- Uses Media3 / ExoPlayer with background playback and MediaSession controls.
- Provides a volume amplifier up to +12 dB, with distortion warning at higher boost levels.
- Includes an audio visualizer with waveform and Colorscape modes.

## Current feature set

### Library and organization

- All Tracks library.
- Multi-file and folder import.
- Multiple playlists.
- Manual playlist ordering with drag/reorder controls.
- Temporary alternate sorts without destroying the saved manual order.
- User-created tags and tag editing.
- Favorites.
- Multi-select batch actions for playlists, tags, and favorites.
- Search across track names.
- Filename-first naming with in-app display-name rename.

### Playback

- Previous / Play-Pause / Next.
- Back/forward 10 seconds.
- Queue screen and temporary queue reordering.
- Shuffle and repeat.
- Global speed presets from 0.5x to 2.0x.
- Volume amplifier from 0 to +12 dB.
- Background playback and lock-screen/Bluetooth media controls.
- Per-track saved playback position.
- Local/offline playback for imported tracks, including materialized local copies when required for cloud/document-provider sources.

### Visualizer

The visualizer uses real playback audio data rather than decorative animation alone. It tracks:

- waveform
- overall energy
- beat/onset strength
- bass
- mids
- treble

Available styles include Radial, Kaleidoscope, Blob, Drops, and Nebula.

As of **v0.1.20**:

- Colorscape reacts more strongly to the music.
- Strong bass/drum hits create visible pulse/expansion effects.
- Mids influence movement/shape and treble adds faster detail.
- Kaleidoscope uses shifting mirrored geometric facets rather than simply a radial spoke pattern.
- Waveform contrast adapts to the visual background; dark scenes use a light/white waveform so it remains visible.
- Visual animation uses a continuous frame clock so scenes do not visibly restart every few seconds.

## Recent changes

### v0.1.20

- Stronger beat-responsive Colorscape behavior.
- Reworked geometric Kaleidoscope.
- Adaptive high-contrast waveform rendering.

### v0.1.19

- Faster startup for already-imported local tracks.
- Continuous visualizer animation timing.

### v0.1.18

- Expanded visualizer scenes and frequency-linked color behavior.

## Known issues / work still in progress

- Continue tuning visualizer sensitivity across quiet, compressed, and bass-heavy recordings.
- Verify that every document-provider/Google Drive import receives a durable offline copy before its original provider becomes unavailable.
- Improve artist/album metadata display and album-aware sorting/grouping.
- Continue polishing queue and playlist interaction.
- Embedded artwork is not yet a complete library/Now Playing experience.

## Development rules / do not regress

- **Offline playback is a core requirement.** A track successfully added to the library should remain playable without network access.
- Manual playlist order must remain recoverable after viewing an alphabetical/date/duration sort.
- Sorting by track name should eventually account for album grouping so multiple albums with tracks such as `01`, `02`, etc. do not become intermixed.
- Favorites remain a first-class library view.
- Playback controls must always remain available: previous, seek back, play/pause, seek forward, and next.
- Visual effects should respond to the actual music. Do not replace audio reactivity with generic looping animation.
- A Kaleidoscope style means evolving mirrored/geometric patterns, not merely a radial pattern.
- Waveform rendering must maintain adequate contrast against the current background.
- Preserve the selected launcher icon and keep the app version visible where established in the UI.

## Build

This repository currently targets:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- Compile/target SDK 37
- Compose BOM 2026.08.00
- Media3 1.11.0
- Room 2.8.5

From a machine with Android SDK 37 installed:

```bash
gradle :app:assembleDebug
```

Or push to GitHub and run the included **Android Debug APK** workflow.

The workflow applies the cumulative UI/behavior patches, builds the debug APK, and uploads a versioned APK artifact. Artifact retention is intentionally **1 day** to reduce GitHub Actions storage use.

## Next priorities

1. Finish durable offline copies for tracks imported from Google Drive/document providers.
2. Add/finish artist and album metadata display and album-aware sorting.
3. Continue tuning Colorscape/Kaleidoscope responsiveness using real music.
4. Improve visual polish without sacrificing playback reliability.
5. Keep every release versioned and compiler-verified before presenting the APK.