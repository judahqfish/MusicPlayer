package com.everywhen.offlinemusic

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

class MusicApplication : Application() {
    val database by lazy { MusicDatabase.create(this) }
    val repository by lazy { MusicRepository(this, database.musicDao()) }

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
    }

    fun takeLastCrash(): String? = runCatching {
        if (!crashFile.exists()) return@runCatching null
        val text = crashFile.readText()
        crashFile.delete()
        text.take(12000)
    }.getOrNull()
}
