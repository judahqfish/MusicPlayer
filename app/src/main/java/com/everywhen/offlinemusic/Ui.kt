package com.everywhen.offlinemusic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

enum class RootScreen(val label: String) { TRACKS("Tracks"), PLAYLISTS("Playlists"), TAGS("Tags"), FAVORITES("Favorites") }

data class DetailState(val type: String, val id: Long, val name: String)

@Composable
fun MusicApp(vm: MainViewModel) {
    var screen by remember { mutableStateOf(RootScreen.TRACKS) }
    var detail by remember { mutableStateOf<DetailState?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val controller by vm.controller.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(vm, onOpen = { if (controller?.currentMediaItem != null) showNowPlaying = true })
                NavigationBar {
                    RootScreen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = detail == null && screen == item,
                            onClick = { detail = null; screen = item },
                            icon = { Icon(rootIcon(item), item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                showNowPlaying -> NowPlayingScreen(vm, onBack = { showNowPlaying = false })
                showSearch -> SearchScreen(vm, onBack = { showSearch = false })
                detail?.type == "playlist" -> PlaylistDetail(vm, detail!!, onBack = { detail = null }, onSearch = { showSearch = true })
                detail?.type == "tag" -> TagDetail(vm, detail!!, onBack = { detail = null })
                else -> when (screen) {
                    RootScreen.TRACKS -> AllTracksScreen(vm, onSearch = { showSearch = true })
                    RootScreen.PLAYLISTS -> PlaylistsScreen(vm, onOpen = { detail = DetailState("playlist", it.id, it.name) }, onSearch = { showSearch = true })
                    RootScreen.TAGS -> TagsScreen(vm, onOpen = { detail = DetailState("tag", it.id, it.name) })
                    RootScreen.FAVORITES -> FavoritesScreen(vm)
                }
            }
        }
    }
}

private fun rootIcon(s: RootScreen) = when (s) {
    RootScreen.TRACKS -> Icons.Default.LibraryMusic
    RootScreen.PLAYLISTS -> Icons.Default.QueueMusic
    RootScreen.TAGS -> Icons.Default.Label
    RootScreen.FAVORITES -> Icons.Default.Favorite
}

@Composable
fun Header(title: String, onSearch: (() -> Unit)? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
        actions = {
            if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
            actions()
        }
    )
}

@Composable
fun AllTracksScreen(vm: MainViewModel, onSearch: () -> Unit) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf("Filename") }
    var addMenu by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.importFiles(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::importFolder) }
    val sorted = remember(all, sort) {
        when (sort) {
            "Date added" -> all.sortedByDescending { it.dateAdded }
            "Duration" -> all.sortedBy { it.durationMs }
            else -> all.sortedBy { it.title().lowercase() }
        }
    }
    Column {
        Header("All Tracks", onSearch = onSearch)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort: $sort", modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                DropdownMenu(addMenu, { addMenu = false }) {
                    listOf("Filename", "Date added", "Duration").forEach { v -> DropdownMenuItem({ Text(v) }, { sort = v; addMenu = false }) }
                }
            }
            FilledTonalButton(onClick = { files.launch(arrayOf("audio/*")) }) { Text("Add files") }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { folder.launch(null) }) { Icon(Icons.Default.FolderOpen, "Add folder") }
        }
        if (sorted.isEmpty()) EmptyState("No music yet", "Add audio files or a folder from this device.")
        else TrackList(sorted, vm, onPlay = { vm.playTracks(sorted, it.id) })
    }
}

@Composable
fun PlaylistsScreen(vm: MainViewModel, onOpen: (PlaylistSummary) -> Unit, onSearch: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Column {
        Header("Playlists", onSearch = onSearch)
        if (playlists.isEmpty()) EmptyState("No playlists yet", "Create a playlist to organize your music.")
        LazyColumn(Modifier.weight(1f)) {
            items(playlists, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.count} tracks") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onOpen(p) }
                )
                HorizontalDivider()
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)) { Icon(Icons.Default.Add, null); Text(" New Playlist") }
    }
    if (creating) NameDialog("New Playlist", "Playlist name", onDismiss = { creating = false }) { vm.createPlaylist(it); creating = false }
}

