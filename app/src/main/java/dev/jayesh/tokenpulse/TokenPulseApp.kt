package dev.jayesh.tokenpulse

import android.app.Application
import dev.jayesh.tokenpulse.work.Alerts
import dev.jayesh.tokenpulse.work.Sync

class TokenPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Alerts.ensureChannel(this)
        Sync.schedule(this)
    }
}
