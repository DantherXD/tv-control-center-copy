package com.example.tvcontrolcenter.system.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.repository.NowPlayingSource
import com.example.tvcontrolcenter.system.notif.PanelNotificationsSource
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads active media sessions. Android only exposes them to an enabled
 * NotificationListenerService, so [hasAccess] reflects that grant.
 */
@Singleton
class MediaSessionNowPlayingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : NowPlayingSource {

    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private var registered = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    override fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Safe to call repeatedly. Quietly does nothing while access is missing. */
    override fun refresh() {
        if (!hasAccess()) {
            _nowPlaying.value = null
            return
        }
        try {
            if (!registered) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listener)
                registered = true
            }
            pick(manager.getActiveSessions(listener))
        } catch (_: SecurityException) {
            _nowPlaying.value = null
        }
    }

    private fun pick(sessions: List<MediaController>) {
        controller?.unregisterCallback(controllerCallback)
        controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        controller?.registerCallback(controllerCallback)
        publish()
    }

    // The last picture we shrank, so a position tick does not rescale the same bitmap.
    private var artSource: Bitmap? = null
    private var artSmall: Bitmap? = null

    /** Album picture from the metadata, shrunk to at most [ART_MAX] px so it stays cheap to keep. */
    private fun artwork(meta: MediaMetadata?): Bitmap? {
        val raw = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (raw === artSource) return artSmall
        artSource = raw
        artSmall = runCatching {
            val longest = maxOf(raw.width, raw.height)
            if (longest <= ART_MAX) raw else {
                val scale = ART_MAX / longest.toFloat()
                Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * scale).toInt().coerceAtLeast(1),
                    (raw.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
        }.getOrNull()
        return artSmall
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val state = c?.playbackState
        _nowPlaying.value = if (c == null || title.isNullOrBlank()) null else NowPlaying(
            title = title,
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            packageName = c.packageName,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            artwork = artwork(meta),
            durationMs = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L),
            positionMs = (state?.position ?: 0L).coerceAtLeast(0L),
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed ?: 1f,
        )
    }

    private companion object {
        const val ART_MAX = 256
    }

    override fun togglePlayPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    override fun skipNext() { controller?.transportControls?.skipToNext() }

    override fun skipPrevious() { controller?.transportControls?.skipToPrevious() }
}

/**
 * Exists so the user can grant notification-listener access, which unlocks media sessions and
 * the notifications page. The system binds it; everything it learns is handed to the sources.
 */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {
    @Inject lateinit var source: MediaSessionNowPlayingSource
    @Inject lateinit var notifications: PanelNotificationsSource

    override fun onListenerConnected() {
        source.refresh()
        notifications.attach(this)
    }

    override fun onListenerDisconnected() {
        notifications.detach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) notifications.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) notifications.onRemoved(sbn)
    }

    override fun onDestroy() {
        notifications.detach(this)
        super.onDestroy()
    }
}
