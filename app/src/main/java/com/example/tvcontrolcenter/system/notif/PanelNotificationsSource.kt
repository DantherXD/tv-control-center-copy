package com.example.tvcontrolcenter.system.notif

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.tvcontrolcenter.domain.model.AppNotification
import com.example.tvcontrolcenter.domain.repository.NotificationSource
import com.example.tvcontrolcenter.system.overlay.CornerPopups
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notifications in the shade, read through the app's own notification listener — the same
 * grant Now Playing needs. The raw [StatusBarNotification] is kept next to the plain data because
 * its content intent is what a press has to fire and its key is what a dismissal cancels.
 */
@Singleton
class PanelNotificationsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val popups: CornerPopups,
) : NotificationSource {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    /** Posted notification by key, newest last as they arrive. */
    private val posted = LinkedHashMap<String, StatusBarNotification>()
    private val labels = HashMap<String, String>()

    // Connected while the listener is bound. Dismissing needs the service itself, not a manager.
    private var listener: NotificationListenerService? = null

    override fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** The listener connected: its list is the truth from here on. */
    fun attach(service: NotificationListenerService) {
        listener = service
        refresh()
    }

    /** The listener went away: nothing can be read or cancelled until it is back. */
    fun detach(service: NotificationListenerService) {
        if (listener !== service) return
        listener = null
        posted.clear()
        _notifications.value = emptyList()
    }

    /** Safe to call repeatedly. Reads what is already in the shade, like the media source does. */
    override fun refresh() {
        if (!hasAccess()) {
            posted.clear()
            _notifications.value = emptyList()
            return
        }
        val active = runCatching { listener?.activeNotifications }.getOrNull() ?: return
        posted.clear()
        active.forEach { posted[it.key] = it }
        publish()
    }

    fun onPosted(sbn: StatusBarNotification) {
        posted[sbn.key] = sbn
        publish()
        // A new one in the shade: the corner says so, while that is switched on.
        if (sbn.worthShowing()) popups.show(sbn.toRow())
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (posted.remove(sbn.key) != null) publish()
    }

    override fun open(key: String): Boolean {
        val intent = posted[key]?.notification?.contentIntent ?: return false
        return runCatching { intent.send() }.isSuccess
    }

    override fun dismiss(key: String) {
        val sbn = posted[key] ?: return
        if (!sbn.isClearable) return
        runCatching { listener?.cancelNotification(sbn.key) }
    }

    override fun clearAll() {
        posted.values.filter { it.isClearable }.forEach {
            runCatching { listener?.cancelNotification(it.key) }
        }
    }

    private fun publish() {
        val all = posted.values.toList()
        // A summary whose children are also in the shade would repeat every one of them.
        val groups = all.mapNotNull { it.notification.group }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        _notifications.value = all
            .filter { it.worthShowing() }
            .filterNot { sbn ->
                sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                    (sbn.notification.group?.let { it in groups } == true)
            }
            .sortedByDescending { it.postTime }
            .map { it.toRow() }
    }

    /** The rules the list and the corner card both follow. */
    private fun StatusBarNotification.worthShowing(): Boolean =
        // Our own notifications are not the user's business.
        packageName != context.packageName &&
            // Now Playing already has a page; its notification would be a second copy of it.
            notification.category != Notification.CATEGORY_TRANSPORT &&
            // "DolbyAudioService", "Wifi monitor service", "Serial console enabled": background
            // services telling us they are running. There is nothing to do with any of them.
            !isServiceNotice() &&
            // Nothing to open and nothing to clear: a row that could only ever do nothing.
            (isClearable || notification.contentIntent != null)

    /**
     * An ongoing notice from a foreground service or from the system. These describe a service that
     * is running rather than something that happened, so the list leaves them out.
     */
    private fun StatusBarNotification.isServiceNotice(): Boolean {
        val flags = notification.flags
        if (flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true
        return isSystemApp()
    }

    /** A notice from a system app: "Serial console enabled", "... is running", and the like. */
    private fun StatusBarNotification.isSystemApp(): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).flags and
            ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    private fun StatusBarNotification.toRow(): AppNotification {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val app = label(packageName)
        return AppNotification(
            key = key,
            packageName = packageName,
            appLabel = app,
            // Some notifications only carry text: the text then leads, and there is no second line.
            title = title ?: text ?: app,
            text = if (title == null) null else text,
            time = postTime,
            clearable = isClearable,
        )
    }

    private fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
