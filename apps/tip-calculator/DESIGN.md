# DESIGN — Tip Calculator

## App: Tip Calculator · slug tip-calculator

### Elevator pitch
A fast, offline squash-the-bill helper for people splitting a restaurant or bar
tab. Enter the bill, pick a tip percentage, split across any number of
diners, and immediately see the tip, the total, and each person's share.
Zero dependencies, tiny footprint, usable on the oldest low-RAM phones.

### 1. Screens
| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Calculator (Home) | Compute tip + split in a single glance | Bill amount input (EditText, decimal keyboard); tip-rate row of 4 preset chips (12% / 15% / 18% / 22%); people stepper (− / 1 / +); results panel with Tip, Total, and Per person; "About" button |
| About | Explain app purpose + how rounding behaves | Short paragraph; note that per-person is rounded to cents; "Back" button |

Empty states: none needed — results always render with sensible zero values
until the bill is entered. Portrait-first, single scrollable column; fits a
480dp-tall screen without scrolling. Phone-only, low-RAM friendly.

### 2. Navigation
- Start screen: **Calculator** (launcher activity).
- Calculator → About: press "About" (startActivity).
- About → Calculator: press "Back" or system back (finish()).
- No tabs/drawer: one task, two screens, back stack is trivial.

### 3. Visual style
- **Palette**: primary `#2E7D32` (bill-green, money), primary-dark `#1B5E20`,
  accent `#FFB300` (tip-gold), screen background `#F5F7FA`, text
  `#212B36` / secondary `#5A6B7B`, error `#C62828`.
- **Typography**: system font; 26sp bold screen title, 20sp bold result
  numbers, 16sp labels/buttons.
- **Spacing/layout**: 24dp screen padding, 12/16dp vertical rhythm, 48dp touch
  targets, preset tip chips 40dp tall with 8dp gutter.
- **Tone**: calm, green-money, no-nonsense; numbers are the hero.
- **Light/dark**: light only (Theme.Material.Light) for v0.1.0.
- Rationale: greens signal "money, safe, settled" — matching the app's
  purpose of quickly settling a shared bill with confidence.

### 4. Interactions
- Bill input: decimal keyboard (`numberDecimal`); typing anywhere recomputes
  results immediately (TextWatcher on the field).
- Tip-rate chips: tapping a rate selects it (filled background) and recomputes;
  selection is the previously picked value or 18% by default.
- People stepper: "−" decrements (floor 1), "+" increments (cap 50; disabled at
  the bounds); count shows between the buttons.
- All results recompute on every change: **Tip**, **Total**, and **Per person**
  (total ÷ people, rounded to cents).
- Validation: empty/invalid bill → parsed to 0.00; results show `$0.00`. A
  blank input never errors or blocks — results simply stay at zero.
- Feedback: results update instantly, currency-formatted. Touch targets ≥48dp;
  buttons and chips expose content descriptions via string resources.
- Accessibility: consistent 48dp minimum targets, label text on every control,
  large 20sp numerals for readability.

### 5. App-icon concept
- **Symbol**: a white dollar-bill tag / "banknote" with a bold stacked "T" for
  tip, centred on a green tile — instantly reads "money + tip".
- **Colors**: tile `#2E7D32`, symbol white (`#FFFFFF`) — matches palette.
- **Background treatment**: solid green rounded-square tile (no gradient, flat
  for legibility at 48px).
- **Rendering**: legacy PNGs for API 21–25 (mdpi..xxxhdpi, generated from the
  same green-tile-plus-white-symbol art as the adaptive foreground), adaptive
  icon (`mipmap-anydpi-v26/ic_launcher.xml` +
  `drawable/ic_launcher_foreground.xml`) for API 26+.

## Delivery checklist
- [x] Every screen covered
- [x] Navigation fully specified
- [x] Visual style concrete (hex values, sizes)
- [x] Interactions and feedback described
- [x] App-icon concept concrete and implementable as drawables