package com.example.tvcontrolcenter.data

import android.content.Context
import com.example.tvcontrolcenter.domain.model.BluetoothIcon
import com.example.tvcontrolcenter.domain.model.BlurLevels
import com.example.tvcontrolcenter.domain.model.BlurMethod
import com.example.tvcontrolcenter.domain.model.FrostStyle
import com.example.tvcontrolcenter.domain.model.IconPack
import com.example.tvcontrolcenter.domain.model.IconStyle
import com.example.tvcontrolcenter.domain.model.MenuIcon
import com.example.tvcontrolcenter.domain.model.MusicStyle
import com.example.tvcontrolcenter.domain.model.PanelLayout
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.domain.model.PhosphorWeight
import com.example.tvcontrolcenter.domain.model.SleepTimerPresets
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.Wallpaper
import com.example.tvcontrolcenter.domain.model.WindowMotion
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.domain.repository.SleepTimerRepository
import com.example.tvcontrolcenter.domain.repository.SystemActions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the last focused tile across panel openings and process restarts. */
@Singleton
class PreferencesFocusMemory @Inject constructor(
    @ApplicationContext context: Context,
) : FocusMemory {
    private val prefs = context.getSharedPreferences("control_center", Context.MODE_PRIVATE)

    override fun get(): TileId =
        prefs.getString(KEY, null)?.let { name -> TileId.entries.firstOrNull { it.name == name } }
            ?: TileId.POWER

    override fun set(id: TileId) {
        prefs.edit().putString(KEY, id.name).apply()
    }

    private companion object {
        const val KEY = "last_focused_tile"
    }
}

/**
 * Counts down in whole minutes and puts the screen to sleep at zero.
 * The scope and tick length are injectable so tests can run on virtual time.
 */
class SleepTimerRepositoryImpl(
    private val scope: CoroutineScope,
    private val actions: SystemActions,
    private val tickMillis: Long = 60_000L,
) : SleepTimerRepository {

    private val _remaining = MutableStateFlow<Int?>(null)
    override val remainingMinutes: StateFlow<Int?> = _remaining.asStateFlow()
    private var job: Job? = null
    private var presetIndex = 0

    override fun cycle() {
        presetIndex = (presetIndex + 1) % SleepTimerPresets.size
        val minutes = SleepTimerPresets[presetIndex]
        if (minutes == 0) cancel() else start(minutes)
    }

    override fun cancel() {
        job?.cancel()
        job = null
        presetIndex = 0
        _remaining.value = null
    }

    private fun start(minutes: Int) {
        job?.cancel()
        _remaining.value = minutes
        job = scope.launch {
            var left = minutes
            while (left > 0) {
                delay(tickMillis)
                left -= 1
                _remaining.value = left
            }
            actions.sleepScreen()
            _remaining.value = null
        }
    }
}

/**
 * Stores the layout as one short string and the settings as plain preference values.
 * Singleton, so an edit made in the app shows up in the overlay at once.
 */
