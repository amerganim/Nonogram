# Play Store listing copy

Build plan §10: "Front-load the searched keywords naturally: nonogram, picross, griddler,
logic puzzle, hanjie. Write for humans; keyword-stuffed descriptions read badly and don't
help much."

So the keywords appear where they would anyway. The pitch is the one thing that actually
distinguishes this app from the twenty others in the niche: **every puzzle is solvable by
logic alone, and none of them require guessing.** That is a real engineering property
(§4.3 rejects any puzzle the solver cannot finish by deduction), not a marketing claim,
and it is the first thing an experienced picross player checks.

---

## App name (30 characters max)

```
Daily Nonogram
```

Alternative if `Daily Nonogram` is taken — Play names must be unique:

```
Daily Nonogram: Picross
```

---

## Short description (80 characters max)

```
Nonogram puzzles solvable by pure logic. A new picross daily, 5,000 offline.
```

*75 characters.* It gets the two searched words in (nonogram, picross), states the
differentiator, and names the two numbers that matter.

---

## Full description (4,000 characters max)

```
A nonogram every day, and five thousand more whenever you want one.

Fill the squares, read the numbers, and a picture appears. If you have played picross,
griddler, hanjie or paint-by-numbers puzzles before, you already know how this works.

NEVER GUESS
Every one of the 5,000 puzzles can be solved by logic alone. Not most of them. All of
them. Each puzzle is checked by a solver before it ships, and anything that would force
you to guess is thrown away. If you are stuck, there is always a next step you can
reason out — and the hint button will show you one you could have found yourself, not a
random square.

A NEW PUZZLE EVERY DAY
One daily puzzle, the same for everyone, rotating through sizes and difficulty across
the week. Build a streak. Miss a day and one free streak freeze a month has you covered.
Missed more than that? Every past day stays open in the calendar with no penalty.

5,000 PUZZLES, ALL UNLOCKED
Browse the whole archive by size, by difficulty, or by what you have finished. Nothing
is locked, nothing is drip-fed, and there is no energy meter waiting for you to come
back. Completed puzzles show the picture you revealed.

FOUR SIZES
5x5 for a spare minute. 10x10 and 15x15 for a proper sit-down. 20x20 when you want to
disappear for half an hour. Pinch to zoom on the big ones.

BUILT TO FEEL RIGHT
Drag across a row to fill it in one stroke — it snaps to the line so a wobbly finger
does not paint diagonally. A tick of haptic feedback on every square. Clues grey out as
you complete them. Undo as far back as you like.

PLAYS OFFLINE
No account. No sign-in. No connection needed. Your progress lives on your phone and
nowhere else. Start a puzzle on a plane and finish it in a tunnel.

LIGHT AND DARK
Both themes fully designed, not one inverted. Works with your system font size if you
need larger text.

---

Free to play with ads between puzzles. Never during one — not once. Remove them with a
single purchase if you would rather not see them.
```

*~1,850 characters.*

### Notes on the copy

- **"Never guess" leads** because it is the genuine differentiator and the thing this
  niche's audience complains about most in competitors' reviews.
- **"No energy meter"** is worth saying explicitly. It is a common complaint about free
  puzzle apps and this one genuinely does not have one.
- **The ad line is last and plain.** Claiming "minimal ads" invites arguments in reviews;
  stating the actual rule ("never during a puzzle") is checkable and true (§8.1, enforced
  by `AdPolicy` and unit-tested).
- **No emoji, no exclamation marks.** Consistent with the visual direction in §7.

---

## Keywords the copy covers naturally

nonogram · picross · griddler · hanjie · paint by numbers · logic puzzle · daily puzzle ·
offline puzzle · brain teaser

---

## Categorisation

- **Category:** Games → Puzzle
- **Tags:** Brain games, Logic, Casual
- **Contains ads:** Yes — must be declared (§11.3)
- **In-app purchases:** Yes — `remove_ads`, `hint_pack_25`
- **Content rating:** Everyone. No violence, no user-generated content, no social
  features, no data shared between users.

---

## Before first upload

- [ ] Confirm the app name is available on Play
- [ ] Privacy policy published and reachable (see `store/privacy-policy.html`)
- [ ] Recapture screenshots if the theme changes
- [ ] Decide whether to localise. §12 suggests Bengali, Spanish, Portuguese and
      Indonesian later; English only is fine for launch.
