# Handoff: TV Control Center (Android TV, tvOS-style panel)

_Written 2026-11-03. Supersedes the older handoff, which described a repo state (zips, `apply.sh`,
`BoxesBackground.kt`, `ui/background/AppBackground.kt`) that no longer exists. Everything below was
checked against the repo at `fd383b4` plus the uncommitted work described in "Just changed"._

## What this is

An Android TV app that recreates the tvOS Control Center: Kotlin, Compose for TV, MVVM + StateFlow,
Hilt, one activity, D-pad only, minSdk 26 (compileSdk/targetSdk 35). It must stay smooth on a weak
2 GB box and scale to 4K. Package `com.example.tvcontrolcenter`.

The owner is GitHub user **DantherXD**, repo **`DantherXD/tv-control-center-copy`**, branch **`main`**.
They test on a real Android TV (Android 14) and report back in messages, sometimes with adb/logcat.
They install the CI release APK with `adb install -r` and pre-compile it with
`adb shell cmd package compile -m speed -f com.example.tvcontrolcenter`.

## How you work here

- Commit straight to `main`, short imperative subject, Codebuff footer. Repo-local identity is
  already set: `DantherXD <citymujrm@gmail.com>`.
- **The owner's standing instruction: after pushing, do not wait for CI.** Push with `cloud_git`
  (`operation: push`, `branch: main`); it sends the current HEAD without force.
- The cloud worktree (`/workspace/chats/<id>`, branch `freebuff/<id>`) starts from `main`. `gh` is
  **not authenticated** in the cloud sandbox, and there is no Android SDK and no sudo, so:
  **nothing compiles locally and CI results cannot be read from here.** CI is the only compiler.
  Say so plainly in the report instead of claiming a build passed.

## CI

`.github/workflows/android.yml`, on push to `main`, PRs, and manual dispatch:

1. `assembleRelease` (optimised; signed with the committed fixed key `app/debug.keystore`) →
   artifact `tv-control-center-release-apk`. APK first so a failing test never blocks installing.
2. `testDebugUnitTest` (note: unit tests still build the debug variant).
3. No lint, no debug APK. A failing step dumps `e:` / Hilt errors into the job summary and uploads
   `build.log` + test reports as artifact `reports`.
4. A separate `ui-tests` job (Android TV emulator, `connectedDebugAndroidTest`) runs only on manual
   dispatch with `run_ui_tests` ticked, and is slow (~45 min cap).

Error output looks like `e: file:///home/runner/work/.../MusicPage.kt:85:50 Unresolved reference…`.

## Source map (`app/src/main/java/com/example/tvcontrolcenter/`)

**Entry / DI**
- `MainActivity.kt`: home page or editor, captures its own window for blur before opening the panel.
- `ControlCenterActivity.kt`: transparent activity that opens the panel over any app.
- `TvControlCenterApp.kt`: Application / Hilt root.
- `di/AppModule.kt`: bindings.

**Domain / data**
- `domain/model/Models.kt`: `TileId` (Power, Wi-Fi, DND, Bluetooth, Sleep Timer, Gamepad,
  Accessibility, Lock, Search, Now Playing, Volume, the Google-TV quick actions, Home, Recents,
  Screenshot, Notifications, Input, Caffeine, Edit, Profile), `SystemStatus`, `NowPlaying`
  (artwork + duration/position/speed), `TvInputItem`, `Capability`, `ActionResult`, `TileState`.
- `domain/model/Layout.kt`: `TileSize` (Small/Wide/Tall/Large/Full), `ControlItem`, `PanelLayout`,
  `GridPacker`, `GridNav`, `MAX_PINNED = 3`, `PanelSettings`, the `*Steps` lists, `BlurLevels`,
  and enums `BlurMethod`, `FrostStyle`, `Wallpaper`, `IconStyle`, `IconPack`, `MusicStyle`.
- `domain/repository/Contracts.kt`: interfaces, including `SystemActions` and `SleepTimerRepository`.
- `data/DataImpl.kt`: SharedPreferences-backed layout/settings (`PanelSettings` is read field by
  field, so a changed data-class default only reaches users who never touched that setting) plus
  `SleepTimerRepositoryImpl` (cycles 0/15/30/60/90 min presets).
