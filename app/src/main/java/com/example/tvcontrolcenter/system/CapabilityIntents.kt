package com.example.tvcontrolcenter.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.tvcontrolcenter.domain.model.Capability

/** The settings screen where the user can grant [this]. Always a new-task intent so it works from services. */
fun Capability.settingsIntent(context: Context): Intent {
    val intent = when (this) {
        Capability.ACCESSIBILITY_SERVICE -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Capability.NOTIFICATION_POLICY -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        Capability.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        Capability.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Starts [capability]'s settings screen, quietly ignoring TV builds that strip it. */
fun Context.openCapabilitySettings(capability: Capability) {
    try {
        startActivity(capability.settingsIntent(this))
    } catch (_: android.content.ActivityNotFoundException) {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
