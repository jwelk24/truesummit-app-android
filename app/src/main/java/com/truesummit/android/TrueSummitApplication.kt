package com.truesummit.android

import android.app.Application
import com.truesummit.android.service.SurfaceRefreshCoordinator

class TrueSummitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Observes the database for the life of the process and pushes changes
        // out to the widgets and any paired watch.
        SurfaceRefreshCoordinator.start(this)
    }
}
