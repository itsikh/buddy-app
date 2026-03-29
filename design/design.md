# Design System: The Tactile Playground

Source: Google Stitch — "Warm Morning" theme
Applied to: `app/src/main/java/com/itsikh/buddy/ui/theme/`

---

## Creative Direction: "The Digital Sandbox"

Every screen is a physical space filled with soft, squishy objects. We break the standard Material grid with **Organic Intentionality** — oversized touch targets, asymmetrical layouts, and depth through color layering. Every tap should feel like pressing a physical button.

---

## Colors

| Role | Hex | Usage |
|------|-----|-------|
| `primary` | `#7B5400` | Actions, app title, progress |
| `primaryContainer` | `#FEB300` | CTA buttons (golden yellow) |
| `onPrimaryContainer` | `#523700` | Text on golden buttons |
| `secondary` | `#006384` | Exploration, stories |
| `secondaryContainer` | `#97DAFF` | Story cards, info surfaces |
| `tertiary` | `#A83206` | Games, energy |
| `tertiaryContainer` | `#FF9473` | Game cards |
| `background` | `#FFF6E1` | App background (warm cream) |
| `surface` | `#FFF6E1` | Same as background |
| `surfaceContainerLowest` | `#FFFFFF` | Elevated cards ("lifted") |
| `surfaceContainerLow` | `#FFF0C4` | Secondary areas |
| `surfaceContainer` | `#FFE796` | Interactive containers |
| `surfaceContainerHigh` | `#FAE18C` | Active/pressed state |
| `surfaceContainerHighest` | `#F5DC81` | Modals, high-priority |
| `onSurface` | `#392E00` | Primary text (warm, not black) |
| `onSurfaceVariant` | `#695B23` | Secondary text |
| `outlineVariant` | `#BFAC6C` | Ghost borders (inputs, focus) |

### Rules
- **No 1px borders** — use background color shifts for separation ("Tonal Sculpting")
- **No pure black** — always use `onSurface` (#392E00) for text
- Primary (#7B5400) = **Action**. Secondary (#006384) = **Exploration**.

---

## Typography

Fonts: **Plus Jakarta Sans** (headlines) + **Be Vietnam Pro** (body/labels)

> Currently using `FontFamily.SansSerif` as fallback.
> To enable: use Android Studio → New → Downloadable Font → search each font name.
> This generates `res/font/` entries and the required `font_certs.xml` automatically.

| Style | Font | Weight | Size |
|-------|------|--------|------|
| Display Large | Plus Jakarta Sans | Black (900) | 57sp |
| Display Small | Plus Jakarta Sans | ExtraBold (800) | 36sp |
| Headline Large | Plus Jakarta Sans | ExtraBold (800) | 32sp |
| Headline Medium | Plus Jakarta Sans | ExtraBold (800) | 28sp |
| Title Large | Plus Jakarta Sans | Bold (700) | 22sp |
| Body Large | Be Vietnam Pro | Normal (400) | 16sp |
| Body Medium | Be Vietnam Pro | Normal (400) | 14sp |
| Label Large | Be Vietnam Pro | Bold (700) | 14sp |
| Label Small | Be Vietnam Pro | Bold (700) | 11sp |

---

## Shapes

"No sharp corners in the sandbox."

| Token | Radius | Used for |
|-------|--------|----------|
| `extraSmall` | 12dp | Chips, badges |
| `small` | 16dp | Text fields |
| `medium` | 20dp | Cards, dialogs |
| `large` | 28dp | Bottom sheets, large cards |
| `extraLarge` | 40dp | Full-screen containers |
| pill | 999dp | Nav items, FAB, XP badge |

---

## Elevation & Depth

**No drop shadows.** Depth is achieved through tonal stacking:

```
surfaceContainerLowest (#FFFFFF)   ← elevated card (feels "lifted")
  on top of
surfaceContainerLow (#FFF0C4)      ← section background
  on top of
background (#FFF6E1)               ← page base
```

For floating elements (Buddy mascot): use `24dp blur, 6% opacity` shadow tinted with warm amber.

---

## Component Rules

### Buttons
- Min height: **64dp**
- Shape: `large` (28dp) or pill
- Primary button: gradient from `primaryContainer` → `primary`, with a `3dp` bottom shadow in `primaryDim` for the 3D "pressable" feel
- On press: `translateY(3dp)`, shadow collapses to `1dp`

### Cards
- No dividers between list items — use spacing (`16dp`) or alternating tints
- Always `medium` (20dp) or `large` (28dp) radius

### Touch Targets
- Minimum **48×48dp** for all interactive elements
- Use **`spacing-6` (24dp)** as default gutter — white space protects small fingers

### Inputs
- Fill: `surfaceContainerLowest` (#FFFFFF)
- Border: `outlineVariant` at **40% opacity on focus only** ("Ghost Border")
- No visible border at rest

---

## RTL

The app UI is in **Hebrew** (RTL). Layouts must support `LayoutDirection.Rtl`.
Use `Arrangement.Start/End` (not `Left/Right`) and `Modifier.padding(start/end)` throughout.
