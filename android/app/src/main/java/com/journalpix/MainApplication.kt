package com.journalpix

import android.app.Application
import com.journalpix.data.OfflineStore

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Offline-first: load persisted trips / places / photo paths from filesDir
        OfflineStore.init(this)
    }
}