@Composable
fun PlaylistDetail(vm: MainViewModel, detail: DetailState, onBack: () -> Unit, onSearch: () -> Unit) {
    val dbTracks by vm.repo.observePlaylistTracks(detail.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var manualTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var sort by remember { mutableStateOf("Manual") }
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.importFiles(it, detail.id) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { u -> vm.importFolder(u, detail.id) } }

    LaunchedEffect(dbTracks.map { it.id }) { manualTracks = dbTracks }

    val displayed = remember(manualTracks, sort) {
        when (sort) {
            "A–Z" -> manualTracks.sortedBy { it.title().lowercase() }
            "Date added" -> manualTracks.sortedByDescending { it.dateAdded }
            "Duration" -> manualTracks.sortedBy { it.durationMs }
            else -> manualTracks
        }
    }

    fun moveManual(from: Int, to: Int, save: Boolean = true) {
        if (from !in manualTracks.indices || to !in manualTracks.indices || from == to) return
        val changed = manualTracks.toMutableList()
        val item = changed.removeAt(from)
        changed.add(to, item)
        manualTracks = changed
        sort = "Manual"
        if (save) vm.savePlaylistOrder(detail.id, changed.map { it.id })
    }

    Column {
        Header(detail.name, onSearch = onSearch, onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Order: $sort", modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Playlist order") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Manual", "A–Z", "Date added", "Duration").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { sort = option; sortMenu = false }
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { displayed.firstOrNull()?.let { vm.playTracks(displayed, it.id) } }, enabled = displayed.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
            FilledTonalButton(onClick = { displayed.firstOrNull()?.let { vm.playTracks(displayed, it.id, shuffle = true) } }, enabled = displayed.isNotEmpty()) { Icon(Icons.Default.Shuffle, null); Text("Shuffle") }
            Box {
                OutlinedButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, null); Text("Add") }
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem({ Text("Choose files") }, { addMenu = false; files.launch(arrayOf("audio/*")) })
                    DropdownMenuItem({ Text("Choose folder") }, { addMenu = false; folder.launch(null) })
                }
            }
        }
        if (sort != "Manual") {
            Text(
                "This is a temporary sort. Switch back to Manual to see your saved order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (displayed.isEmpty()) {
            EmptyState("Playlist is empty", "Add tracks from this device.")
        } else if (sort == "Manual") {
            PlaylistTrackList(
                tracks = manualTracks,
                vm = vm,
                playlistId = detail.id,
                onPlay = { vm.playTracks(manualTracks, it.id) },
                onMove = { from, to -> moveManual(from, to, save = true) },
                onDragMove = { from, to -> moveManual(from, to, save = false) },
                onDragFinished = { vm.savePlaylistOrder(detail.id, manualTracks.map { it.id }) }
            )
        } else {
            TrackList(displayed, vm, onPlay = { vm.playTracks(displayed, it.id) }, playlistId = detail.id)
        }
    }
}

@Composable
fun PlaylistTrackList(
    tracks: List<TrackEntity>,
    vm: MainViewModel,
    playlistId: Long,
    onPlay: (TrackEntity) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit
) {
    var selectedIds by remember(tracks.map { it.id }) { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistPicker by remember { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val rowStepPx = with(LocalDensity.current) { 64.dp.toPx() }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onCancel = { selectedIds = emptySet() },
                onPlaylist = { playlistPicker = true },
                onTags = { tagPicker = true },
                onFavorite = { vm.setFavoriteForTracks(selectedIds, true); selectedIds = emptySet() }
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tracks, key = { it.id }) { track ->
                val index = tracks.indexOfFirst { it.id == track.id }
                PlaylistTrackRow(
                    track = track,
                    index = index,
                    lastIndex = tracks.lastIndex,
                    vm = vm,
                    playlistId = playlistId,
                    selected = track.id in selectedIds,
                    selectionMode = selectionMode,
                    onPlay = onPlay,
                    onToggleSelection = { selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id },
                    onStartSelection = { selectedIds = selectedIds + track.id },
                    onMove = onMove,
                    onDragMove = onDragMove,
                    onDragFinished = onDragFinished,
                    rowStepPx = rowStepPx
                )
                HorizontalDivider()
            }
        }
    }

    if (playlistPicker) BatchPlaylistDialog(vm, selectedIds, { playlistPicker = false }) { selectedIds = emptySet(); playlistPicker = false }
    if (tagPicker) BatchTagDialog(vm, selectedIds, { tagPicker = false }) { selectedIds = emptySet(); tagPicker = false }
}

