# DESIGN_TEMPLATE.md — Design-First Template

Every app in the AppFactory MUST start with a `DESIGN.md` covering the five
sections below. Fill every section; do not leave placeholders. Design precedes
all implementation code.

## App: `<name>` · slug `<slug>`

### Elevator pitch
One paragraph: what the app does, for whom, and why it exists.

### 1. Screens
For EVERY screen:
| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Home | ... | ... |
| Detail | ... | ... |

Add any notes on empty states, scroll behavior, and device sizes (phones,
portrait-first, low-RAM friendly).

### 2. Navigation
- Start screen / launcher behavior.
- Flow between screens (list each transition and trigger).
- Back behavior on each screen.
- Any global navigation (tabs, drawer) with justification.

### 3. Visual style
- **Palette**: primary, secondary, accent, background, text (exact hex values).
- **Typography**: heading and body sizes/weights (system font).
- **Spacing/layout**: margins, paddings, corner radii.
- **Tone**: e.g. calm, playful, utilitarian.
- **Light/dark**: whether dark mode is supported and how.
- Rationale: one sentence tying style to the app's purpose.

### 4. Interactions
- Each user-initiated action and its feedback (button press, input field,
  gesture, toast/snackbar, content change).
- Validation rules (e.g. required fields, numeric ranges).
- Error/empty/loading states and messaging.
- Accessibility notes (touch targets ≥48dp, labels).

### 5. App-icon concept
- Symbol (shape/idea) and why it maps to the app.
- Colors used (must match palette).
- Background treatment (solid, gradient, pattern).
- Legacy (<API 26) and adaptive (v26+) rendering approach.

## Delivery checklist
- [ ] Every screen covered
- [ ] Navigation fully specified
- [ ] Visual style concrete (hex values, sizes)
- [ ] Interactions and feedback described
- [ ] App-icon concept concrete and implementable as drawables