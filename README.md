# TV Control Center

An Android TV app that recreates the look and behaviour of the tvOS Control Center panel with an original
"Liquid Glass" material: Kotlin, Jetpack Compose for TV, MVVM + StateFlow, Hilt, single activity, D-pad only.
No Apple logos, SF Symbols or fonts. Icons are Material icons, the font is Inter (SIL OFL, see `FONT_LICENSE_Inter.txt`),
and the forest wallpaper is generated procedurally.

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
| In this app (demo) | Press **OK** on the wallpaper, or **long-press Menu**. |
| Over any app, as an activity | Launch **Open Control Center** (second entry in the launcher row), or run `adb shell am start -a com.example.tvcontrolcenter.action.OPEN`. Assign it to a remote button with any button-remapper app or a remote that can launch apps. Launching it again closes it. |
| Over any app, as an overlay | Enable **Control Center key trigger** under Accessibility settings (button in the demo screen). Then long-press **Menu** or **Settings** anywhere. Keys are listed in `ControlCenterAccessibilityService.TRIGGER_KEYS`. |
| Overlay without accessibility | Tap **Grant overlay permission**, then the same button toggles an overlay window (`OverlayService`, needs "display over other apps"). |

### Quick actions

The panel has two parts. The **main cluster** on the right is the tvOS-style layout. The **quick actions** column on its left is a
Google TV-style grid of labelled shortcuts. From the main cluster, press Left on Power, Bluetooth or Game controllers to enter it.

| Quick action | What it does | Needs |
|---|---|---|
| Settings, Display, Sound, Captions, Screen saver, Apps | Opens that settings screen (falls back to Settings if the TV build lacks it) | nothing |
| Home | Goes to the launcher | Accessibility service (intent fallback without it) |
| Recent apps, Notifications | Opens the system recents or notification shade | Accessibility service |
| Screenshot | Closes the panel, then takes a screenshot (Android 9+) | Accessibility service |
| Volume tile, OK | Mutes or unmutes | nothing |

Home, Recent apps, Screenshot and Notifications run about 0.6 s after the panel closes, so the panel is not in the picture.

D-pad: arrows move focus, **OK** activates, **Back** closes. On the Volume tile, Left/Right change volume. On Now Playing, Left/Right skip tracks and OK plays or pauses. Focus is trapped inside the panel, and the last focused tile is restored on the next open.

## Architecture

```
domain/        pure Kotlin: models, repository + action interfaces (no Android types)
data/          focus memory (SharedPreferences), sleep timer
system/        Android integrations: AudioManager, settings intents, MediaSessionManager,
               NotificationListener, AccessibilityService trigger, overlay window
di/            Hilt modules, plus an entry point so the overlay window can build the ViewModel
ui/theme/      design tokens (sizes, springs, glass values) and Inter font
ui/glass/      GlassBackdrop, AGSL shader, painters and the liquidGlass modifier
ui/panel/      ViewModel, explicit FocusGraph, GlassTile, ControlCenterHost (open/close choreography)
ui/demo/       demo scene hosting the panel
```

### Liquid Glass implementation

* **API 33+ (AGSL).** `GlassShader.kt` draws each tile as a rounded-rect signed-distance field. Near the rim it bends the
  sampling position along the surface normal (lens refraction), splits R/G/B along the normal (chromatic fringing), applies an
  adaptive tint, a 1 px rim highlight and a specular sheen that travels round the edge while focused.
* **API 31-32.** A crop of the pre-blurred wallpaper plus a gradient highlight and gradient rim.
* **Below 31.** A tinted translucent gradient.
* **Backdrop.** The wallpaper is blurred **once** at load into a 480x270 bitmap (CPU box blur), and every tile samples it
  directly. That is the "cache the blur" requirement, and it means no per-frame blur and no screen capture. I did not use
  Haze: it blurs live content but cannot feed a refraction shader, and the wallpaper is a known image here.
* **Adaptive tint.** Luminance is precomputed per blurred pixel. Tiles over bright content get dark glass, and vice versa.
* **Focus.** The focused tile lifts 6 dp, scales to 1.08, becomes thinner and brighter, and its sheen starts moving. The
  sheen runs only on the focused tile and is throttled to ~30 updates/s when `isLowRamDevice`. Idle panels redraw nothing.
  Animated values are read in draw/graphicsLayer lambdas, so focus changes never recompose.