- `data/ProfilePhoto.kt`: save/load/clear the profile picture (256 px, in app storage).

**System**
- `system/media/NowPlaying.kt`: media sessions via the notification listener; artwork, position.
- `system/CapabilityIntents.kt`: settings screens for the permissions/services.
- `system/actions/AndroidSystemActions.kt`: real system actions; screenshot via the accessibility
  service; caffeine; Wi-Fi/Bluetooth state; the Bluetooth "Remotes & accessories" launch chain;
  natural-sorted TV inputs.
- `system/actions/AndroidSystemStatusSource.kt`: polls status every 2 s while the panel is open.
- `system/actions/Caffeine.kt`: keeps the screen on with an invisible 1 px accessibility overlay.
- `system/backdrop/BackdropCapture.kt`: screenshot or window capture, then blur; patient mode;
  `screenshotFull()`; `blurBitmap()` (consumes its source).
- `system/backdrop/BoxBlur.kt`, `system/backdrop/ScreenshotSaver.kt`.
- `system/trigger/ControlCenterAccessibilityService.kt`: trigger keys + `takeScreenshot`.
- `system/overlay/OverlayController.kt`, `system/overlay/OverlayService.kt`, `system/WindowBackdrop.kt`.

**UI**
- `ui/theme/Tokens.kt`: `PanelDimens`, `GlassTokens`, `Motion`, type styles, `headerFor()`,
  `musicHeightFor()`.
- `ui/theme/UiScale.kt`: 4K density override, `ProvideTextScale`.
- `ui/glass/FrostedGlass.kt`: sheet/tile drawing, LENS crops, edge width, invert/flat/lite modes,
  `glassInk` (contrast-aware ink),
  composition locals `LocalInvertFocus`, `LocalLiteGlass`, `LocalOverBlur`, `LocalTileInk`,
  `LocalEdgeWidth`, `LocalFrostStyle`.
- `ui/glass/Backdrop.kt`: blurred-screen holder plus its `LumaMap` brightness grid, which is what
  the ink contrast reads.
- `ui/background/AppWallpaper.kt`: the eight painted wallpapers for the app's own pages.
- `ui/panel/ControlCenterViewModel.kt`: UI state, tile activation, long press, caffeine,
  Wi-Fi/Bluetooth/input helpers, music page request.
- `ui/panel/ControlCenterPanel.kt`: host; open/close animation, backdrop + scrim, `page`,
  `closingSub`, `closingMusic`, `returnTo`, focus restore, Back handling.
- `ui/panel/PanelGrid.kt`: `PanelContent` (header, grid, page switching, `gridLayer`),
  `TileGrid`, `PanelTile`, focus properties, `span()`, `gridHeight()`.
- `ui/panel/MusicPage.kt`: `PanelPage` enum, `MusicFocus`, `MusicOrigin`, `cardGlass()`,
  `MusicCard` (Compact 150 dp / Big 388 dp), `popIn()`, `shrinkInto()`, transport buttons.
- `ui/panel/SubPages.kt`: Wi-Fi/Bluetooth/Input windows, `SubPageCard`, `windowMotion`, `Start`,
  `SubRow`/`SubRow.Info`/`SubRow.Action`, row builders, `subTileOf`.
- `ui/panel/GlassTile.kt`, `TileCatalog.kt` (icons; Edit uses `res/drawable/ic_menu_toggles.xml`),
  `TileFaces.kt`, `ProfileAvatar.kt`, `IconStyles.kt` (Material Filled/Outlined/Rounded table),
  `PhosphorIcons.kt` (Phosphor paths as `ImageVector`s, MIT, `PHOSPHOR_LICENSE.txt`),
  `LivePanel.kt` (the demo/editor preview, reuses `PanelContent` with defaults).
- `ui/edit/EditScreen.kt`: tabbed editor (Controls, Look, Top bar, Blur) with one-row steppers and a
  live preview, incl. the "Top bar button size" stepper.