@Composable
private fun PlaylistTrackRow(
    track: TrackEntity,
    index: Int,
    lastIndex: Int,
    vm: MainViewModel,
    playlistId: Long,
    selected: Boolean,
    selectionMode: Boolean,
    onPlay: (TrackEntity) -> Unit,
    onToggleSelection: () -> Unit,
    onStartSelection: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    rowStepPx: Float
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val rowModifier = Modifier
        .fillMaxWidth()
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
        .combinedClickable(
            onClick = { if (selectionMode) onToggleSelection() else onPlay(track) },
            onLongClick = onStartSelection
        )
        .padding(start = 8.dp)

    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) {
            Checkbox(selected, onCheckedChange = { onToggleSelection() })
        } else {
            IconButton(onClick = { vm.toggleFavorite(track) }) {
                Icon(if (track.favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, "Favorite")
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(track.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!selectionMode) {
            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
            IconButton(onClick = { onMove(index, index + 1) }, enabled = index < lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
            Icon(
                Icons.Default.DragHandle,
                "Long-press and drag to reorder",
                modifier = Modifier
                    .size(48.dp)
                    .padding(10.dp)
                    .pointerInput(track.id, index, lastIndex) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragAccum = 0f },
                            onDragEnd = { dragAccum = 0f; onDragFinished() },
                            onDragCancel = { dragAccum = 0f; onDragFinished() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccum += dragAmount.y
                                if (dragAccum > rowStepPx && index < lastIndex) {
                                    onDragMove(index, index + 1)
                                    dragAccum = 0f
                                } else if (dragAccum < -rowStepPx && index > 0) {
                                    onDragMove(index, index - 1)
                                    dragAccum = 0f
                                }
                            }
                        )
                    }
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Rename display name") }, { menu = false; rename = true })
                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, playlistId) })
                }
            }
        }
    }

    if (rename) NameDialog("Rename Track", "Display name", initial = track.displayName ?: track.title(), onDismiss = { rename = false }) { vm.renameTrack(track, it); rename = false }
    if (tags) TagEditor(track, vm, onDismiss = { tags = false })
}

@Composable
fun TagsScreen(vm: MainViewModel, onOpen: (TagSummary) -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Column {
        Header("Tags")
        if (tags.isEmpty()) EmptyState("No tags yet", "Create tags to group tracks across playlists.")
        LazyColumn(Modifier.weight(1f)) {
            items(tags, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = { Text("${tag.count} tracks") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onOpen(tag) }
                )
                HorizontalDivider()
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)) { Icon(Icons.Default.Add, null); Text(" New Tag") }
    }
    if (creating) NameDialog("New Tag", "Tag name", onDismiss = { creating = false }) { vm.createTag(it); creating = false }
}

@Composable
fun TagDetail(vm: MainViewModel, detail: DetailState, onBack: () -> Unit) {
    val tracks by vm.repo.observeTagTracks(detail.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var playChoice by remember { mutableStateOf(false) }
    Column {
        Header(detail.name, onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { playChoice = true }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
            FilledTonalButton(onClick = { tracks.firstOrNull()?.let { vm.playTracks(tracks, it.id, true) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.Shuffle, null); Text("Shuffle") }
        }
        TrackList(tracks, vm, onPlay = { vm.playTracks(tracks, it.id) })
    }
    if (playChoice) AlertDialog(
        onDismissRequest = { playChoice = false },
        title = { Text("Play tagged tracks?") },
        text = { Text("Play ${tracks.size} tracks in order as a temporary queue?") },
        confirmButton = { TextButton(onClick = { tracks.firstOrNull()?.let { vm.playTracks(tracks, it.id) }; playChoice = false }) { Text("Yes") } },
        dismissButton = { TextButton(onClick = { playChoice = false }) { Text("No") } }
    )
}

@Composable
fun FavoritesScreen(vm: MainViewModel) {
    val tracks by vm.favorites.collectAsStateWithLifecycle()
    Column {
        Header("Favorites")
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tracks.firstOrNull()?.let { vm.playTracks(tracks, it.id) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
            FilledTonalButton(onClick = { tracks.firstOrNull()?.let { vm.playTracks(tracks, it.id, true) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.Shuffle, null); Text("Shuffle") }
        }
        if (tracks.isEmpty()) EmptyState("No favorites yet", "Tap the heart beside a track to add it here.")
        else TrackList(tracks, vm, onPlay = { vm.playTracks(tracks, it.id) })
    }
}

@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by vm.repo.searchTracks(query).collectAsStateWithLifecycle(initialValue = emptyList())
    Column {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search tracks") })
        }
        if (query.isNotBlank()) TrackList(results, vm, onPlay = { vm.playTracks(results, it.id) })
    }
}

@Composable
fun SelectionBar(count: Int, onCancel: () -> Unit, onPlaylist: () -> Unit, onTags: () -> Unit, onFavorite: () -> Unit) {
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
}

@Composable
fun TrackList(tracks: List<TrackEntity>, vm: MainViewModel, onPlay: (TrackEntity) -> Unit, playlistId: Long? = null) {
    var selectedIds by remember(tracks) { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistPicker by remember { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onCancel = { selectedIds = emptySet() },
                onPlaylist = { playlistPicker = true },
                onTags = { tagPicker = true },
                onFavorite = { vm.setFavoriteForTracks(selectedIds, true); selectedIds = emptySet() }
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    vm = vm,
                    onPlay = onPlay,
                    playlistId = playlistId,
                    selected = track.id in selectedIds,
                    selectionMode = selectionMode,
                    onToggleSelection = { selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id },
                    onStartSelection = { selectedIds = selectedIds + track.id }
                )
                HorizontalDivider()
            }
        }
    }

    if (playlistPicker) BatchPlaylistDialog(vm, selectedIds, { playlistPicker = false }) { selectedIds = emptySet(); playlistPicker = false }
    if (tagPicker) BatchTagDialog(vm, selectedIds, { tagPicker = false }) { selectedIds = emptySet(); tagPicker = false }
}

