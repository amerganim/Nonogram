# Phase 1 calibration

Measured 2026-09-19. Reproduce with:

```bash
./gradlew generatePuzzlePack -Pcalibrate=true -Psample=4000
```

Sample: 4,000 logic-solvable grids per size, seed `20260919`. Raw output in
`docs/calibration-raw.txt`.

---

## 1. Acceptance rate — the plan's estimate was pessimistic

Build plan §4.4 says *"Rejection rate will be high — that's expected and fine."*
Measured, it is not high:

| Size | Candidates | Accepted | Acceptance |
|---|---|---|---|
| 5×5 | 5,777 | 4,000 | **69.2%** |
| 10×10 | 7,207 | 4,000 | **55.5%** |
| 15×15 | 6,372 | 4,000 | **62.8%** |
| 20×20 | 6,348 | 4,000 | **63.0%** |

Two thirds of candidate grids are solvable by logic alone. The 15×15 and 20×20 rates
are *higher* than 10×10 because those sizes go through the blob shaper (§4.4's "shape
bias"): contiguous regions produce longer runs and longer runs constrain lines harder.
The shape bias the plan asked for on aesthetic grounds pays for itself on yield too.

Practical effect: the full 5,000-puzzle pack generates in **2.0 seconds** on 8 threads.
There is plenty of headroom to raise the count or tighten the quality bar later.

## 2. `solveDepth` alone cannot implement the plan's difficulty table

Build plan §4.4 proposes:

| Difficulty | Sizes | Approx. solveDepth |
|---|---|---|
| EASY | 5×5, 10×10 | 2–3 |
| MEDIUM | 10×10, 15×15 | 4–6 |
| HARD | 15×15, 20×20 | 7–10 |
| EXPERT | 20×20 | 11+ |

Measured depth distributions:

| Size | p50 | p75 | p90 | p99 | max |
|---|---|---|---|---|---|
| 5×5 | 2 | 2 | 3 | 4 | 8 |
| 10×10 | 3 | 4 | 5 | 8 | 13 |
| 15×15 | 4 | 4 | 5 | 7 | 10 |
| 20×20 | 4 | 5 | 5 | 7 | **10** |

Three problems:

1. **Depth does not grow with grid size.** The plan assumes bigger grids need deeper
   solves. The opposite holds: 10×10 reaches depth 13, while 20×20 tops out at 10. A
   larger grid puts more crossing constraints through every cell, so each pass
   accomplishes proportionally more.
2. **`EXPERT = 20×20 at depth 11+` is an empty band.** Not one puzzle in 4,000 reached
   it. Left as specified, the EXPERT quota of 800 could never be filled.
3. **Depth is too coarse to make four bands.** At 20×20, 68% of puzzles are depth 4 or
   5. Any cutoff on raw depth moves the band share by 20+ points at a step.

## 3. The composite score

`DifficultyRater.hardness` keeps depth dominant and adds two measured signals:

```
hardness = solveDepth × 100
         + slowPasses × 60        // passes resolving ≤5% of the grid — the grind
         + (1 − openingYield) × 40 // how little the first pass gave away
```

`slowPasses` separates puzzles depth cannot tell apart — at 20×20 it spreads across
0–7 with real mass at 1, 2 and 3. The opening-yield term is continuous on purpose: with
integer-only terms the scores clump and no cutoff lands near the quota split.

| Size | slowPasses distribution | openingYield p10 / p50 / p90 |
|---|---|---|
| 5×5 | all 0 (too small to grind) | 0.48 / 0.76 / 1.00 |
| 10×10 | 0:1949 1:1805 2:193 3:32 4+:21 | 0.27 / 0.48 / 0.69 |
| 15×15 | 0:746 1:2274 2:730 3:171 4+:79 | 0.53 / 0.69 / 0.81 |
| 20×20 | 0:312 1:2053 2:1219 3:307 4+:109 | 0.45 / 0.64 / 0.77 |

## 4. Calibrated cutoffs

Chosen as the quantile that produces the quota split in §4.5 of the plan
(1,500 easy / 1,500 medium / 1,200 hard / 800 expert):

| Size | Bands | Cutoff | Achieved share | Quota needs |
|---|---|---|---|---|
| 5×5 | EASY only | — | 100% EASY | 600 EASY |
| 10×10 | EASY / MEDIUM | **417** | 54.4% EASY | 54.5% |
| 15×15 | MEDIUM / HARD | **472** | 55.7% MEDIUM | 55.6% |
| 20×20 | HARD / EXPERT | **477** | 43.5% HARD | 42.9% |

All three land within one point of target, so the pack fills every bucket from the
harvest with no targeted top-up needed.

### What the bands mean

They are **relative within a grid size**, not absolute across sizes. A 20×20 `HARD` is
"the easier 43% of 20×20 puzzles", not "as hard as a 15×15 `HARD`". This matches how the
archive is browsed — pick a size, then a difficulty inside it — and it is the only
honest reading available, because the underlying depth distributions for different sizes
overlap almost completely.

## 5. Resulting pack

```
EASY   5x5:   600 puzzles, depth 1-6
EASY   10x10: 900 puzzles, depth 2-4
MEDIUM 10x10: 750 puzzles, depth 4-12
MEDIUM 15x15: 750 puzzles, depth 2-4
HARD   15x15: 600 puzzles, depth 4-9
HARD   20x20: 600 puzzles, depth 2-4
EXPERT 20x20: 800 puzzles, depth 4-12
```

5,000 puzzles, 5,000 distinct ids, **173,012 bytes (169 KB)** — inside the plan's
150–250 KB estimate and well under its 1 MB ceiling.

Note the overlapping depth ranges between bands (e.g. 15×15 HARD spans 4–9 while 15×15
MEDIUM spans 2–4). That is the composite score doing its job: two depth-4 puzzles can
land in different bands because one ground through three slow passes and the other did
not.

## 6. When to re-run this

Any change to `GridShaper`, `LineSolver` or `PuzzleSolver` moves this distribution.
Re-run the calibration and update `DifficultyRater`'s cutoffs, or the difficulty labels
drift away from the quotas without anything failing.
