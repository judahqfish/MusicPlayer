package com.everywhen.offlinemusic

import android.app.Application

class MusicApplication : Application() {
    val database by lazy { MusicDatabase.create(this) }
    val repository by lazy { MusicRepository(this, database.musicDao()) }
}