@Composable
fun TrackRow(
    track: TrackEntity,
    vm: MainViewModel,
    onPlay: (TrackEntity) -> Unit,
    playlistId: Long?,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(false) }
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
        .combinedClickable(
            onClick = { if (selectionMode) onToggleSelection() else onPlay(track) },
            onLongClick = onStartSelection
        )
        .padding(start = 8.dp)

    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) {
            Checkbox(selected, onCheckedChange = { onToggleSelection() })
        } else {
            IconButton(onClick = { vm.toggleFavorite(track) }) {
                Icon(if (track.favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, "Favorite")
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(track.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Rename display name") }, { menu = false; rename = true })
                    DropdownMenuItem({ Text("Edit tags") }, { menu = false; tags = true })
                    playlistId?.let { pid -> DropdownMenuItem({ Text("Remove from playlist") }, { menu = false; vm.removeFromPlaylist(track.id, pid) }) }
                }
            }
        }
    }
    if (rename) NameDialog("Rename Track", "Display name", initial = track.displayName ?: track.title(), onDismiss = { rename = false }) { vm.renameTrack(track, it); rename = false }
    if (tags) TagEditor(track, vm, onDismiss = { tags = false })
}

@Composable
fun BatchPlaylistDialog(vm: MainViewModel, selectedIds: Set<Long>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var createNew by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${selectedIds.size} tracks to playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) Text("No playlists yet.")
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.count} tracks") },
                        leadingContent = { Icon(Icons.Default.QueueMusic, null) },
                        modifier = Modifier.clickable { vm.addTracksToPlaylist(selectedIds, playlist.id); onDone() }
                    )
                }
                TextButton(onClick = { createNew = true }) { Icon(Icons.Default.Add, null); Text(" New playlist") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (createNew) NameDialog("New Playlist", "Playlist name", onDismiss = { createNew = false }) { name ->
        vm.createPlaylist(name) { playlistId -> vm.addTracksToPlaylist(selectedIds, playlistId) }
        createNew = false
        onDone()
    }
}

@Composable
fun BatchTagDialog(vm: MainViewModel, selectedIds: Set<Long>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var createNew by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tags to ${selectedIds.size} tracks") },
        text = {
            Column {
                if (tags.isEmpty()) Text("No tags yet.")
                tags.forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (tag.id in chosen) chosen - tag.id else chosen + tag.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(tag.id in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + tag.id else chosen - tag.id })
                        Text(tag.name)
                    }
                }
                TextButton(onClick = { createNew = true }) { Icon(Icons.Default.Add, null); Text(" New tag") }
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { vm.addTagsToTracks(selectedIds, chosen); onDone() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (createNew) NameDialog("New Tag", "Tag name", onDismiss = { createNew = false }) { name ->
        vm.createTag(name) { tagId -> vm.addTagsToTracks(selectedIds, setOf(tagId)) }
        createNew = false
        onDone()
    }
}