* **Open/close.** The cluster grows from the top-right corner on a spring while tiles settle in a stagger; closing reverses
  it so the tiles merge into one blob and shrink into the corner. Tokens: `Motion` in `Tokens.kt`.
* **Parallax.** The wallpaper is drawn 8 % oversize and drifts opposite the focused tile (`parallaxVector`).

## Permissions and what degrades

| Permission / service | Used for | Without it |
|---|---|---|
| Accessibility service (user enables) | Open from any app, power menu, screen off | Panel only opens inside this app; Power Off and Lock ask you to enable it |
| `SYSTEM_ALERT_WINDOW` | Overlay window via `OverlayService` | Settings screen opens so you can grant it |
| Notification listener (user enables) | Now Playing via `MediaSessionManager` | Now Playing tile hidden; no crash |
| Notification policy access | Do Not Disturb toggle | Settings screen opens so you can grant it |
| `ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS` | Network and volume | n/a (normal permissions) |

## Known limitations

* **Not compiled or device-tested** (see Status). The 2 GB-RAM, 60 fps requirement is designed for but unmeasured. Profile with
  Perfetto on a real box and adjust `GlassTokens.lensStrengthPx` and the tile count of shader draws if needed.
* **Overlay over other apps has no refraction.** Android never lets an app read pixels of other apps, so the AGSL path works only
  where the backdrop is this app's own image. The overlay uses real window blur-behind (`FLAG_BLUR_BEHIND`, Android 12+) on
  devices with cross-window blur enabled, and the tinted fallback elsewhere.
* **The activity pauses the app underneath.** `ControlCenterActivity` is a transparent window on top of the running app, so the app gets `onPause`
  and some players stop. The accessibility overlay (long-press Menu with the service enabled) pauses nothing, but needs that service.
* **No wallpaper behind the glass in the activity or overlay.** Glass assumes bright content and uses dark tint so white text stays readable. Real blur of
  the app underneath needs Android 12+ and a device that enables cross-window blur.
* **Long-press Menu is not guaranteed.** Many Android TV remotes never deliver Menu, and Home is never delivered. Use
  Settings or remap in `TRIGGER_KEYS`.
* **Power Off** opens the system power dialog (Android 12+ via accessibility) or turns the screen off. Android cannot power a device off.
* **Wi-Fi tile** shows on/off, not the network name: reading the SSID needs location permission, which I avoided.
* **Do Not Disturb** uses interruption filter "none"; it needs the policy-access grant.
* **Volume** is a no-op on fixed-volume HDMI-CEC outputs.
* **Game controllers** opens Bluetooth settings (no dedicated screen exists across TV builds).
* **Tile positions during the open animation** are sampled before the scale animation settles, so refraction can be a few px off for ~300 ms.
* Status is polled every 2 s while the panel is open (simple, permission-free). A broadcast-based source would be tidier.
* No Wi-Fi/Bluetooth detail sub-panels, Sleep Timer picker or AirPods-style device pickers yet. The Sleep Timer tile cycles Off, 15, 30, 60, 90 minutes.
* Contrast: white text on glass is tuned for AA over the bundled wallpaper. Verify over bright content before shipping.

## tvOS behaviours Android cannot replicate

1. Reading and refracting **live content behind the panel** from other apps (system compositor access only).
2. A **system-level Control Center key** (Apple's Home long-press) and system-owned panel placement above every app.
3. **Real power-off**, **AirPods/Apple device switching**, Home/HomeKit controls and Apple ID user-profile switching.
4. **Per-user** Control Center from the profile avatar and the tvOS status bar integration.
5. **Seamless Siri Remote haptics and Taptic feedback** (n/a on TV; short sound cues are used instead).
6. **System-wide Do Not Disturb semantics** identical to tvOS, and the **Sleep Timer that fades audio** at the OS level.
7. Pixel-identical Liquid Glass: Apple's material is a private compositor effect. This is an independent approximation.

## Next steps

Send the screen recording and a focused-state screenshot, and tune: tile sizes, rim and tint values, spring stiffness, and the
open/close morph timing in `Tokens.kt`.
