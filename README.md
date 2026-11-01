# TV Control Center

An Android TV app that recreates the look and behaviour of the tvOS Control Center panel with an original
"Liquid Glass" material: Kotlin, Jetpack Compose for TV, MVVM + StateFlow, Hilt, single activity, D-pad only.
No Apple logos, SF Symbols or fonts. Icons are Material icons, the font is Inter (SIL OFL, see `FONT_LICENSE_Inter.txt`),
and the animated acrylic background is drawn in code (no image assets).

> **Status.** This source was written without an Android SDK in the authoring environment, so it has **not been compiled
> or run**. Expect a few small compile fixes on first build, and no performance numbers exist yet. Sizes, spacing and
> spring values are estimated from **one static screenshot**. Re-tune `ui/theme/Tokens.kt` against a screen recording.

## Setup

1. Android Studio Ladybug or newer, JDK 17.
2. Open this folder. Gradle sync downloads everything (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, `androidx.tv:tv-material` 1.0.0).
3. Run the `app` configuration on an Android TV emulator or device (API 26+, best on API 33+).
4. Unit tests: `./gradlew testDebugUnitTest`. Compose D-pad tests (need a device or emulator): `./gradlew connectedDebugAndroidTest`.

## Continuous integration

`.github/workflows/android.yml` runs on every push to `main` and every pull request: unit tests, lint, then `assembleDebug`.
The debug APK and the reports are uploaded as run artifacts (Actions tab, open a run, Artifacts section).
To also run the Compose focus tests on an Android TV emulator, use **Actions > Android CI > Run workflow** and tick the option.
Since the source has not been compiled yet, the first CI run is the first real build; fix whatever it reports.

## Using it

| Where | How |
|---|---|
| In this app (demo) | Press **OK** on the background, or **long-press Menu**. |
| Over any app, as an activity | Launch **Open Control Center** (second entry in the launcher row), or run `adb shell am start -a com.example.tvcontrolcenter.action.OPEN`. Assign it to a remote button with any button-remapper app or a remote that can launch apps. Launching it again closes it. |
| Over any app, as an overlay | Enable **Control Center key trigger** under Accessibility settings (button in the demo screen). Then long-press **Menu** or **Settings** anywhere. Keys are listed in `ControlCenterAccessibilityService.TRIGGER_KEYS`. |
| Overlay without accessibility | Tap **Grant overlay permission**, then the same button toggles an overlay window (`OverlayService`, needs "display over other apps"). |

### One grid, fully customisable

All controls live on one 4-column grid inside a single frosted sheet. There is no separate quick-actions column any more.
Every control can be **Small** (1x1), **Wide** (2x1), **Tall** (1x2), **Large** (2x2) or **Full row** (4x1), moved, hidden
and added back. The default shows Power, Wi-Fi, Do Not Disturb, Bluetooth, Sleep Timer, four shortcuts, Now Playing (only while
something plays), Volume, and Settings / Home / Recent apps / Edit. Display, Sound, Captions, Screen saver, Apps, Screenshot and
Notifications are available from the editor.

**Edit Control Center:** open it from the **Edit** control in the panel, or from the demo screen. Pick a control with **OK**; the
buttons on the left set its size, move it earlier or later, or hide it. "Add controls" lists hidden ones. "Panel size" scales
the panel (80 % to 175 %) on top of the automatic screen fit, "Blur behind" toggles the system blur, and "Reset layout" restores
the default. Changes save immediately and every open panel follows.

| Control | What it does | Needs |
|---|---|---|
| Settings, Display, Sound, Captions, Screen saver, Apps | Opens that settings screen (falls back to Settings if the TV build lacks it) | nothing |
| Home | Goes to the launcher | Accessibility service (intent fallback without it) |
| Recent apps, Notifications | Opens the system recents or notification shade | Accessibility service |
| Screenshot | Closes the panel, then takes a screenshot (Android 9+) | Accessibility service |
| Volume, OK | Mutes or unmutes; Left/Right change the volume | nothing |
| Edit | Opens the layout editor | nothing |

Home, Recent apps, Screenshot and Notifications run about 0.6 s after the panel closes, so the panel is not in the picture.

D-pad: arrows move focus by geometry (so any arrangement works), **OK** activates, **Back** closes. On Volume, Left/Right change volume. On Now Playing, Left/Right skip tracks and OK plays or pauses. Focus is trapped inside the panel, and the last focused control is restored on the next open.

### 4K and 50-inch screens

The design tokens are in dp at a 960 x 540 reference. `ProvideUiScale` replaces the density so the panel takes the same share of the
screen however the TV reports its size (a 4K set that reports 1920 dp gets x2, one that reports 960 dp gets x1). Use **Panel size** in the
editor to go bigger or smaller.

## Architecture

```
domain/        pure Kotlin: models, PanelLayout, GridPacker and GridNav (packing + D-pad geometry),
               repository + action interfaces (no Android types)
data/          focus memory, layout and settings (SharedPreferences), sleep timer
system/        Android integrations: AudioManager, settings intents, MediaSessionManager,
               NotificationListener, AccessibilityService trigger, overlay window, window blur
di/            Hilt modules, plus an entry point so the overlay window can build the ViewModel
ui/theme/      design tokens, Inter font, ProvideUiScale (screen-size fit)
ui/glass/      frostedSheet and frostedTile modifiers
ui/background/ AcrylicBackground (moving, low-resolution, cheap)
ui/panel/      ViewModel, GlassTile, tile faces per size, TileGrid, ControlCenterHost
ui/edit/       the layout editor
ui/demo/       demo scene hosting the panel
```

