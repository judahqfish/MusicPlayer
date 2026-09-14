# Offline Music Player v0.1.0

Android-only, local-file music player. Designed to operate with **no Internet permission**.

## Included in this first coding build

- All Tracks library
- Multi-file import through Android's document picker
- Folder import through Storage Access Framework
- Multiple playlists
- User-created tags and tag editing
- Favorites
- Filename-first track naming and in-app display-name rename
- Search across track names
- Media3 / ExoPlayer playback
- Background playback through MediaSessionService
- Screen-off / lock-screen / Bluetooth media controls through MediaSession
- Previous / Play-Pause / Next / Seek
- Global speed presets 0.5x–2.0x
- Shuffle and repeat controls
- Per-track saved playback position
- Playlist-specific membership/order data model
- Local Room database
- 1-day GitHub Actions APK artifact retention

## Explicitly offline

`AndroidManifest.xml` intentionally does **not** declare `android.permission.INTERNET`.

No analytics, cloud sync, streaming, ads, telemetry, online artwork, or remote metadata lookup are included.

## Build

This repository targets:

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

Or push to GitHub and run the included **Android Debug APK** workflow. The APK artifact is retained for one day.

## v0.1.0 limitations / next coding pass

The architecture is in place, but these UX pieces should be completed before calling it 1.0:

- Drag reordering UI for playlist tracks and queue (database schema already supports ordered playlists)
- Full queue screen and temporary queue reordering
- Add-existing-library-tracks picker inside a playlist
- Playlist rename/delete/clear confirmation UI
- Tag rename/delete and "Create playlist from tag"
- Multi-select batch actions
- Locate File/relink UI
- Embedded artwork display (playback supports local media; UI currently uses placeholder icon)
- Search playlist/tag names in addition to tracks
- Persist/restore last app screen and each playlist's last track/shuffle state in UI
- More robust natural-end vs manual-skip position reset logic
- Automatic resume specifically after transient phone/audio-focus interruption and reconnect policy tests

These are intentionally listed rather than silently faked in the UI.
