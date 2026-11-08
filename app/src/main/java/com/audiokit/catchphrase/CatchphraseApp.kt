package com.audiokit.catchphrase

import android.app.Application
import com.audiokit.catchphrase.data.AppDatabase
import com.audiokit.catchphrase.data.Repository
import com.audiokit.catchphrase.data.SettingsStore
import com.audiokit.catchphrase.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CatchphraseApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: Repository by lazy { Repository(database, this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        appScope.launch { runCatching { repository.cleanupOldClips() } }
    }
}