@Singleton
class PreferencesLayoutRepository @Inject constructor(
    @ApplicationContext context: Context,
) : LayoutRepository {
    private val prefs = context.getSharedPreferences("control_center_layout", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(PanelLayout.decode(prefs.getString(KEY_LAYOUT, null)))
    override val layout: StateFlow<PanelLayout> = _layout.asStateFlow()

    private val _settings = MutableStateFlow(
        PanelSettings(
            uiScale = prefs.getFloat(KEY_SCALE, 1f),
            blurBehind = prefs.getBoolean(KEY_BLUR, true),
            blurLevel = BlurLevels.clamp(prefs.getInt(KEY_BLUR_LEVEL, 3)),
            blurMethod = BlurMethod.entries.firstOrNull { it.name == prefs.getString(KEY_BLUR_METHOD, null) } ?: BlurMethod.AUTO,
            frost = FrostStyle.entries.firstOrNull { it.name == prefs.getString(KEY_FROST, null) } ?: FrostStyle.SMOKE,
            wallpaper = Wallpaper.entries.firstOrNull { it.name == prefs.getString(KEY_WALLPAPER, null) } ?: Wallpaper.BOXES,
            topOffset = prefs.getInt(KEY_TOP, 48),
            headerGap = prefs.getInt(KEY_GAP, 8),
            invertFocus = prefs.getBoolean(KEY_INVERT, true),
            card = prefs.getBoolean(KEY_CARD, false),
            textScale = prefs.getFloat(KEY_TEXT, 1f),
            notificationTextScale = prefs.getFloat(KEY_NOTICE_TEXT, 0.8f),
            cornerNotices = prefs.getBoolean(KEY_CORNER_NOTICES, false),
            edgeWidth = prefs.getFloat(KEY_EDGE, 1.5f),
            topSize = prefs.getInt(KEY_TOP_SIZE, 44),
            topBorderless = prefs.getBoolean(KEY_TOP_FLAT, false),
            menuIsHome = prefs.getBoolean(KEY_MENU_HOME, true),
            patientCapture = prefs.getBoolean(KEY_PATIENT, false),
            musicStyle = MusicStyle.entries.firstOrNull { it.name == prefs.getString(KEY_MUSIC_STYLE, null) } ?: MusicStyle.COMPACT,
            windowMotion = WindowMotion.entries.firstOrNull { it.name == prefs.getString(KEY_WINDOW_MOTION, null) } ?: WindowMotion.MID_AIR,
            autoInk = prefs.getBoolean(KEY_AUTO_INK, true),
            appSlots = prefs.getString(KEY_APP_SLOTS, null)
                ?.split('|')
                ?.map { it.ifEmpty { null } }
                ?.take(4)
                ?.let { it + List(4 - it.size) { null } }
                ?: List(4) { null },
            soundCues = prefs.getBoolean(KEY_SOUND_CUES, true),
            volumeStep = prefs.getInt(KEY_VOLUME_STEP, 3),
            activityLog = prefs.getBoolean(KEY_ACTIVITY_LOG, false),
            iconStyle = IconStyle.entries.firstOrNull { it.name == prefs.getString(KEY_ICONS, null) } ?: IconStyle.FILLED,
            iconPack = IconPack.entries.firstOrNull { it.name == prefs.getString(KEY_ICON_PACK, null) } ?: IconPack.MATERIAL,
            phosphorWeight = PhosphorWeight.entries.firstOrNull { it.name == prefs.getString(KEY_PHOSPHOR_WEIGHT, null) }
                ?: PhosphorWeight.FILL,
            bluetoothIcon = BluetoothIcon.entries.firstOrNull { it.name == prefs.getString(KEY_BLUETOOTH_ICON, null) }
                ?: BluetoothIcon.STANDARD,
            menuIcon = MenuIcon.entries.firstOrNull { it.name == prefs.getString(KEY_MENU_ICON, null) } ?: MenuIcon.BARS,
            activeHighlight = prefs.getBoolean(KEY_ACTIVE_HIGHLIGHT, true),
            profilePhotoVersion = prefs.getInt(KEY_PHOTO, 0),
            profileColor = prefs.getInt(KEY_COLOR, 0),
        ),
    )
    override val settings: StateFlow<PanelSettings> = _settings.asStateFlow()

    override fun update(transform: (PanelLayout) -> PanelLayout) {
        val next = transform(_layout.value)
        if (next == _layout.value) return
        _layout.value = next
        prefs.edit().putString(KEY_LAYOUT, next.encode()).apply()
    }

    override fun resetLayout() {
        _layout.value = PanelLayout.Default
        prefs.edit().remove(KEY_LAYOUT).apply()
    }

    override fun setUiScale(scale: Float) {
        _settings.value = _settings.value.copy(uiScale = scale)
        prefs.edit().putFloat(KEY_SCALE, scale).apply()
    }

    override fun setBlurBehind(enabled: Boolean) {
        _settings.value = _settings.value.copy(blurBehind = enabled)
        prefs.edit().putBoolean(KEY_BLUR, enabled).apply()
    }

    override fun updateSettings(transform: (PanelSettings) -> PanelSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit()
            .putFloat(KEY_SCALE, next.uiScale)
            .putBoolean(KEY_BLUR, next.blurBehind)
            .putInt(KEY_BLUR_LEVEL, next.blurLevel)
            .putString(KEY_BLUR_METHOD, next.blurMethod.name)
            .putString(KEY_FROST, next.frost.name)
            .putString(KEY_WALLPAPER, next.wallpaper.name)
            .putInt(KEY_TOP, next.topOffset)
            .putInt(KEY_GAP, next.headerGap)
            .putBoolean(KEY_INVERT, next.invertFocus)
            .putBoolean(KEY_CARD, next.card)
            .putFloat(KEY_TEXT, next.textScale)
            .putFloat(KEY_NOTICE_TEXT, next.notificationTextScale)
            .putBoolean(KEY_CORNER_NOTICES, next.cornerNotices)
            .putFloat(KEY_EDGE, next.edgeWidth)
            .putInt(KEY_TOP_SIZE, next.topSize)
            .putBoolean(KEY_TOP_FLAT, next.topBorderless)
            .putBoolean(KEY_MENU_HOME, next.menuIsHome)
            .putString(KEY_ICONS, next.iconStyle.name)
            .putString(KEY_ICON_PACK, next.iconPack.name)
            .putString(KEY_PHOSPHOR_WEIGHT, next.phosphorWeight.name)
            .putString(KEY_BLUETOOTH_ICON, next.bluetoothIcon.name)
            .putString(KEY_MENU_ICON, next.menuIcon.name)
            .putBoolean(KEY_ACTIVE_HIGHLIGHT, next.activeHighlight)
            .putString(KEY_MUSIC_STYLE, next.musicStyle.name)
            .putString(KEY_WINDOW_MOTION, next.windowMotion.name)
            .putBoolean(KEY_AUTO_INK, next.autoInk)
            .putString(KEY_APP_SLOTS, next.appSlots.joinToString("|") { it ?: "" })
            .putBoolean(KEY_SOUND_CUES, next.soundCues)
            .putInt(KEY_VOLUME_STEP, next.volumeStep)
            .putBoolean(KEY_ACTIVITY_LOG, next.activityLog)
            .putBoolean(KEY_PATIENT, next.patientCapture)
            .putInt(KEY_PHOTO, next.profilePhotoVersion)
            .putInt(KEY_COLOR, next.profileColor)
            .apply()
    }

    private companion object {
        const val KEY_BLUR_LEVEL = "blur_level"
        const val KEY_BLUR_METHOD = "blur_method"
        const val KEY_FROST = "frost"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_TOP = "top_offset"
        const val KEY_GAP = "header_gap"
        const val KEY_INVERT = "invert_focus"
        const val KEY_CARD = "card"
        const val KEY_TEXT = "text_scale"
        const val KEY_EDGE = "edge_width"
        const val KEY_TOP_SIZE = "top_size"
        const val KEY_TOP_FLAT = "top_borderless"
        const val KEY_MENU_HOME = "menu_is_home"
        const val KEY_ICONS = "icon_style"
        const val KEY_ICON_PACK = "icon_pack"
        const val KEY_PHOSPHOR_WEIGHT = "phosphor_weight"
        const val KEY_BLUETOOTH_ICON = "bluetooth_icon"
        const val KEY_MENU_ICON = "menu_icon"
        const val KEY_NOTICE_TEXT = "notice_text"
        const val KEY_CORNER_NOTICES = "corner_notices"
        const val KEY_ACTIVE_HIGHLIGHT = "active_highlight"
        const val KEY_MUSIC_STYLE = "music_style"
        const val KEY_WINDOW_MOTION = "window_motion"
        const val KEY_AUTO_INK = "auto_ink"
        const val KEY_APP_SLOTS = "app_slots"
        const val KEY_SOUND_CUES = "sound_cues"
        const val KEY_VOLUME_STEP = "volume_step"
        const val KEY_ACTIVITY_LOG = "activity_log"
        const val KEY_PATIENT = "patient_capture"
        const val KEY_PHOTO = "profile_photo"
        const val KEY_COLOR = "profile_color"
        const val KEY_LAYOUT = "layout"
        const val KEY_SCALE = "ui_scale"
        const val KEY_BLUR = "blur_behind"
    }
}