### Frosted glass implementation

The earlier per-tile AGSL refraction shader was removed: it was heavy on TV GPUs, and it could not work over other apps anyway.
Glass is now plain drawing, so it costs almost nothing per frame:

* **Sheet.** One rounded card behind all controls: a dark base for contrast on bright apps, a white veil that fades downwards,
  faint grain, and a rim that is bright on top and faint at the bottom.
* **Tiles.** Same recipe per control, in a smaller dose. "On" controls get brighter glass instead of a coloured icon. Focus lifts
  the tile 4 dp, scales it to 1.07, brightens it and adds a top-left glint and a contact shadow.
* **Real blur where the system allows.** Panel windows ask Android 12+ to blur what is behind them (`FLAG_BLUR_BEHIND`), on devices
  with cross-window blur enabled. That is the only way to see the running app frosted behind the panel; elsewhere the dim is
  stronger and the sheet alone carries the look. Toggle it with "Blur behind" in the editor if it costs frames on your box.
* **Performance.** Focus and open progress are read in draw/graphicsLayer lambdas, so moving focus recomposes nothing. Open/close
  animates per grid row (not per tile) on stiff springs and settles in about a quarter of a second. Nothing animates while idle.
* **Acrylic background.** Five soft colour fields drift on a 90 s loop. They are drawn into an offscreen layer one sixth of the
  screen size and scaled up by the GPU, so a 4K screen costs about as much as 640 x 360, and the position updates 25 times a second.

## Permissions and what degrades

| Permission / service | Used for | Without it |
|---|---|---|
| Accessibility service (user enables) | Open from any app, power menu, screen off | Panel only opens inside this app; Power Off and Lock ask you to enable it |
| `SYSTEM_ALERT_WINDOW` | Overlay window via `OverlayService` | Settings screen opens so you can grant it |
| Notification listener (user enables) | Now Playing via `MediaSessionManager` | Now Playing tile hidden; no crash |
| Notification policy access | Do Not Disturb toggle | Settings screen opens so you can grant it |
| `ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS` | Network and volume | n/a (normal permissions) |

## Known limitations

* **Not device-tested.** The 2 GB-RAM, 60 fps requirement is designed for but unmeasured. Profile with Perfetto on a real box; if the
  blur is costly, switch "Blur behind" off.
* **No refraction of other apps.** Android never lets an app read pixels of other apps. The overlay uses real window blur-behind
  (`FLAG_BLUR_BEHIND`, Android 12+) on devices with cross-window blur enabled, and a stronger dim elsewhere.
* **The activity pauses the app underneath.** `ControlCenterActivity` is a transparent window on top of the running app, so the app gets `onPause`
  and some players stop. The accessibility overlay (long-press Menu with the service enabled) pauses nothing, but needs that service.
* **Blur needs Android 12+ and a device that enables cross-window blur.** Many TV builds do not, so over other apps the glass is frosted by the sheet only.
* **Long-press Menu is not guaranteed.** Many Android TV remotes never deliver Menu, and Home is never delivered. Use
  Settings or remap in `TRIGGER_KEYS`.
* **Power Off** opens the system power dialog (Android 12+ via accessibility) or turns the screen off. Android cannot power a device off.
* **Wi-Fi tile** shows on/off, not the network name: reading the SSID needs location permission, which I avoided.
* **Do Not Disturb** uses interruption filter "none"; it needs the policy-access grant.
* **Volume** is a no-op on fixed-volume HDMI-CEC outputs.
* **Game controllers** opens Bluetooth settings (no dedicated screen exists across TV builds).
* Status is polled every 2 s while the panel is open (simple, permission-free). A broadcast-based source would be tidier.
* The editor sets sizes and order but does not drag. Move is "swap with the next visible control"; the grid fills first-fit, so a small
  control can slot into a gap beside a larger one.
* No Wi-Fi/Bluetooth detail sub-panels, Sleep Timer picker or AirPods-style device pickers yet. The Sleep Timer tile cycles Off, 15, 30, 60, 90 minutes.
* Contrast: white text on the dark-based glass is tuned for AA. Verify over very bright content before shipping.

## tvOS behaviours Android cannot replicate

1. Reading and refracting **live content behind the panel** from other apps (system compositor access only).
2. A **system-level Control Center key** (Apple's Home long-press) and system-owned panel placement above every app.
3. **Real power-off**, **AirPods/Apple device switching**, Home/HomeKit controls and Apple ID user-profile switching.
4. **Per-user** Control Center from the profile avatar and the tvOS status bar integration.
5. **Seamless Siri Remote haptics and Taptic feedback** (n/a on TV; short sound cues are used instead).
6. **System-wide Do Not Disturb semantics** identical to tvOS, and the **Sleep Timer that fades audio** at the OS level.
7. Pixel-identical Liquid Glass: Apple's material is a private compositor effect. This is an independent approximation.

## Next steps

Send a photo of the panel on your TV and tune `PanelDimens`, the glass alphas in `GlassTokens`, and the springs in `Motion` (`Tokens.kt`).