- `ui/demo/DemoScreen.kt`: home page with a live preview, setup checklist, Open/Edit buttons.

**Tests** (`app/src/test`, `app/src/androidTest`, `app/src/sharedTest`): `LayoutTest` (every TileId in
the default layout, fixed default positions), `BoxBlurTest`, `ControlCenterViewModelTest`,
`SleepTimerRepositoryTest`, `fakes/Fakes.kt`, and `PanelFocusNavigationTest` (Compose, emulator only).

## Current feature state

**Grid and top bar.** Clock plus round Menu / Profile buttons (Now Playing optional, `MAX_PINNED = 3`),
then a 4-column grid of Small/Wide/Tall/Large/Full controls. Round buttons are `topSize` dp
(**default 44**, steps 32–48); the header row is `maxOf(44 dp, topSize)` so nothing shifts at the
default. Menu is the main page (`menuIsHome`), so the music button only opens the music page — pressing it
again from inside that page opens the app that is playing — and Menu still opens the editor if
Profile is hidden (can't lock yourself out).

**Small windows (Wi-Fi, Bluetooth, Input).** `PanelSettings.windowMotion` picks how they arrive and
leave. **MID_AIR** (the default) grows from 94 % in the window's own place and fades out where it
stands, so the backdrop inside it is never stretched into a tile's shape. **FROM_CONTROL** is the
old behaviour — first frame is the tile's rect, non-uniform scale, translated onto the tile — and it
does stretch. **OFF** draws the window at once and finishes a close immediately. Open springs
0.88 / 520, close 0.9 / 700. Height is the sum of the rows (min `subPageHeight` 214, max
`subPageMaxHeight` 320, scroll past the cap). The host keeps `page` until the card has landed
(`closingSub` → `closeSub`), the grid is composed underneath for the whole close, and rows ignore
presses while closing. Focus is handed back to the opening control as the close **starts**
(`LaunchedEffect(closingSub)`), so it moves once instead of wandering over the grid when the card
unmounts. Back during the shrink closes the panel, so Back-Back still dismisses. While a control has a window behind it, `GlassTile` exposes the hold as a semantics `onLongClick`, so
the action is reachable with TalkBack.

**Music page.** Opened from the Now Playing top-bar button when `menuIsHome` is on, and from the
remote's **Menu key**, which swaps the grid and the music page both ways (Menu on a sub window goes
back to the grid). Opens with `popIn()` (fade + 0.9→1 scale, settles from -14 dp, 0.78 / 380).
**Back shrinks the card into that top-bar button** (`closingMusic` → `closeMusic`), with `MusicOrigin`
computed in `PanelGrid` from `topSize`, `header`, `gap` and the button's index; the grid is
underneath and focus is handed to the grid's top-left control as the close starts, so it moves once
and lands where a page starts. The button is drawn **solid white while its page is open**
(`GlassTile(solid = …)`), until the page hands back to the grid. The page opens on its **play
button** (`LaunchedEffect(page)`), and **OK on the music button opens the app that is playing**
(`onOpenNowPlayingApp` → `openApp(nowPlaying.packageName)`), which is what that button leads to from
there; the on-screen **Menu button** pressed as home still leaves instantly (it would otherwise
steal focus from Menu). The bar stays focusable on the music page, so Up from the card reaches the
music button and Left/Right move between the bar buttons (`all = if (music) pinned else navigation`),
while a small window takes the bar out of focus (`focusable = !sub && !musicClosing`).

