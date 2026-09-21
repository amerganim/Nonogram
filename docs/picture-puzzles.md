# Picture puzzles

The generator (build plan §4.4) shapes its grids into contiguous blobs "so completed
puzzles look like *something*". They do read as organic shapes — but a blob is not a
picture. You finish one, you get a pleasant silhouette, and no moment of recognition.

Recognition is the actual payoff of a nonogram. It is the reason someone fills in the
last square instead of closing the app. So alongside the 5,000 generated puzzles there
are 49 drawings made by hand, each with a name, shipped as a second pack.

## What ships

| | Generated | Pictures |
|---|---|---|
| Count | 5,000 | 49 |
| Asset | `puzzles.bin` (178 KB) | `pictures.bin` (1.5 KB) |
| Sizes | 5–20 | 10 (×38), 15 (×11) |
| Named | no | yes |
| Used by the daily | yes | no |

**The two packs are deliberately separate.** Merging them was the obvious first move and
it is wrong twice over. The daily selector draws from pools filtered out of the generated
pack, so adding records would shift every index and change every daily puzzle ever
assigned — including ones players have already solved, whose progress is keyed by puzzle
id. And 49 drawings spread across a daily rotation would be exhausted inside a month,
after which the feature silently stops existing. They are their own collection, browsable
at any time, from the **Pictures** card on the Play screen.

## Format v2

The pack format gained a per-record name:

```
width u8 | height u8 | difficulty u8 | solveDepth u8 | nameLength u8 | name bytes | bitset
```

A generated puzzle writes `nameLength = 0` and costs one extra byte. The name is
variable-length and sits *before* the bitset, so a mis-sized name field corrupts every
record after it rather than just its own — `PuzzlePackTest.names round trip` exists
specifically to catch that, by encoding a pack where only every other record is named.

## The bar a drawing has to clear

Being nice to look at has nothing to do with being solvable. Every drawing still has to
pass §4.3 in full: **solvable by logic alone, exactly one solution.** Checked two
independent ways, because they catch different faults:

- `PuzzleSolver` — reaches the answer without guessing. A *stall* means a player would
  have to guess, which is the one thing a fair nonogram never asks for.
- `BacktrackingVerifier` — counts solutions, capped at 2. Logic reaching *an* answer is
  not proof it is the *only* answer.

`PictureLibraryTest` runs both over all 49 on every test run, so a drawing cannot be
edited into an unsolvable state without the build saying so. To check by hand while
drawing:

```bash
./gradlew generatePuzzlePack -Ppictures=true
```

This prints OK / STALL / BAD / BROKEN per drawing and writes nothing.

## What fails, and why

Roughly one drawing in six does not survive first contact, and the failures are not
random — they follow from how line solving works. Two kinds, and they need different
fixes.

**The solver rejects it.** Thin shapes, outlines and diagonals. A line made of single
squares has a clue like `1 1 1`, which pins almost nothing, so the solver runs out of
forced moves in the middle (a *stall*) or the search finds two arrangements the clues
cannot tell apart. A pure diagonal is the worst case: every line is one run that can
slide, and nothing holds it. The fix is always the same — make the shape solid. Of the
second batch, four failed this way: a pair of cherries on thin stems (three solutions), a
carrot that tapered a square at a time (stalled), a diagonal pencil (two solutions, and
no redrawing keeps it a pencil — dropped), and a crab with outlined claws (three
solutions).

**The eye rejects it.** This one no test can catch, and it is the more common failure. A
drawing can be perfectly solvable and simply not look like the thing. Sometimes the fix
is a redraw; often it is cheaper and more honest to *rename it to what it actually is*,
because the name is the payoff and a wrong name spoils the moment the picture exists for:

| Drawn as | Ships as | Why |
|---|---|---|
| Cherry | **Frying pan** | round body, long handle — nobody sees fruit |
| Diamond | **Trophy** | two handles and a tapering cup |
| Candle | **Chess pawn** | the flame never read as a flame |
| Crab | **Frog** | two eyes on top and four legs; a crab's claws are at its sides |
| Carrot | **Fox** | two sprigs are ears and a narrowing body is a muzzle. Three attempts, same result |

## Three from the first batch, and why

These are the interesting part — the failures are not random, they follow from how
line-solving works.

- **Crescent moon — STALL.** Drawn as an outline. An outline leaves its interior
  underdetermined: the clue for a row of an outlined shape is `1 … 1`, which pins almost
  nothing, and the solver runs out of forced moves in the middle. Redrawn solid. Solid
  shapes constrain their lines far harder, because a long run has few placements.
- **Cup of tea — BAD, two solutions.** The handle and the rim produced two clue sets that
  a swap between them satisfied equally. Replaced with a star; the cup was never going to
  survive the fix without becoming a different drawing.
- **Flower — legible failure, not a solver failure.** It solved fine and read as a
  lollipop. Five petals need more than 15×15 to separate. Replaced with an anchor, which
  reads at any size.

Two more, **Envelope** and **Leaf**, passed the solver and failed a visual review — the
envelope's flap was broken into pieces that read as noise, and the leaf was parallel
diagonals that read as stripes. Both redrawn. That review is a contact sheet of every drawing
rendered side by side; there is no automated substitute for looking at them.

## Adding a drawing

1. Add a `picture("Name", …)` block to `PictureLibrary.all`. Square, `#` and `.` only —
   the constructor rejects anything else.
2. `./gradlew generatePuzzlePack -Ppictures=true` and read the verdict.
3. If it stalls, make the shapes more solid. If the count comes back 2, something in the
   drawing is symmetric in a way the clues cannot distinguish; change one of the two
   halves, not both.
4. `./gradlew generatePuzzlePack` to rewrite both packs, then `./gradlew test`.

Keep names to 16 characters — longer ones are clipped under an archive thumbnail.

## Where the name shows up

Nowhere, until it is solved. A thumbnail labelled "Cat" gives away the answer, so the
archive shows `10x10` until the picture is complete and the name afterwards, in the
accent colour. On completion the results card leads with **You drew — *Cat***, ahead of
the time and the mistake count, because that is the moment the feature exists for.
