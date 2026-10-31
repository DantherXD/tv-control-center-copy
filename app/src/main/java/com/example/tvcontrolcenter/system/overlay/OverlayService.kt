package com.example.tvcontrolcenter.system.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.system.openCapabilitySettings

/**
 * Overlay path for devices where the accessibility service is not enabled.
 * Needs the "display over other apps" permission; without it, asks for it and stops.
 */
class OverlayService : Service() {
    private var controller: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            openCapabilitySettings(Capability.OVERLAY)
            stopSelf()
            return START_NOT_STICKY
        }
        val c = controller ?: OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            onGone = { stopSelf() },
        ).also { controller = it }
        if (c.isShowing) c.hide() else c.show()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller?.hide()
        controller = null
    }

    companion object {
        fun toggle(context: Context) {
            context.startService(Intent(context, OverlayService::class.java))
        }
    }
}
