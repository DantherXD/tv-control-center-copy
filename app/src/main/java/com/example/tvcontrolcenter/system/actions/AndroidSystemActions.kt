package com.example.tvcontrolcenter.system.actions

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.wifi.WifiManager
import android.widget.Toast
import com.example.tvcontrolcenter.domain.model.LauncherApp
import com.example.tvcontrolcenter.domain.model.TvInputItem
import com.example.tvcontrolcenter.R
import com.example.tvcontrolcenter.system.backdrop.ScreenshotSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.repository.SystemActions
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Real system actions where Android allows them. Anything else returns a typed result. */
@Singleton
class AndroidSystemActions @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemActions {

    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    override fun setVolumePercent(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) * max / 100f).toInt()
        // On HDMI-CEC / fixed-volume boxes this can be a no-op. See README.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    override fun toggleMute(): ActionResult = try {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        ActionResult.Done
    } catch (_: SecurityException) {
        ActionResult.Unavailable
    }

    override fun openWifiSettings() = launchSettings(Settings.ACTION_WIFI_SETTINGS)

    // Google TV and Android TV call this page "Remotes & accessories"; the TV settings app has its
    // own screen for it, so try that first and the standard Bluetooth action after.
    override fun openBluetoothSettings() = launchFirst(
        tvSettings("com.android.tv.settings"),
        tvSettings("com.google.android.tvsettings"),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )

    override fun openGameControllerSettings() = openBluetoothSettings()

    override fun openAccessibilitySettings() = launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    override fun openSettings() = launchSettings(Settings.ACTION_SETTINGS)

    override fun openDisplaySettings() = launchSettings(Settings.ACTION_DISPLAY_SETTINGS)

    override fun openSoundSettings() = launchSettings(Settings.ACTION_SOUND_SETTINGS)

    override fun openCaptionSettings() = launchSettings(Settings.ACTION_CAPTIONING_SETTINGS)

    /**
     * Starts the configured screen saver. Android 14 no longer lets an ordinary app trigger
     * the system dream (SystemUI's Somnambulator is a no-op there), so use the public preview
     * activity some screensaver apps export. AerialViews is the established one; the selected
     * screensaver is checked first so its own test activity wins. Settings remains the fallback.
     */
    override fun startScreensaver(): ActionResult {
        if (launchSelectedDreamPreview()) return ActionResult.Done
        if (startComponent(AERIAL_VIEWS, AERIAL_VIEWS_TEST)) return ActionResult.Done
        // Older TV builds still honour this trigger; Android 14 builds only finish it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            startComponent("com.android.systemui", "com.android.systemui.Somnambulator")
        ) {
            return ActionResult.Done
        }
        Toast.makeText(context, R.string.screensaver_no_trigger, Toast.LENGTH_LONG).show()
        return launchSettings(Settings.ACTION_DREAM_SETTINGS)
    }

    /** Launches a screensaver app's exported test/preview activity, if it has one. */
    private fun launchSelectedDreamPreview(): Boolean {
        val raw = Settings.Secure.getString(context.contentResolver, SCREENSAVER_COMPONENTS) ?: return false
        return raw.split(',').any { part ->
            val component = ComponentName.unflattenFromString(part.trim()) ?: return@any false
            val info = runCatching {
                context.packageManager.getPackageInfo(component.packageName, PackageManager.GET_ACTIVITIES)
            }.getOrNull() ?: return@any false
            val activities = info.activities ?: return@any false
            val candidates = activities
                .asSequence()
                .filter { it.exported }
                .filter { activity ->
                    val name = activity.name.lowercase()
                    (name.contains("test") || name.contains("preview")) &&
                        !name.contains("settings") && !name.contains("preference")
                }
                .sortedBy { if (it.name.contains("test", ignoreCase = true)) 0 else 1 }
                .toList()
            candidates.any { startComponent(component.packageName, it.name) }
        }
    }

    private fun startComponent(packageName: String, className: String): Boolean = try {
        context.startActivity(
            Intent()
                .setClassName(packageName, className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    override fun openAppsSettings() = launchSettings(Settings.ACTION_APPLICATION_SETTINGS)

    // An app pinned to a slot: its own launcher intent, nothing special.
    override fun openApp(packageName: String): ActionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult.Unavailable
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    @Suppress("DEPRECATION")
    override fun installedApps(): List<LauncherApp> {
        val pm = context.packageManager
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val found = pm.queryIntentActivities(leanback, 0).ifEmpty {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }
        return found
            .map { LauncherApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override fun openSearch(): ActionResult {
        val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(assist)
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    override fun toggleDoNotDisturb(currentlyOn: Boolean): ActionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult.NeedsCapability(Capability.NOTIFICATION_POLICY)
        }
        nm.setInterruptionFilter(
            if (currentlyOn) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_NONE,
        )
        return ActionResult.Done
    }

    override fun powerOff(): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        return if (service.showPowerDialog()) ActionResult.Done else sleepScreen()
    }

    override fun sleepScreen(): ActionResult {
        // The TV settings app's own picture-off screen, which is what the TV's Lock control does.
        // Tried first because on these sets it turns the picture off without asking a service.
        try {
            context.startActivity(
                Intent()
                    .setClassName("com.android.tv.settings", "com.android.tv.settings.partnercustomizer.power.PictureOffActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return ActionResult.Done
        } catch (_: Exception) {
            // Not on this TV: fall back to the accessibility service's lock.
        }
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        return if (service.lockScreen()) ActionResult.Done else ActionResult.Unavailable
    }

    override fun goHome(): ActionResult {
        if (ControlCenterAccessibilityService.instance != null) {
            return global(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        // No accessibility service: the plain HOME intent still works from the panel's window.
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(home)
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    override fun showRecents() = global(AccessibilityService.GLOBAL_ACTION_RECENTS)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val wifi get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter get() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("DEPRECATION")
    override fun wifiEnabled(): Boolean = runCatching { wifi.isWifiEnabled }.getOrDefault(false)

    override fun bluetoothEnabled(): Boolean =
        runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)

    /** Needs the location permission on Android 8.1+, otherwise the name is hidden and this gives null. */
    @Suppress("DEPRECATION")
    override fun wifiNetworkName(): String? = runCatching {
        wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    @Suppress("DEPRECATION")
    override fun setWifiEnabled(enabled: Boolean): ActionResult {
        // A device owner, a profile owner and system builds are the documented exemptions to the
        // Android 10 ban; every other app just gets false.
        if (runCatching { wifi.setWifiEnabled(enabled) }.getOrDefault(false)) return ActionResult.Done
        // Writing Settings.Global's "wifi_on" does not help, so it is not tried: the Wi-Fi service
        // reads that value at boot and writes it itself, and never watches it. The write succeeds,
        // nothing switches, and the row springs back when it re-reads the radio.
        //
        // What is left is the system's own switch, pressed for you: the accessibility service opens
        // the Wi-Fi screen, clicks the switch, and closes the screen again.
        if (toggleFromSystemScreen(Settings.ACTION_WIFI_SETTINGS, enabled, WIFI_WORDS)) {
            return ActionResult.Done
        }
        // No accessibility service: the small system panel, then the full screen.
        return launchFirst(
            Intent(WIFI_PANEL),
            Intent(INTERNET_PANEL),
            Intent(Settings.ACTION_WIFI_SETTINGS),
        )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun setBluetoothEnabled(enabled: Boolean): ActionResult {
        // enable() and disable() need BLUETOOTH_PRIVILEGED, which an ordinary app cannot hold, so
        // they hand back false on a stock TV.
        val adapter = bluetoothAdapter
        if (runCatching { if (enabled) adapter?.enable() else adapter?.disable() }.getOrNull() == true) {
            return ActionResult.Done
        }
        // Bluetooth keeps its state in "bluetooth_on", which the Bluetooth manager does watch on
        // the builds that honour it, so the write can switch the radio for real with only
        // WRITE_SECURE_SETTINGS (adb can grant that). Where a build ignores the write nothing
        // switches and the row springs back on its re-read.
        if (writeGlobal(BLUETOOTH_ON, enabled)) return ActionResult.Done
        if (toggleFromSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS, enabled, BLUETOOTH_WORDS)) {
            return ActionResult.Done
        }
        return openBluetoothSettings()
    }

    /** Drives the system's own switch through the accessibility service; false when it cannot. */
    private fun toggleFromSystemScreen(action: String, on: Boolean, keywords: Array<String>): Boolean =
        ControlCenterAccessibilityService.instance?.switchRadio(action, on, keywords) ?: false

    /**
     * Writes one of the radio settings. Possible only with WRITE_SECURE_SETTINGS, which
     * `adb shell pm grant com.example.tvcontrolcenter android.permission.WRITE_SECURE_SETTINGS`
     * gives on a TV without root; the grant survives reboots and app updates. False when it is
     * missing, so the caller can still fall back to the system's own screen.
     */
    private fun writeGlobal(key: String, on: Boolean): Boolean {
        val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        return runCatching {
            Settings.Global.putInt(context.contentResolver, key, if (on) 1 else 0)
        }.isSuccess
    }

    /**
     * Needs the Bluetooth connect permission on Android 12+; empty without it. A rename is stored as
     * the device's alias, so that is read first: [android.bluetooth.BluetoothDevice.getName] keeps
     * the name the device had when it was first bonded, which is how a renamed device went on
     * showing its old name here.
     */
    @SuppressLint("MissingPermission")
    override fun pairedBluetoothDevices(): List<String> = runCatching {
        bluetoothAdapter?.bondedDevices.orEmpty()
            .mapNotNull { device ->
                val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { device.alias }.getOrNull()
                } else {
                    null
                }
                (alias?.takeIf { it.isNotBlank() } ?: device.name)?.takeIf { it.isNotBlank() }
            }
            .take(5)
    }.getOrDefault(emptyList())

    override fun tvInputs(): List<TvInputItem> = runCatching {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        manager.tvInputList
            .filter { it.isPassthroughInput }
            .map { TvInputItem(it.id, (it.loadCustomLabel(context) ?: it.loadLabel(context)).toString()) }
            .sortedWith { a, b -> naturalCompare(a.label, b.label) }
    }.getOrDefault(emptyList())

    override fun caffeineOn(): Boolean = Caffeine.isOn

    override fun toggleCaffeine(): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        return if (runCatching { Caffeine.toggle(service) }.isSuccess) ActionResult.Done else ActionResult.Unavailable
    }

    override fun switchInput(id: String): ActionResult = try {
        val uri = TvContract.buildChannelUriForPassthroughInput(id)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Done
    } catch (_: Exception) {
        ActionResult.Unavailable
    }

    /**
     * Android TV does nothing for the system screenshot action, so take the picture ourselves
     * (accessibility screenshot, Android 11+) once the panel has closed, and save it to Pictures.
     */
    override fun takeScreenshot(): ActionResult {
        if (ControlCenterAccessibilityService.instance == null) {
            return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, minSdk = Build.VERSION_CODES.P)
        }
        scope.launch {
            delay(PANEL_CLOSE_DELAY_MILLIS)
            val saved = ScreenshotSaver.takeAndSave(context)
            Toast.makeText(
                context,
                if (saved) R.string.screenshot_saved else R.string.screenshot_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
        return ActionResult.Done
    }

    override fun showNotifications() = global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    /** Runs an accessibility global action once the panel has had time to close. */
    private fun global(action: Int, minSdk: Int = 0): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        if (Build.VERSION.SDK_INT < minSdk) return ActionResult.Unavailable
        handler.postDelayed({ service.performGlobalAction(action) }, PANEL_CLOSE_DELAY_MILLIS)
        return ActionResult.Done
    }

    private fun tvSettings(pkg: String) = Intent().setClassName(pkg, "com.android.tv.settings.accessories.AddAccessoryActivity")

    /** Tries each screen in turn, then the root settings. No visibility checks: starting is enough. */
    private fun launchFirst(vararg intents: Intent): ActionResult {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ActionResult.Done
            } catch (_: Exception) {
                // Not on this TV: next one.
            }
        }
        return launchSettings(Settings.ACTION_SETTINGS)
    }

    private fun launchSettings(action: String): ActionResult = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Done
    } catch (_: ActivityNotFoundException) {
        // Some TV builds strip individual settings screens. Fall back to the root screen.
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    private companion object {
        /** Slightly longer than the panel's close spring, so it is gone before the action runs. */
        const val PANEL_CLOSE_DELAY_MILLIS = 600L

        /** The Settings.Global key, spelled out because the SDK keeps the constant hidden. */
        const val BLUETOOTH_ON = "bluetooth_on"

        /** Names the radio in a settings screen, so the right switch is the one clicked. */
        val WIFI_WORDS = arrayOf("wi-fi", "wifi", "wlan")
        val BLUETOOTH_WORDS = arrayOf("bluetooth")

        /** Settings.Panel actions, spelled out for the same reason. */
        const val WIFI_PANEL = "android.settings.panel.action.WIFI"
        const val INTERNET_PANEL = "android.settings.panel.action.INTERNET_CONNECTIVITY"

        /** The screensaver component list is readable without a special permission. */
        const val SCREENSAVER_COMPONENTS = "screensaver_components"

        /** AerialViews exports this activity specifically so other apps can start its screensaver. */
        const val AERIAL_VIEWS = "com.neilturner.aerialviews"
        const val AERIAL_VIEWS_TEST = "com.neilturner.aerialviews.ui.screensaver.TestActivity"
    }
}

/** "HDMI 2" before "HDMI 10": compares runs of digits as numbers. */
internal fun naturalCompare(a: String, b: String): Int {
    val x = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList()
    val y = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) {
            p.toBigInteger().compareTo(q.toBigInteger())
        } else p.compareTo(q, ignoreCase = true)
        if (c != 0) return c
    }
    return x.size - y.size
}
