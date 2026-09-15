from pathlib import Path

p = Path('app/src/main/java/com/everywhen/offlinemusic/Ui.kt')
text = p.read_text()

# Mini-player: show elapsed / total time in the persistent controls.
text = text.replace(
'''    var title by remember { mutableStateOf("") }
    var hasTrack by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }''',
'''    var title by remember { mutableStateOf("") }
    var hasTrack by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var expanded by remember { mutableStateOf(false) }''',
1)
text = text.replace(
'''            title = controller?.mediaMetadata?.title?.toString().orEmpty()
            hasTrack = controller?.currentMediaItem != null
            delay(300)''',
'''            title = controller?.mediaMetadata?.title?.toString().orEmpty()
            hasTrack = controller?.currentMediaItem != null
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = controller?.duration?.takeIf { it > 0L } ?: 0L
            delay(300)''',
1)
text = text.replace(
'''                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        if (expanded) "Collapse playback controls" else "Show speed and amplifier"
                    )
                }''',
'''                if (hasTrack) {
                    Text(
                        "${formatDuration(position)} / ${formatDuration(duration)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        if (expanded) "Collapse playback controls" else "Show speed and amplifier"
                    )
                }''',
1)

# Selection bar supports removing selected tracks from the app/library.
old = '''fun SelectionBar(count: Int, onCancel: () -> Unit, onPlaylist: () -> Unit, onTags: () -> Unit, onFavorite: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel selection") }
            Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, "Add to playlist") }
            IconButton(onClick = onTags) { Icon(Icons.Default.Label, "Add tags") }
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, "Favorite selected") }
        }
    }
}'''
new = '''fun SelectionBar(count: Int, onCancel: () -> Unit, onPlaylist: () -> Unit, onTags: () -> Unit, onFavorite: () -> Unit, onRemove: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel selection") }
            Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, "Add to playlist") }
            IconButton(onClick = onTags) { Icon(Icons.Default.Label, "Add tags") }
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, "Favorite selected") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, "Remove selected from MusicPlayer") }
        }
    }
}'''
if old not in text:
    raise SystemExit('SelectionBar block not found')
text = text.replace(old, new, 1)

# Add remove callback to both selection bars.
text = text.replace(
'''                onTags = { tagPicker = true },
                onFavorite = { vm.setFavoriteForTracks(selectedIds, true); selectedIds = emptySet() }
            )''',
'''                onTags = { tagPicker = true },
                onFavorite = { vm.setFavoriteForTracks(selectedIds, true); selectedIds = emptySet() },
                onRemove = { vm.removeTracksFromLibrary(selectedIds); selectedIds = emptySet() }
            )''')

# Single-track remove actions: playlist row and normal library row. The file itself is retained.
text = text.replace(
'''                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, playlistId) })''',
'''                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, playlistId) })
                    DropdownMenuItem({ Text("Remove from MusicPlayer (keep file)") }, { menu = false; vm.removeTracksFromLibrary(setOf(track.id)) })''',
1)
text = text.replace(
'''                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    playlistId?.let { pid -> DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, pid) }) }''',
'''                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    playlistId?.let { pid -> DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, pid) }) }
                    DropdownMenuItem({ Text("Remove from MusicPlayer (keep file)") }, { menu = false; vm.removeTracksFromLibrary(setOf(track.id)) })''',
1)

# Batch tag dialog: allow comma/semicolon/newline-separated tags in one entry.
text = text.replace(
'''fun BatchTagDialog(vm: MainViewModel, selectedIds: Set<Long>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var createNew by remember { mutableStateOf(false) }''',
'''fun BatchTagDialog(vm: MainViewModel, selectedIds: Set<Long>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var createNew by remember { mutableStateOf(false) }
    var typeMany by remember { mutableStateOf(false) }''',
1)
text = text.replace(
'''                TextButton(onClick = { createNew = true }) { Icon(Icons.Default.Add, null); Text(" New tag") }''',
'''                TextButton(onClick = { typeMany = true }) { Icon(Icons.Default.Edit, null); Text(" Type multiple tags") }
                TextButton(onClick = { createNew = true }) { Icon(Icons.Default.Add, null); Text(" New tag") }''',
1)
text = text.replace(
'''    if (createNew) NameDialog("New Tag", "Tag name", onDismiss = { createNew = false }) { name ->
        vm.createTag(name) { tagId -> vm.addTagsToTracks(selectedIds, setOf(tagId)) }
        createNew = false
        onDone()
    }
}''',
'''    if (createNew) NameDialog("New Tag", "Tag name", onDismiss = { createNew = false }) { name ->
        vm.createTag(name) { tagId -> vm.addTagsToTracks(selectedIds, setOf(tagId)) }
        createNew = false
        onDone()
    }
    if (typeMany) MultiTagEntryDialog(
        onDismiss = { typeMany = false },
        onApply = { raw -> vm.createTagsFromText(raw, selectedIds); typeMany = false; onDone() }
    )
}''',
1)

# Single-track tag editor also gets multi-tag typing.
text = text.replace(
'''fun TagEditor(track: TrackEntity, vm: MainViewModel, onDismiss: () -> Unit) {
    val allTags by vm.tags.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }''',
'''fun TagEditor(track: TrackEntity, vm: MainViewModel, onDismiss: () -> Unit) {
    val allTags by vm.tags.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var typeMany by remember { mutableStateOf(false) }''',
1)
text = text.replace(
'''                allTags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                    }) {
                        Checkbox(tag.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + tag.id else selected - tag.id })
                        Text(tag.name)
                    }
                }
            }
        },''',
'''                allTags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                    }) {
                        Checkbox(tag.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + tag.id else selected - tag.id })
                        Text(tag.name)
                    }
                }
                TextButton(onClick = { typeMany = true }) { Icon(Icons.Default.Edit, null); Text(" Type multiple tags") }
            }
        },''',
1)
text = text.replace(
'''        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MiniPlayer''',
'''        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (typeMany) MultiTagEntryDialog(
        onDismiss = { typeMany = false },
        onApply = { raw -> vm.createTagsFromText(raw, setOf(track.id)); typeMany = false }
    )
}

@Composable
fun MultiTagEntryDialog(onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type multiple tags") },
        text = {
            Column {
                Text("Separate tags with commas, semicolons, or new lines.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("car, favorites, upbeat") }
                )
            }
        },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onApply(value) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MiniPlayer''',
1)

p.write_text(text)
