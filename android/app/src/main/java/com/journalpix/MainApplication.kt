package com.journalpix

import android.app.Application
import android.util.Log
import com.journalpix.data.OfflineStore

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Offline-first: load persisted trips / places / photo paths from filesDir
        OfflineStore.init(this)

        // Workaround for openmapview crash on view detach:
        // MapController.onDestroy -> cancelChildren throws during teardown,
        // which propagates as an uncaught exception and kills the process.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable.stackTrace.any {
                    it.className.contains("openmapview") ||
                    it.className.contains("MapController")
                }) {
                Log.w("MainApplication", "Ignoring openmapview teardown crash", throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
