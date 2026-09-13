# DESIGN — Hello AppFactory

## App: Hello AppFactory · slug hello-appfactory

### Elevator pitch
A minimal, dependency-free Android app that proves the AppFactory pipeline and
serves as the reference template for every future app: two screens, one local
interaction, tiny footprint, comfortable on low-RAM phones.

### 1. Screens
| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Home | Entry + demonstrate a local interaction | Bold heading; "Presses: N" live counter; "Press me" button; "About" button |
| About | Static context for the app | One paragraph of explanation; "Back" button |

Empty states: two static text screens — none needed. Portrait-first, scales to
any phone; fits 480dp-tall screens without scrolling (both layouts are short).

### 2. Navigation
- Start screen: **Home** (launcher activity).
- Home → About: press "About" (startActivity).
- About → Home: press "Back" or system back (finish()).
- No tabs/drawer — unnecessary for a two-screen app.

### 3. Visual style
- **Palette**: primary `#1565C0`, primary-dark `#0D47A1`, accent `#FFB300`,
  screen background `#F5F7FA`, text `#212B36` / secondary `#5A6B7B`.
- **Typography**: system font; 28sp bold heading, 18sp body, 16sp buttons.
- **Spacing/layout**: 24dp screen padding, 16/12/28dp vertical rhythm, 48dp
  touch targets.
- **Tone**: calm, utilitarian, engineer-friendly.
- **Light/dark**: light only (Theme.Material.Light) for v0.1.0.
- Rationale: framework visuals and framework blue/white signal "system app,
  zero bloat" — reinforcing the light-footprint promise.

### 4. Interactions
- Press me: increments counter, TextView updates immediately (state kept in
  activity field; label via `Greeting.counterLabel`).
- About: opens About screen (Intent).
- Back: returns using framework back stack / finish().
- Input validation: none (no input fields).
- Feedback: immediate text updates; a11y labels come from string resources and
  ≥48dp touch targets.

### 5. App-icon concept
- **Symbol**: a white ring (annulus) with a small white centre dot on a blue
  tile — like a crosshair/launch mark: "this thing goes somewhere".
- **Colors**: tile `#1565C0`, ring/dot white (`#FFFFFF`).
- **Background treatment**: solid blue, rounded-square tile.
- **Rendering**: legacy PNGs for API 21–25 (mdpi..xxxhdpi, generated from
  `tools/gen-icons.py`), adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` +
  `drawable/ic_launcher_foreground.xml`) for API 26+.

## Delivery checklist
- [x] Every screen covered
- [x] Navigation fully specified
- [x] Visual style concrete (hex values, sizes)
- [x] Interactions and feedback described
- [x] App-icon concept concrete (implemented as PNG + vector)