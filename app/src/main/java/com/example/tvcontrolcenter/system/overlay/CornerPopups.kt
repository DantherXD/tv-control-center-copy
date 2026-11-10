package com.example.tvcontrolcenter.system.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.tvcontrolcenter.domain.model.AppNotification
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import com.example.tvcontrolcenter.ui.panel.CornerNoticeCard
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme
import com.example.tvcontrolcenter.ui.theme.ProvideUiScale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A new notification in the corner of the screen, for the editor's "Corner pop-ups" setting.
 *
 * The card is a window of its own, added when a notification arrives and taken away a few seconds
 * later, so nothing runs and nothing is drawn while none does. It never takes focus, so the remote
 * keeps driving whatever is playing underneath. Without a place to put it — no "display over other
 * apps" and no accessibility service — there is nowhere to draw, so it does nothing.
 */
@Singleton
class CornerPopups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val layouts: LayoutRepository,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val showing = mutableStateOf<AppNotification?>(null)

    private var view: ComposeView? = null
    private var host: OverlayHost? = null
    private var manager: WindowManager? = null
    private var lastKey: String? = null
    private var lastWords: String? = null

    private val dismiss = Runnable { remove() }

    init {
        current = this
    }

    /** Takes the card down now: the panel is opening over that corner anyway. */
    fun hide() {
        handler.post { remove() }
    }

    /** Called for every new notification; the setting decides whether anything is drawn. */
    fun show(notice: AppNotification) {
        if (!layouts.settings.value.cornerNotices) return
        handler.post { showOnMain(notice) }
    }

    private fun showOnMain(notice: AppNotification) {
        // An app that keeps rewriting one notification (a download, a timer) would otherwise put a
        // card up every few seconds; the same words again are not news.
        val words = "${notice.appLabel}\n${notice.title}\n${notice.text}"
        if (notice.key == lastKey && words == lastWords) return
        if (!addWindow()) return
        lastKey = notice.key
        lastWords = words
        showing.value = notice
        markVisible(SHOW_MILLIS)
        handler.removeCallbacks(dismiss)
        handler.postDelayed(dismiss, SHOW_MILLIS)
    }

    private fun addWindow(): Boolean {
        if (view != null) return true
        val (source, type) = windowHost() ?: return false
        val windowManager = source.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val newHost = OverlayHost().also { it.resume() }
        val composeView = ComposeView(source).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(newHost)
            setViewTreeSavedStateRegistryOwner(newHost)
            setViewTreeViewModelStoreOwner(newHost)
            setContent {
                ControlCenterTheme {
                    // The panel's own scale, so the card is a readable size on a 4K set too.
                    ProvideUiScale(layouts.settings.value.uiScale) {
                        showing.value?.let { CornerNoticeCard(it) }
                    }
                }
            }
        }
        // Kept off the edge by a share of the screen, since this window has no UI scale of its own.
        val inset = (source.resources.displayMetrics.widthPixels * CORNER_INSET).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = inset
            y = inset
        }
        return try {
            windowManager.addView(composeView, params)
            view = composeView
            host = newHost
            manager = windowManager
            true
        } catch (_: RuntimeException) {
            // No permission and no accessibility window: leave nothing half-built behind.
            newHost.destroy()
            false
        }
    }

    /**
     * Where the card can be added: our own window once "display over other apps" is granted,
     * otherwise one belonging to the accessibility service, which needs no permission of its own.
     */
    private fun windowHost(): Pair<Context, Int>? = when {
        Settings.canDrawOverlays(context) ->
            context to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> ControlCenterAccessibilityService.instance?.let {
            it to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
    }

    private fun remove() {
        handler.removeCallbacks(dismiss)
        val v = view
        val wm = manager
        if (v != null && wm != null) runCatching { wm.removeViewImmediate(v) }
        host?.destroy()
        view = null
        host = null
        manager = null
        showing.value = null
        clearVisible()
    }

    companion object {
        /** Until when a card is on screen, so a panel opened at that moment can land on its list. */
        @Volatile
        private var visibleUntil = 0L

        /** The instance in use, so the panel can take a card down without being handed one. */
        @Volatile
        private var current: CornerPopups? = null

        /** The card of the instance in use, if one is up. Called when the panel opens over it. */
        fun hideCard() {
            current?.hide()
        }

        /**
         * True while a card is on screen, or was a moment ago: the trigger pressed while one is
         * showing opens the panel straight on the notifications window instead of the grid.
         */
        fun visibleNow(): Boolean = SystemClock.elapsedRealtime() < visibleUntil

        private fun markVisible(millis: Long) {
            visibleUntil = SystemClock.elapsedRealtime() + millis
        }

        private fun clearVisible() {
            visibleUntil = 0L
        }

        const val SHOW_MILLIS = 5_000L
        /** Share of the screen width that keeps the card off the edge. */
        const val CORNER_INSET = 0.025f
    }
}
