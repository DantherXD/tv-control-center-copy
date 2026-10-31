package com.example.tvcontrolcenter.system.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.repository.NowPlayingSource
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

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        _nowPlaying.value = if (c == null || title.isNullOrBlank()) null else NowPlaying(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            packageName = c.packageName,
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    override fun togglePlayPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    override fun skipNext() { controller?.transportControls?.skipToNext() }

    override fun skipPrevious() { controller?.transportControls?.skipToPrevious() }
}

/** Exists so the user can grant notification-listener access, which unlocks media sessions. */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {
    @Inject lateinit var source: MediaSessionNowPlayingSource

    override fun onListenerConnected() {
        source.refresh()
    }
}
