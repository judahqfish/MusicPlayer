package com.everywhen.offlinemusic

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MusicApplication : Application() {
    val database by lazy { MusicDatabase.create(this) }
    val repository by lazy { MusicRepository(this, database.musicDao()) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crashFile by lazy { filesDir.resolve("last_crash.txt") }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                crashFile.writeText(writer.toString())
            }
            previous?.uncaughtException(thread, throwable)
        }

        // Keep one small metadata backup outside app-private storage. Room flows
        // invalidate when playlist/tag membership or ordering changes, so this
        // automatically refreshes the file after edits while debouncing bursts.
        applicationScope.launch {
            combine(repository.playlists, repository.tags) { _, _ -> Unit }
                .debounce(1500)
                .collect {
                    runCatching { writeLibraryBackup(this@MusicApplication, repository) }
                }
        }
    }

    fun takeLastCrash(): String? = runCatching {
        if (!crashFile.exists()) return@runCatching null
        val text = crashFile.readText()
        crashFile.delete()
        text.take(12000)
    }.getOrNull()
}
