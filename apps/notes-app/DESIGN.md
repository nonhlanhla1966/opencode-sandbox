# DESIGN.md — Notes App

## App: Notes · slug `notes-app`

### Elevator pitch
A lightweight, fully offline notes app where the user can create, edit, search
and delete short notes with a clean, modern, distraction-free interface. It
stores everything in a local SQLite database on the device — no account, no
network, no data leaves the phone. Built for low-RAM Android phones and quick
note-taking on the go.

### 1. Screens

| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Home (list) | Browse, search and open notes; start creating new ones | Header ("Notes" + note count), search field, `ListView` of notes (title, one-line snippet, updated date), empty-state message, round "+" add button, "About" link |
| Edit (create/edit) | Compose a new note or edit an existing one | Title `EditText`, multiline body `EditText`, "Delete" button (edit mode only), soft keyboard; auto-saves on back |
| About | Credits and feature summary | App name, version, short description, "Back" button |

- **Empty states**: when no notes exist the list area shows "No notes yet — tap
  + to write one." When a search matches nothing it shows "No matching notes."
- **Scroll behavior**: the list scrolls naturally; long bodies scroll inside the
  editor. Search stays pinned at the top.
- **Device sizes**: phones, portrait-first, single-screen flows; content is
  linear (`LinearLayout`) so it scales to small or narrow screens without
  custom layout logic.

### 2. Navigation
- **Launcher** opens `HomeActivity`. Launcher intent is MAIN/LAUNCHER; no
  splash, no globals.
- Home → Edit: tapping a note row (`edit mode`) or the "+" button
  (`create mode`). Both start `EditActivity`; only create mode leaves the
  header/footer buttons hidden.
- Edit → Home: pressing Back (or the system back key) **auto-saves** the note
  and returns to the (refreshed) list. Pressing "Delete" asks for confirmation,
  then deletes and returns to the list.
- Home → About: tapping "About" opens `AboutActivity`; Back returns to Home.
- **Back behavior**: from Edit and About, system back returns to Home and the
  list is always re-queried from the database, so the user sees their changes.
- **Global navigation**: none needed — two-level flow only; no tabs or drawer.

### 3. Visual style
- **Palette**:
  - Primary: `#4C6EF5` (indigo) — brand color, "+" button, header.
  - Primary dark: `#3D56C9` — status bar.
  - Accent: `#FFC24B` (warm amber) — subtle highlight of the active search
    field edge and focus states.
  - Screen background: `#F5F7FC`; surface (cards/rows/inputs): `#FFFFFF`.
  - Text primary: `#1C2333`; text secondary: `#5B6B7F`; hint text: `#8A94A6`.
  - Danger (delete): `#E5484D`.
- **Typography**: system font (Roboto on Android). Header 20sp (bold), card
  title 16sp (semi-bold), snippet 14sp, search hint 14sp, editor title 18sp,
  editor body 16sp (1.4 line spacing). All measured in `sp`.
- **Spacing/layout**: 16dp page margins, 12dp between cards, 12dp inside cards,
  10dp corner radius on cards and inputs.
- **Tone**: calm, clean, modern; lots of whitespace, flat colors, no shadows
  beyond a light card outline.
- **Light/dark**: light theme only (v1) for a consistent, low-overhead render;
  palette chosen so a future dark mode is a simple swap of `colors.xml`.
- **Rationale**: blue-ink-on-paper feel; the white cards on a pale-blue-grey
  background read like index cards, matching the act of writing notes.

### 4. Interactions
- **"+ " button**: opens a new blank note in the editor. >= 48dp tap target.
- **Note row tap**: opens that note in edit mode, prefilled.
- **Search field**: filters the list live on every keystroke (case-insensitive
  match over title and body). Clearing the box restores the full list.
- **Save / auto-save**: Back always saves. A note needs at least a title or a
  body; saving a completely empty note is ignored with a toast. Returning home
  shows the saved note at the top (newest first).
- **Delete**: visible only when editing an existing note. Shows a confirm
  dialog; confirm deletes and returns to the refreshed list, cancel does
  nothing.
- **Validation rules**: title/body are stored trimmed; a blank title is stored
  as "Untitled".
- **Error/empty states**: empty list, no-search-results, and an "About" view
  are all described above; no other failure modes exist (no network, no
  permissions).
- **Feedback**: every mutation gives immediate visible feedback — the list
  content changes after save/delete, and a toast explains ignored empty saves.
- **Accessibility**: all tap targets >= 48dp; the "+" and "Delete" buttons have
  `contentDescription`s; search has a real label; text contrast meets WCAG AA.

### 5. App-icon concept
- **Symbol**: a white "note page" (rounded rectangle) with a folded bottom-right
  corner, carrying three short horizontal "text lines" in indigo — instantly
  readable as "a note".
- **Colors used**: indigo `#4C6EF5` tile + white `#FFFFFF` page; lines indigo
  `#3D56C9` — all pulled from the app palette.
- **Background treatment**: solid indigo tile with a generous inner margin;
  concentric-ish rounded-rect outline suggests paper.
- **Rendering**: adaptive icon (API 26+) with a solid indigo
  `ic_launcher_background` and the page/lines as a `VectorDrawable` foreground;
  legacy (pre-26) PNGs at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi generated by
  `tools/gen-icons.py` (stdlib-only), matching the adaptive foreground.

## Delivery checklist
- [x] Every screen covered (Home, Edit, About, empty states)
- [x] Navigation fully specified (launcher → home → edit/about, back behavior)
- [x] Visual style concrete (hex values, sizes, spacing, corners)
- [x] Interactions and feedback described (search, save, delete, validation)
- [x] App-icon concept concrete and implementable as drawables