# Play Store listing copy

Build plan §10: "Front-load the searched keywords naturally: nonogram, picross, griddler,
logic puzzle, hanjie. Write for humans; keyword-stuffed descriptions read badly and don't
help much."

So the keywords appear where they would anyway. The copy is built on the three things a
competitor cannot say, in the order they matter:

1. **No puzzle ever requires a guess, and that is checked rather than promised.** Every
   shipped grid passes a solver that finishes it by deduction, and a search that confirms
   it has exactly one answer (§4.3). This is the top complaint in the genre and nobody
   else advertises fixing it.
2. **The hint explains the move instead of filling a square.** Same solver, used twice.
3. **Nothing is locked, timed or paywalled.** No energy, no lives, no stage gates.

Everything else in the description supports those three. Nothing below is a claim the app
does not keep — each number was read out of the code, not estimated.

---

## App name (30 characters max)

```
Nonogram: Pure Logic
```

*20 characters.* Puts the differentiator in the name, where it survives being seen in a
search result with no description attached.

Alternatives, in case the name is taken — Play names must be unique:

```
Nonogram Picross: Pure Logic
Daily Nonogram: Picross
```

The first alternative buys the second search keyword at the cost of length. The second is
the old name; it now undersells the app, because the daily puzzle stopped being the home
screen and a ladder of levels took its place.

---

## Short description (80 characters max)

```
Nonogram picross always solvable by logic. Hints that teach. Nothing locked.
```

*76 characters.* Both searched words, then all three claims in the order the full
description uses them. Play counts the trailing full stop, which is what took the first
draft of this line one character over the limit.

---

## Full description (4,000 characters max)

```
Every puzzle in this app can be solved by logic alone. Not most of them. All of them.

Fill in the squares the numbers describe, and a picture appears. If you have played
picross, griddler, hanjie or paint-by-numbers puzzles before, you already know the rules.

NEVER GUESS — CHECKED, NOT PROMISED
Every grid is run through a solver before it ships. One pass proves it can be finished by
reasoning alone. A second proves it has exactly one answer. Anything that fails either
test is thrown away instead of shipped.

So when you are stuck, there is always a next step you can work out. You are never being
asked to gamble and find out twenty squares later that you were wrong.

A HINT THAT TEACHES YOU THE MOVE
Most puzzle games fill in a square and tell you nothing. That gets you unstuck once and
leaves you no better at the next one.

This one explains itself:

"Row 2 needs 2 + 2 plus a gap between each, which is exactly 5. There is only one way to
fit that."

It even picks which square to show you based on what can be explained — so you get a move
you could have spotted yourself, rather than one that needed six steps of
cross-referencing. Three free hints a day, and they do not expire unused.

NOTHING LOCKED, NOTHING TIMED
No energy meter. No lives refilling while you wait. No stages sealed behind other stages.
All 5,049 puzzles are open from the moment you install — including the hard ones,
including the ones you have not earned yet. If you want to start on a 20x20, start on a
20x20.

START AT LEVEL 1
116 numbered levels, easiest first: 5x5 to warm up, then 10x10, then 15x15, then 20x20.
The next one is always one tap from the home screen, so there is never a question of
where to begin. Prefer to choose for yourself? Pick any size and difficulty and play a
random one.

49 PICTURES DRAWN BY HAND
Alongside the generated puzzles there are 49 grids drawn one square at a time — a
lighthouse, a guitar, a teapot, a frog. Each has a name, and the name stays hidden until
you finish it. They are held to exactly the same standard as everything else: solvable by
logic, exactly one answer.

A PUZZLE EVERY DAY
One daily puzzle, the same for everyone, rotating through sizes and difficulties across
the week. Build a streak. Miss a day and one free streak freeze a month covers you. Miss
more than that, and every past day is still sitting in the calendar with no penalty.

LEARN IT IN NINETY SECONDS
Never played a nonogram? The walkthrough solves one in front of you, one deduction at a
time, explaining each move as it makes it. No rules to memorise first.

BUILT TO FEEL RIGHT
Drag across a row to fill it in one stroke — it locks to the line, so a wobbly finger
cannot paint diagonally. A tick of haptic feedback on every square. Clues grey out as you
complete them. Undo as far back as you like. Pinch to zoom on the big grids.

PLAYS OFFLINE
No account. No sign-in. No connection needed. Your progress lives on your phone and
nowhere else. Start a puzzle on a plane and finish it in a tunnel.

LIGHT AND DARK
Both fully designed, not one inverted from the other. Text scales with your system font
size.

---

Free, with ads. Never during a puzzle — not once, not ever. At most one between puzzles,
and not after every puzzle. A single purchase removes them for good.
```

*~2,750 characters.*

### Notes on the copy

- **"Not most of them. All of them." is the first line** because it is the claim that
  separates this app from every competitor, and because it is falsifiable — a reviewer who
  finds a puzzle needing a guess has caught us out. `PuzzleGeneratorTest` and
  `PictureLibraryTest` are why that is safe to print.
- **The hint example is real output**, copied from the app, not written for the listing.
- **"No energy meter" is worth stating outright.** It is the most common complaint about
  free puzzle apps, and this one genuinely does not have one.
- **The ad line is last, plain, and specific.** "Minimal ads" invites arguments in
  reviews; "never during a puzzle" is checkable, and it is enforced by `AdPolicy` and
  unit-tested (§8.1). The real cadence — one interstitial at most, only on dismissing a
  results card, never within three minutes of the last, at most four a session, and never
  before your third puzzle — is stricter than the copy promises, which is the right way
  round.
- **No emoji, no exclamation marks**, consistent with the visual direction in §7.
- **Numbers come from the code**: 5,049 puzzles (5,000 generated + 49 drawn), 116 levels,
  3 free hints a day, 1 streak freeze a month. If any of those change, this file is wrong
  and needs editing.

---

## Keywords the copy covers naturally

nonogram · picross · griddler · hanjie · paint by numbers · logic puzzle · daily puzzle ·
offline puzzle · brain teaser · no guessing

---

## Categorisation

- **Category:** Games → Puzzle
- **Tags:** Brain games, Logic, Casual
- **Contains ads:** Yes — must be declared (§11.3)
- **In-app purchases:** Yes — `remove_ads`, `hint_pack_25`
- **Content rating:** Everyone. No violence, no user-generated content, no social
  features, no data shared between users.

---

## What the screenshots should carry

The current captures in `store/screenshots/` predate the ladder, the explaining hint and
the new look, so they need retaking either way. Shoot them to match the three claims
rather than to tour the app:

1. **The hint explaining itself** — the one thing no competitor's screenshot can show.
2. **The Play ladder**, with Level 1 and "Start here" visible. Answers "where do I begin"
   before the install.
3. **A finished picture with its name** — the payoff.
4. **A 15x15 mid-solve**, showing the board and the greyed-out clues.
5. **The picture collection**, to show there is a collection.
6. **Light theme**, so nobody assumes it is dark-only.

`store/capture_screenshots.py` drives a real device over adb; `compose_screenshots.py`
adds the caption bands afterwards, so captions can be reworded without replaying anything.

---

## Before first upload

- [ ] Confirm the name is available on Play
- [ ] Privacy policy published and reachable (see `store/privacy-policy.html` — its
      contact email is still a `TODO`)
- [ ] Retake the screenshots: the current ones are two redesigns out of date
- [ ] Decide whether to localise. §12 suggests Bengali, Spanish, Portuguese and
      Indonesian later; English only is fine for launch.
