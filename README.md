# TV Control Center

An Android TV app that recreates the look and behaviour of the tvOS Control Center panel with an original
"Liquid Glass" material: Kotlin, Jetpack Compose for TV, MVVM + StateFlow, Hilt, single activity, D-pad only.
No Apple logos, SF Symbols or fonts. Icons are Material icons, the font is Inter (SIL OFL, see `FONT_LICENSE_Inter.txt`),
and the wallpaper (a static wall of colourful boxes) is drawn in code. Drop `wallpaper_boxes.jpg` into `res/drawable` to use a photo instead.

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

The panel is a clock with two round buttons (Menu and Profile) on top, then one 4-column grid, like the tvOS panel. Every control can be
**Small** (1x1), **Wide** (2x1), **Tall** (1x2), **Large** (2x2) or **Full row** (4x1), and can be moved, hidden and added back. The default
grid has Power, Wi-Fi, Do Not Disturb, Bluetooth, Sleep Timer, four shortcuts, Now Playing (only while something plays) and Volume.
Settings, Home, Recent apps, Display, Sound, Captions, Screen saver, Apps, Screenshot and Notifications are available from the editor.
The clock follows the TV's 12/24 hour setting. **Menu** opens the editor; **Profile** opens Android settings.
The **Apps** tab pins an app to one of four slots, so it opens in one press from the grid, and can log which screen a control
really opens (`adb logcat -s TCC`).

**Hold OK** on Wi-Fi or Bluetooth for a small window with its status, the network or audio device in use and a row that opens the
system settings; **press OK** on Input for the list of inputs, one row each. The hold is exposed to TalkBack too, so the action is reachable from a screen
reader. A window opens in place, growing from slightly
smaller, and it is as tall as its rows so nothing hides below the fold (long lists scroll). **Window animation** in the editor
picks between that (**Mid air**, the default), growing out of the control it was opened from (**Out of the control**, which
stretches the picture behind it into the control's shape), and no motion (**Off**). Back closes the window the same way, with the
grid already there again, and the next Back closes the panel.

**Edit Control Center:** open it from the **Menu** button in the panel, or from the demo screen. Pick a control with **OK**; the
buttons on the left set its size, move it earlier or later, or hide it. "Add controls" lists hidden ones. Below that:

* **Panel position** lowers the panel from the top edge (six steps).
* **Gap under the top bar** sets the space between the clock row and the controls (0 to 24).
* **Selected control colors** Inverted (focused control turns white, icon and text dark) or Normal.
* **Automatic icon colour** turns icons and text dark when the picture behind a control is light, so white never sits on white. On by default.
* **Key sounds** turns the click on focus and OK on or off. **Volume step** sets how far one Left/Right press moves the volume.
* **Music button** puts Now Playing in the top bar. It then opens a now-playing page (album art, title, progress, rewind / play-pause / forward) instead of acting as a grid control. Back shrinks the card back into that button, with the grid already there again, and the next Back closes the panel. The remote's **Menu** key swaps the two pages both ways, and the music button stays white while its page is open.
* **Window animation** how the Wi-Fi, Bluetooth and Input windows arrive and leave: Mid air (grows in place, the default), Out of the control, or Off.
* **Buttons beside the clock** turns Menu and Profile on or off.
* **Profile photo** picks a picture with the system file picker (square-cropped to 256 px and kept in the app), removes it, or
  picks one of six colours. If the TV has no file picker, use a colour.
* **Blur strength** Off, Light, Medium, Strong, Max.
* **Blur method** Auto, Screenshot (blur only inside the glass) or Whole screen (the whole picture behind the panel is blurred from a screenshot, with no box around the controls; the system window blur is the fallback).
  A line under it says whether screenshot blur is ready.
* **Glass style** Smoke (dark), Milky (light), Clear, Lens (the blur behind the controls is dimmed, so the edge and sheen read
  instead of the bright screen), then Ocean, Amethyst, Ember, Forest, Rose and Midnight.
* **Panel size** scales the panel (80 % to 175 %) on top of the automatic screen fit. "Reset layout" restores the default.

Changes save immediately and every open panel follows.

Glass is now plain drawing, so it costs almost nothing per frame:

* **Sheet.** One rounded card behind all controls: a dark base for contrast on bright apps, a white veil that fades downwards,
  faint grain. It is drawn only when there is no blurred picture, and only behind the grid.
* **Tiles.** Same recipe per control, in a smaller dose. "On" controls get brighter glass instead of a coloured icon. Focus scales
  the tile to 1.06 (no lift), brightens it and adds a top-left glint. Edges are a faint hairline, and the card has no border.
* **Real blur of the app behind (Android 11+).** When the accessibility service is on (inside this app no service is needed: it copies its own window), the panel takes a screenshot of the app
  just before it opens (`BackdropCapture`), shrinks it to about 480 px, box-blurs it on a background thread (a few tens of ms), and
  draws the matching piece inside each control. The app stays sharp around the glass and is blurred only inside it, as in tvOS. The
  panel waits for the picture for at most 350 ms. Protected video (DRM) cannot be captured and falls back to the frosted look below.
  After updating, switch the accessibility service off and on once so Android grants the new screenshot permission.
* **Fallback.** Without a screenshot (older Android, service off, protected content) the panel draws a frosted sheet and darker tiles.
  On Android 12+ with cross-window blur it also asks the system to blur the whole screen behind it (blur method **Whole screen**, or Auto when no screenshot is possible).
* **Performance.** Focus and open progress are read in draw/graphicsLayer lambdas, so moving focus recomposes nothing. Open/close
  animates per grid row (not per tile) on stiff springs and settles in about a quarter of a second. Nothing animates while idle.
* **Wallpaper.** Eight painted styles for the app's own pages (Boxes, Aurora, Sunset, Forest, Waves, Nebula, Grid, Charcoal),
  drawn once per screen size. Nothing animates.
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
  blur is costly, set Blur strength to Off or Light.
* **Blur over other apps needs Android 11+ and the accessibility service.** The service is the only thing that may screenshot other
  apps. Without it the glass is the frosted fallback. There is no refraction, only blur.
* **The activity pauses the app underneath.** `ControlCenterActivity` is a transparent window on top of the running app, so the app gets `onPause`
  and some players stop. The accessibility overlay (long-press Menu with the service enabled) pauses nothing, but needs that service.
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
* Contrast: the ink is white over the dark-based glass and turns dark by itself when the picture behind a control is light (**Automatic icon colour**), which keeps white apps readable. Verify over mixed content before shipping.

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
