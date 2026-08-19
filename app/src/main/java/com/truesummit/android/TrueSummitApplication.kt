package com.truesummit.android

import android.app.Application
import android.util.Log
import com.plaid.link.Plaid
import com.truesummit.android.service.SurfaceRefreshCoordinator

class TrueSummitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Observes the database for the life of the process and pushes changes
        // out to the widgets and any paired watch.
        SurfaceRefreshCoordinator.start(this)

        // Link's own event stream, which is delivered process-wide rather than
        // through the activity result. An OAuth round trip through Chrome comes
        // back in a second task, and the result never reached the launcher; this
        // reports what Link itself thinks happened either way, so a HANDOFF with
        // no LinkSuccess pins the failure on result delivery rather than on the
        // flow.
        Plaid.setLinkEventListener { event ->
            Log.w("PlaidLink", "event=$event")
        }
    }
}
