# Daily Nonogram

A daily logic-puzzle app for Android. One nonogram per day plus an archive of 5,000
pre-generated puzzles. No backend, no accounts, no network beyond the ad SDK.

Built against `nonogram-app-build-plan.md`. Phases run in order; see **Status** below.

---

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Puzzle engine — solver, generator, pack | **Complete**, 54 tests green |
| 2 | Game screen | Not started |
| 3 | Shell (daily, archive, progress) | Not started |
| 4 | Visual design and theming | Not started |
| 5 | Monetization | Not started |
| 6 | Hardening | Not started |
| 7 | Store assets | Not started |
| 8 | Publishing (human-operated) | **Start now, in parallel** — see below |

### Phase 1 acceptance criteria

- [x] `solveLine` property-tested over 20,000 random valid lines, zero incorrect deductions
- [x] `PuzzleSolver` classifies a hand-written suite: 5 unique, 3 ambiguous, 3 contradictory
- [x] 100 puzzles per difficulty, every one confirmed single-solution by an independent
      backtracking verifier
- [x] 5,000-puzzle pack emitted at 169 KB (budget: 1 MB); round-trip test re-derives all
      clues and reproduces the file byte for byte
- [x] Zero Android imports under `puzzle/`, enforced by `PuzzlePackagePurityTest`
- [x] Full suite runs in 2.6s (budget: 30s)

---

## Commands

```bash
./gradlew :app:testDebugUnitTest          # unit tests
./gradlew :app:assembleDebug              # debug build
./gradlew :app:assembleRelease            # release build (verify R8 output runs)
./gradlew :app:bundleRelease              # AAB for Play upload
./gradlew generatePuzzlePack              # regenerate the bundled puzzle pack
```

Calibrate difficulty thresholds (see `docs/phase1-calibration.md`):

```bash
./gradlew generatePuzzlePack -Pcalibrate=true -Psample=4000
```

`generatePuzzlePack` also accepts `-Pcount=`, `-Pseed=`, `-Pthreads=` and `-Pout=`.
The same seed and thread count produce a byte-identical pack on any machine.

---

## Layout

```
app/src/main/kotlin/com/ganim/nonogram/
└── puzzle/          # Pure Kotlin, zero Android deps (enforced by test)
    ├── model/       # Puzzle, Clue, Grid, CellState, Difficulty
    ├── solver/      # LineSolver, PuzzleSolver, BacktrackingVerifier
    ├── generator/   # PuzzleGenerator, GridShaper, DifficultyRater
    ├── pack/        # Binary pack codec
    └── tools/       # Offline generation entry point
```

`puzzle/` stays free of Android imports so it runs on a desktop JVM at full speed —
that is what lets the generator and its tests be fast, and it is enforced rather than
merely intended.

---

## Decisions that differ from the build plan

Both are documented in full where they live; summarised here so they are not a surprise.

**1. Difficulty is rated on a composite score, not `solveDepth` alone.**
The plan's table (`EXPERT = 20×20 at depth 11+`) cannot be implemented: measured over
4,000 samples per size, no 20×20 puzzle reached depth 11, and depth turns out *not* to
grow with grid size. `DifficultyRater.hardness` keeps depth dominant and adds two
measured signals to break up the clumps. The plan invites exactly this ("tune these
thresholds empirically during Phase 1"). Numbers in `docs/phase1-calibration.md`.

**2. Bands are relative within a grid size.** A 20×20 `HARD` means "the easier 43% of
20×20 puzzles", not a difficulty comparable to a 15×15 `HARD`. The depth distributions
for different sizes overlap almost completely, so no cross-size absolute scale exists.

---

## Open decisions

**`applicationId` is currently `com.ganim.nonogram`** — a placeholder standing in for
the plan's `com.<yourdomain>.nonogram`. It is a find-and-replace away right now and
**permanently fixed the moment the app is first uploaded to Play**. Change it before
Phase 8 if you want something else.

**This directory is not a git repository yet.** The plan calls for committing the
generated pack (`app/src/main/assets/puzzles.bin`, 169 KB) so it is not rebuilt per
developer. `.gitignore` is already written for it.

---

## Phase 8 starts now, not later

Per build plan §11.1, these run in parallel with engineering and gate the launch date:

1. Play Console developer account, $25, plus identity verification (takes days).
2. Decide account type. A personal account created after 13 Nov 2023 must complete
   closed testing — **12 testers, opted in continuously for 14 days, genuinely engaged**
   — before production access.
3. Payments profile. The bank account country must match the profile country and
   **cannot be changed afterwards**.
4. AdMob setup and address verification (PIN by post) — slow, and it blocks the first
   payout.

Realistic total from first tester to production approval: **3–6 weeks**. Recruiting 15
testers to land 12 should begin around Phase 3.

---

## Toolchain

JDK 21 (Android Studio JBR), Gradle 9.7.1, AGP 9.4.1 (Kotlin support is built in — the
separate `org.jetbrains.kotlin.android` plugin is an error from AGP 9.0), compileSdk 36,
minSdk 24, targetSdk 36.
