# DESIGN — Flashlight

## App: Flashlight · slug `flashlight`

### Elevator pitch
A dead-simple flashlight: one giant button turns the phone's camera LED on and
off. No menus, no settings, no network. The whole app is a single dark screen
built for one fast, memorable gesture — tap, light; tap, dark. It targets
low-RAM Android phones and works fully offline.

### 1. Screens
| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Home (only screen) | Toggle the camera LED | App title, ONE big square button (`toggleButton`), status line (`status`) |

Notes:
- Single screen, portrait-locked (common for flashlight use), phone-first.
- No scrolling needed; content is vertically centered and fits any screen ≥ ~4".
- Low-RAM friendly: plain framework views, no dependencies, no background
  services, `FLAG_KEEP_SCREEN_ON` only while the LED is on.
- Unsupported-flash devices (no `FEATURE_CAMERA_FLASH` / no flash camera): the
  button is disabled and the status line reads "This device has no flash unit".

### 2. Navigation
- Launcher starts `MainActivity` directly; there are no other screens.
- No forward navigation, no tabs/drawer (none needed).
- Back / Home: system default. The LED is force-turned off in `onStop()`, so
  leaving the app (Home, Back, power button) always switches the light off —
  a deliberate safety default.
- Rotation is locked to portrait and handled via `configChanges`, so a rotate
  does not kill the light unexpectedly.

### 3. Visual style
- **Palette** (dark, utilitarian):
  | Role | Hex |
  |------|-----|
  | background (near-black) | `#0B0D11` |
  | surface (off state) | `#232733` |
  | accent / LED & ON state | `#FFC400` |
  | OFF-state border | `#FFC400` (thin amber ring) |
  | primary text | `#FFFFFF` |
  | secondary text / OFF status | `#8A93A5` |
- **Typography**: system font only. Title 18sp medium, small caps, letter-spaced;
  button label 20sp bold; status 14sp.
- **Spacing/layout**: content centered on screen; button 200×200dp with 32dp
  corner radius; 24dp page padding; 16dp gap between title, button, status.
- **Tone**: utilitarian and calm — dark UI, black background, single loud amber
  "LED" control. The off state is dim, the on state is bright (matches its
  function in the dark).
- **Light/dark**: always dark. A flashlight is used in the dark by definition,
  so no light theme and no theme switching.

### 4. Interactions
- **Big button tap** → toggles the camera LED.
  - LED off → amber filled button, label `TAP TO TURN OFF`, status
    `Flashlight is ON` in amber, screen kept on.
  - LED on → dark button with amber ring, label `TAP TO TURN ON`, status
    `Flashlight is OFF` in grey, screen-keep flag cleared.
- **Camera runtime permission** (Android 6+): if the camera permission is not
  yet granted, tapping first requests it; a toast explains why. Granting toggles
  immediately; denying shows a toast and keeps the light off.
- **No flash available**: button disabled + status message; taps are ignored.
- **LED busy** (e.g. camera in use / `CameraAccessException`): short toast with
  the reason; UI stays in the last safe state.
- **App backgrounded / screen off**: LED off in `onStop()` automatically.
- **Accessibility**: button is a real `Button` (≥48dp, far larger), default
  focus traversal, visible label changes; status line is a plain `TextView`
  announced by TalkBack.

### 5. App-icon concept
- **Symbol**: a round "flash" lens with white light rays radiating around it —
  instantly reads as "torch/flash". Matches the app's single-action purpose.
- **Colors**: amber `#FFC400` lens and white rays on a near-black `#0B0D11`
  rounded tile.
- **Background treatment**: solid near-black, amber lens centred, four white
  rays (up/up-left/up-right/diagonal) implying a beam.
- **Rendering**: legacy densities (< API 26) get generated PNGs (48/72/96/
  144/192px); API 26+ uses `mipmap-anydpi-v26/ic_launcher.xml` adaptive icon
  (same artwork as a vector foreground + color background).

## Delivery checklist
- [x] Every screen covered (single screen defined)
- [x] Navigation fully specified (single screen, back/home/stop behaviour)
- [x] Visual style concrete (hex values, sizes)
- [x] Interactions and feedback described (toggle, permission, error, stop)
- [x] App-icon concept concrete and implementable as drawables