**Icons.** Every icon in the panel, the music page, the windows and the editor is drawn by
`TileIcon`/`FadingIcon`, which ask `IconStyles` for the Material twin and, when `IconPack.PHOSPHOR`
is on, `PhosphorIcons` first (the Material `ImageVector` is the key; an icon the pack lacks falls
back to Material). Phosphor is stored as path strings and built into `ImageVector`s on first use, so
the pack adds no drawables. All six of its weights are bundled — Thin, Light, Regular, Bold, Fill and
Duotone, `PanelSettings.phosphorWeight` — and the weight replaces Icon style while the pack is on;
the cache is keyed by name and weight, so only the weight in use is ever built. Duotone ships two
paths, the shape at 0.2 alpha under the outline, which is why a path is a `Shape(d, alpha)`.
Bluetooth has two possible sources (`PanelSettings.bluetoothIcon`, Bluetooth mark or headphones), so
`lookOf` holds the mark and `lookIcon` swaps in the headphones; both are in the Phosphor twin table.
The Menu button is the one control drawn from a drawable, so `lookIcon` hands it `PhosphorIcons.menu`
itself. The pack is a setting (`PanelSettings.iconPack`, "Icon pack") and is off by default; the
weight row only appears once Phosphor is chosen.

**Starting focus.** The panel opens on the grid's top-left control (`firstGridTile()`: the lowest row
then column below `GridPacker.TOP_ROW`), never on whichever button the bar composed first; the music
page opens on play, coming back from it lands on that same top-left control, and only a closed small
window hands focus back to the control that opened it.

**Glass and blur.** Ten `FrostStyle`s (Smoke, Milky, Clear, Lens, Ocean, Amethyst, Ember, Forest,
Rose, Midnight). Blur method AUTO (screenshot, window blur fallback), SCREENSHOT (blur only inside the
glass), WINDOW (whole screen). Patient capture (1.2 s, 3 tries) for video. Whole-screen mode draws the
blurred screenshot full screen with no box. Fallback when nothing can blur: frosted sheet/card.
Customisable: blur strength, frost style, edge width, invert focus, card on/off, text scale, panel
size, position, header gap, icon style and pack, music style, wallpapers (eight, app pages only),
profile photo/colour, automatic icon colour, icon pack and its six weights, the Bluetooth icon
(Bluetooth mark or headphones) and the active-control highlight. The glass edge is set from none to
5 dp in fine steps (`EdgeWidthSteps`), and whole dp shows without a decimal.

**Active highlight.** A control whose action is on asks for brighter glass (`Face.active` →
`GlassTile(active = …)`, `GlassTokens.tileActiveBoost`): Wi-Fi, Bluetooth, Do Not Disturb, the sleep
timer, the screen hold and now playing while music plays. `faceOf` also drops the bright tint when
`PanelSettings.activeHighlight` is off, and that is the "Highlight active controls" row in the editor.
Top-bar buttons keep their own rule (white while their page is open), which is not this setting.

**Lock.** `sleepScreen()` starts `com.android.tv.settings/.partnercustomizer.power.PictureOffActivity`
first — the TV settings app's own picture-off screen — and only falls back to the accessibility
service's `lockScreen()` when that activity is missing. Power Off's fallback uses the same path.

**Ink contrast.** The glass assumes the screen behind it is dark, so white icons over a white app had
about 1.5:1 contrast and disappeared. `BackdropCapture` now builds a 32x18 `LumaMap` from the blurred
pixels on the blur thread, each control asks `Backdrop.brightness()` for its own rectangle, and
`glassInk(style, brightness)` picks white or `inkDark` by whichever actually has contrast on that
style's glass (white stays until it drops under 3:1). The clock uses the same rule; the sheet, the
sub-window rows and the editor buttons are still plain white ink. Setting: `PanelSettings.autoInk`
("Automatic icon colour", on by default, off restores the old all-white look).

**Other.** Sleep Timer tile cycles the presets; Caffeine keeps the screen on (hidden by default);
Screenshot tile captures through the accessibility service; overlay + accessibility trigger paths for
opening the panel; demo/home page with a live preview.

**App slots.** Four grid tiles (`TileId.APP_1..APP_4`, list `AppSlots`) each hold a package name in
`PanelSettings.appSlots` (stored as `a|b|c|d` under `app_slots`), assigned in the editor's new **Apps**
tab from `SystemActions.installedApps()` (leanback launcher query, `launcher` fallback). The tile draws
the app's own icon and label through `PackageManager` (`appArt`/`appLabelOf` in `TileFaces.kt`) and OK
starts its launch intent. Four slots kept the identity model untouched: each slot is its own `TileId`,
so layout encoding, focus requesters and `key(p.id)` keep working — adding unlimited tiles would need a
new identity type instead.

