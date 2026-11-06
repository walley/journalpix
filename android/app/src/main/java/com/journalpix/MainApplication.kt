package com.journalpix

import android.app.Application
import android.util.Log
import com.journalpix.data.OfflineStore

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Offline-first: load persisted trips / places / photo paths from filesDir
        OfflineStore.init(this)

        // MapLibre GL must be initialized before any MapView is created.
        // Empty API key + MapLibre tile server: we use our own free style URL (OpenFreeMap), no key needed.
        org.maplibre.android.MapLibre.getInstance(
            this,
            "",
            org.maplibre.android.WellKnownTileServer.MapLibre
        )

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