@Composable
fun TagEditor(track: TrackEntity, vm: MainViewModel, onDismiss: () -> Unit) {
    val allTags by vm.tags.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(track.id) { selected = vm.repo.dao.tagsForTrack(track.id).map { it.id }.toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags") },
        text = {
            Column {
                allTags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                    }) {
                        Checkbox(tag.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + tag.id else selected - tag.id })
                        Text(tag.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            allTags.forEach { vm.setTag(track.id, it.id, it.id in selected) }
            onDismiss()
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val controller by vm.controller.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var hasTrack by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        while (controller != null) {
            playing = controller?.isPlaying == true
            title = controller?.mediaMetadata?.title?.toString().orEmpty()
            hasTrack = controller?.currentMediaItem != null
            delay(300)
        }
    }
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().clickable(enabled = hasTrack, onClick = onOpen).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                if (hasTrack) title.ifBlank { "Current track" } else "Playback controls",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, enabled = hasTrack) { Icon(Icons.Default.SkipPrevious, "Previous track") }
                IconButton(onClick = vm::seekBack, enabled = hasTrack) { Icon(Icons.Default.Replay10, "Back 10 seconds") }
                FilledIconButton(onClick = vm::playPause, enabled = hasTrack) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause") }
                IconButton(onClick = vm::seekForward, enabled = hasTrack) { Icon(Icons.Default.Forward10, "Forward 10 seconds") }
                IconButton(onClick = vm::next, enabled = hasTrack) { Icon(Icons.Default.SkipNext, "Next track") }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: MainViewModel, onBack: () -> Unit) {
    val controller by vm.controller.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val amplifierDb by vm.amplifierDb.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speedMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        while (controller != null) {
            playing = controller?.isPlaying == true
            title = controller?.mediaMetadata?.title?.toString().orEmpty()
            pos = controller?.currentPosition?.coerceAtLeast(0) ?: 0
            dur = controller?.duration?.takeIf { it > 0 } ?: 0
            delay(300)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Now Playing", onBack = onBack, actions = { IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, "Queue") } })
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally))
        Text(title.ifBlank { "No track" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).align(Alignment.CenterHorizontally), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Slider(value = if (dur > 0) pos.toFloat().coerceIn(0f, dur.toFloat()) else 0f, onValueChange = { controller?.seekTo(it.toLong()) }, valueRange = 0f..maxOf(1f, dur.toFloat()), modifier = Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(pos)); Text(formatDuration(dur)) }
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            TextButton(onClick = { speedMenu = true }) { Text("Speed ${speed}×") }
            DropdownMenu(speedMenu, { speedMenu = false }) {
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s -> DropdownMenuItem({ Text("${s}×") }, { vm.setSpeed(s); speedMenu = false }) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }
            IconButton(onClick = vm::seekBack) { Icon(Icons.Default.Replay10, "Back 10 seconds") }
            FilledIconButton(onClick = vm::playPause, modifier = Modifier.size(68.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = vm::seekForward) { Icon(Icons.Default.Forward10, "Forward 10 seconds") }
            IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Default.Shuffle, "Shuffle") }
            IconButton(onClick = {
                val next = when (controller?.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                vm.setRepeatMode(next)
            }) { Icon(Icons.Default.Repeat, "Repeat") }
        }
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Volume amplifier", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(if (amplifierDb < 0.1f) "Off" else "+${"%.1f".format(amplifierDb)} dB")
                }
                Slider(
                    value = amplifierDb,
                    onValueChange = vm::setAmplifierDb,
                    valueRange = 0f..12f,
                    steps = 11
                )
                Text(
                    "Boosts audio above normal playback. Higher settings can distort loud recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (showQueue) QueueDialog(vm, onDismiss = { showQueue = false })
}

@Composable
fun QueueDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val controller by vm.controller.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) { while (controller != null) { tick++; delay(500) } }
    val c = controller
    val count = c?.mediaItemCount ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue") },
        text = {
            if (count == 0) Text("Queue is empty") else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(count, key = { it }) { index ->
                    val item = c!!.getMediaItemAt(index)
                    val isCurrent = index == c.currentMediaItemIndex
                    ListItem(
                        headlineContent = { Text(item.mediaMetadata.title?.toString() ?: "Track") },
                        leadingContent = { if (isCurrent) Icon(Icons.Default.PlayArrow, "Now playing") else Text("${index + 1}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { if (index > c.currentMediaItemIndex + 1) { c.moveMediaItem(index, index - 1); tick++ } }, enabled = index > c.currentMediaItemIndex + 1) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                IconButton(onClick = { if (index + 1 < c.mediaItemCount) { c.moveMediaItem(index, index + 1); tick++ } }, enabled = index >= c.currentMediaItemIndex + 1 && index + 1 < c.mediaItemCount) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { if (!isCurrent) { c.removeMediaItem(index); tick++ } }, enabled = !isCurrent) { Icon(Icons.Default.Close, "Remove") }
                            }
                        },
                        modifier = Modifier.clickable { c.seekTo(index, 0); c.play(); tick++ }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NameDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onSave(value) }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