**Activity capture.** `ControlCenterAccessibilityService.onAccessibilityEvent` writes
`package/activity` to logcat tag `TCC` on every window change while `PanelSettings.activityLog` is on;
the service reads that preference straight from the layout prefs file because a service cannot use
injection. This replaces the manual `adb shell dumpsys activity activities | grep -E
"mResumedActivity|topResumedActivity"` round trip: press the control, then `adb logcat -s TCC`. The
toggle lives in the Apps tab.

Key sounds and the volume step are settings too (`soundCues` through `LocalSoundCues`, `volumeStep` in
steps 1/2/3/5/10). Handy defaults to know: both are on/3 %, and `PanelSettings` reads each key on its
own, so a new default only reaches installs that never touched it.

## Deliberate decisions (don't "fix" these)

- Release APK only in CI, tests after the upload.
- Editor is one row per setting (OK next, Left previous), grouped in tabs, because per-tile rows were
  slow on the TV.
- Whole-screen blur draws the screenshot with no box; system window blur is only a fallback.
- The panelless frosted fallback exists on purpose (many TVs can't blur).
- Palette-only icons (no SF Symbols: Apple licence). Inter is bundled (OFL, `FONT_LICENSE_Inter.txt`).
- A key release only counts on the control where the press started.
- Wi-Fi/Bluetooth on/off toggles were removed: Android blocks apps from switching those radios.
- `PanelSettings` values persist individually, so bumping a default is safe for existing installs.
- Sub windows default to MID_AIR: FROM_CONTROL stretches the picture inside the window into the
  tile's shape while it opens and closes, which is what the owner objected to. Keep all three
  options; MID_AIR is the one that never stretches.

## Traps that have cost builds

- **A duplicate `import` is a compile error**, and a missing import is too. After editing, grep the
  import block: `grep "^import" file.kt | sort -k2 | uniq -d -f1`.
- `FocusRequester.Cancel` needs `@OptIn(ExperimentalComposeUiApi::class)`.
- A parameter named `onClick` collides with the semantics `onClick(label)`; use `activate`.
- `Dp` arithmetic: `Dp * Int`, `Dp / Int`, `Dp / Dp` exist, but a `graphicsLayer`'s `size` may not be
  the node's final size if `size()` comes later in the chain — pass dimensions explicitly instead.
- An `if` expression inside an arithmetic expression needs parens to stay readable:
  `(p * (if (closing) 1.4f else 4f))`.
- Tool-based edits have silently mangled quoting before; always re-read the edited region.

## Open items

1. **Verify the recent pushes in CI.** Since `fd383b4` nothing has been compiled: closing animations,
   the window-motion setting, contrast-aware ink, app slots with the Apps tab, activity capture, and the
   focusability gating. Most likely failure points: the two exhaustive `when`s over `TileId`
   (`TileCatalog`, `ControlCenterViewModel.onTileActivated`) after the four APP ids were added, the
   smart casts in `TileFaces`/`GlassTile`, the `app_slots` string round-trip in `DataImpl`, and the
   new `EditTab.APPS` branch.
2. **Music page: transport presses still work during the ~250 ms shrink.** The small windows got a
   `NoActivate` guard for their rows; the transport buttons do not have one yet.
3. **"A lot of tiles have no action"** — the owner reported it without naming them. Ask which. Then:
   `adb shell am start -a android.settings.<ACTION>`, followed by
   `adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"`, and fix
   the launch chains in `AndroidSystemActions.kt`.
4. **Bluetooth page** may not open on their TV; the settings-component names there are guesses.
5. **README is partly stale.** The status banner still says "not compiled or run" and the CI section
   still claims "unit tests, lint, then assembleDebug". The feature sections (windows, music page,
   glass, wallpapers, size steps) have been kept current.
6. Housekeeping: tests for Caffeine, Input, `naturalCompare`, and the newer settings defaults.
7. Still unmeasured: frame time on a 2 GB box. No performance claim should be made without a
   measurement.
