# Arcade Night

The build plan (§7) asked for "calm, precise, uncluttered… a well-made physical puzzle
book, not a casual game with gradients and bubble letters". Phases 1–4 built exactly
that: warm paper, near-black ink, one teal accent. It was correct, and it was quiet.

The brief then changed — this should feel like a **game** — so the palette and the
controls did too. This document is what replaced it and why, so the next change is an
argument rather than a repaint.

## The one rule

**One colour per meaning.** Not "more saturation": a small set of hues, each with a job
it never leaves, held to consistently enough that a glance tells you what a thing does
before you read it.

| Role | Dark | Light | Means |
|---|---|---|---|
| `accent` | gold `#FFC145` | burnt orange `#BF3F08` | the accent as **ink**: text, a meter, a thin line |
| `accentFill` | gold `#FFC145` | gold `#FFB020` | the accent as a **fill** carrying dark text: a button face, a selected tab |
| `cellMistake` | coral `#FF6B6B` | `#C2372F` | what costs you something: a wrong square, a spent life, a streak about to break |
| `success` | mint `#3DDC97` | `#12795A` | confirmed and safe: a solved day, a hint in hand, a free action |
| `info` | sky `#5AC8FA` | `#0E6E8F` | being explained: a hint, the line the tutorial is working on |
| `collection` | violet `#C77DFF` | `#6D3BBF` | the hand-drawn pictures, wherever they appear |

You can read the app by colour alone: the last life, the `expert` filter chip and a
mistake cell are the same red; the hint button's count badge, a solved calendar day and
"0 mistakes" are the same green.

## The board stays quiet

Everything above is **chrome**. Inside the grid there are two colours — filled and empty
— plus a muted cross.

A nonogram is *read*, not just looked at. A tinted square competes with the clue beside
it, and a player who miscounts because of decoration blames the game. The colour is spent
on the frame, not the picture.

The filled square is whichever colour is furthest from the board behind it: gold on
night, indigo ink on paper. The rule is "highest contrast available", not "gold" — which
is why the two themes disagree here and nowhere else.

## Type

- **Fredoka** for display — the timer, the streak, screen titles, the picture reveal.
  Its rounded terminals do the game-feel work, and that is precisely what lets the board
  stay disciplined. Without it, colour would have to carry the personality, and colour
  inside the grid is the one thing forbidden above.
- **Outfit** for body — labels, captions, buttons.

Both load from the Play Services font provider rather than being committed as binaries.
A device without Play Services gets the system sans; every size is set in sp against
metrics the fallback also satisfies, so a failed download costs the personality, not the
layout.

## The bevel

`PrimaryButton` draws a strip of the accent's darker shade below its face, so it looks
pressable before it is pressed; on press the face drops onto the bevel and the key
bottoms out. It is the only decorative flourish in the app, it appears once per screen,
and it is what most of the "game" impression actually comes from.

Disabled loses the bevel as well as the colour. A key that still looks pressable and does
nothing is worse than one that plainly is not.

## Ink and fill are two different colours

On night they are the same gold. On paper they cannot be: a colour dark enough to be
readable *as text* on cream is too dark to be a cheerful button, and the first port
shipped exactly that — a muddy brown primary that made the light theme look like a
different, sadder app.

So `accent` stays dark for text and lines, and `accentFill` stays bright for surfaces you
put dark text on. A bright fill has almost no contrast against paper (1.7:1), so anything
wearing it also wears an `accentDeep` edge, which is what gives the control its visible
boundary.

The solid chip is a separate composable (`AccentChip`) rather than a flag on `Chip`,
because the first version let the caller choose the fill and the daily screen chose
`accent` — shipping ink on dark orange at **2.88:1**. The contrast test could not catch
it: the palette pair it checks was fine, only the call site was wrong. So the call site
no longer gets to choose.

## What contrast cost

A saturated palette fails WCAG AA far more easily than a quiet one. The first draft of
the light theme reused the dark theme's gold, which is **1.5:1 on white** — invisible.
The light accents are darkened versions of the same hues, not the same hex values.

`ThemeContrastTest` now checks 26 pairs per theme rather than 13: every meaning colour
against all three surfaces it actually lands on. A colour that reads beautifully on the
page ground and disappears on a card is the specific failure this palette invites.

Two values were moved during the port for exactly that reason:

- the dark satisfied-clue grey failed at 4.21:1 on a raised card → lightened to `#9A90DC`
- the dark major grid line failed at 2.58:1 against an empty cell → lightened to `#9084E0`

Two more were only findable on a device, which is why the port was checked there:

- **The status bar icons were invisible in the light theme.** The system picks their
  colour from `isAppearanceLightStatusBars`, not from what is behind them, so cream paper
  got white icons. Set in `NonogramTheme`.
- **The tutorial's focus band was gold at low alpha**, which over the dark ground is a
  muddy brown — and meant the highlight and the filled squares shared a hue, on the one
  screen whose whole job is telling them apart. It now uses the theme's own `highlight`,
  the same wash the play screen puts under the row your finger is on.

## Where it lives

- `ui/theme/Color.kt` — the only file allowed to name a colour. `ThemePurityTest` fails
  the build on a literal anywhere else.
- `ui/theme/Type.kt` — the two families and the type scale.
- `ui/components/Glyphs.kt` — the icon set, as SVG path data on a 24×24 grid. The same
  paths the design canvas uses, so mockup and app cannot drift.
- `ui/components/GameControls.kt` — buttons, panels, chips, stat tiles, meters, capsules.
  A radius or a border weight is decided once here instead of eight times across screens.

## What was deliberately not done

- **The board cells are still flat rectangles**, not rounded and bevelled like the
  mockup's. At 20×20 that is 400 rounded rects per frame on a device whose empty-redraw
  floor is already ~13 ms (see the performance table in the README). The chrome can
  afford ornament; the hot path cannot.
- **No animation was added to the reveal** beyond what already existed. Every duration in
  the app goes through `Motion`, so reduce-motion applies to all of it — a rule that a
  new flourish is very easy to break. (`ThemePurityTest` now catches the named-argument
  form `tween(durationMillis = …)`, which slipped past the first version of that check.)
