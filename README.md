# Daily Nonogram

A daily logic-puzzle app for Android. One nonogram per day plus an archive of 5,000
pre-generated puzzles. No backend, no accounts, no network beyond the ad SDK.

Built against `nonogram-app-build-plan.md`. Phases run in order; see **Status** below.

---

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Puzzle engine — solver, generator, pack | **Complete** |
| 2 | Game screen | **Built**, 2 criteria need a device |
| 3 | Shell (daily, archive, progress) | **Built**, 2 criteria need a device |
| 4 | Visual design and theming | Not started |
| 5 | Monetization | Not started |
| 6 | Hardening | Not started |
| 7 | Store assets | Not started |
| 8 | Publishing (human-operated) | **Start now, in parallel** — see below |

**174 tests, 0 failures**, running in about 6 seconds.

### Phase 3 acceptance criteria

- [x] Daily selection is deterministic — same date yields the same puzzle across fresh
      installs, with the hash pinned by test so past dailies can never silently move
- [x] Streak increments, breaks and freezes correctly across simulated date changes,
      unit-tested against an injected clock (37 tests) — no device date changes involved
- [x] Archive filters over all 5,000 puzzles — **but "no jank while scrolling" is not
      measured yet.** The index is built from record headers without decoding any grid,
      and only visible thumbnails decode one; that should be enough, but it is a claim
      about a real GPU, not a tested fact.
- [ ] **Progress survives app kill, device restart and app update.** The snapshot codec
      and repository logic are tested, and Room verifies its own SQL at compile time. The
      DAO round-trip and a real process kill are not yet exercised. There is also only a
      v1 schema so far, so there is no migration to test.

### Phase 2 acceptance criteria

- [x] All four grid sizes lay out and are reachable (`BoardMetrics` unit-tested at each size)
- [ ] **Sustained 60fps dragging on 20×20** — needs a device or emulator, not yet measured
- [x] Drag-paint mode-locking and axis-snapping behave as specified (`DragTracker` tests)
- [ ] **Kill mid-puzzle and relaunch** — the save/restore round-trip is unit-tested
      including corrupt and truncated files, but not yet exercised on a real process kill
- [x] Hint always returns a logically-deducible cell — verified by solving whole puzzles
      by hint alone and re-deriving each one independently
- [x] Undo reverses every action type including hint reveals

Two criteria above are unticked on purpose. The logic behind them is tested; what is
untested is the behaviour of a real Android process and a real GPU. Do not treat Phase 2
as signed off until both are checked on hardware.

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
├── puzzle/          # Pure Kotlin, zero Android deps (enforced by test)
│   ├── model/       # Puzzle, Clue, Grid, CellState, Difficulty
│   ├── solver/      # LineSolver, PuzzleSolver, BacktrackingVerifier
│   ├── generator/   # PuzzleGenerator, GridShaper, DifficultyRater
│   ├── pack/        # Binary pack codec
│   └── tools/       # Offline generation entry point
├── daily/           # DailySchedule, DailySelector, StreakCalculator, calendar screen
├── archive/         # Browser over all 5,000 puzzles
├── data/
│   ├── assets/      # PuzzlePackLoader - reads puzzles.bin
│   ├── db/          # Room entities, DAOs, database
│   ├── repo/        # PuzzleRepository, ProgressRepository, SettingsRepository
│   └── session/     # BoardSnapshotCodec - packs an in-progress board
├── game/            # Rules, input and rendering
│   ├── GameState    # immutable board + lives + timer + undo
│   ├── GameEngine   # every rule, as pure functions
│   ├── DragTracker  # mode lock and axis snap
│   ├── ClueProgress # which clue groups are satisfied
│   ├── HintProvider # picks a deducible cell
│   ├── BoardMetrics # layout, zoom, pan (no Compose types, so it is unit-tested)
│   ├── BoardCanvas  # one-pass Canvas rendering
│   ├── BoardGestures# tap / drag / long-press / pinch
│   └── GameViewModel
├── ui/              # Navigation, settings screen, theme tokens, AppContainer
└── MainActivity.kt
```

`puzzle/` stays free of Android imports so it runs on a desktop JVM at full speed —
that is what lets the generator and its tests be fast, and it is enforced rather than
merely intended.

The same instinct shapes `game/`: rules, input interpretation and layout arithmetic are
plain Kotlin classes with no Compose types, so they are unit-tested directly instead of
through a rendered frame. Only `BoardCanvas`, `BoardGestures` and `GameScreen` touch
Compose.

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

**The daily puzzle repeats about 14 times a year.** This is inherent to the plan's
`hash(dateString) % poolSize` (§6.2): each day is an independent draw with no memory, so
the birthday paradox applies — 104 Mondays and Tuesdays drawing from 900 easy 10×10
puzzles will collide on their own. Measured over 2026: 14 repeats, slightly better than
the ~19 chance would predict, so the hash is fine. The *formula* is the limit.

It is fixable without a server and without storing anything: number each spec's
occurrences since an epoch date, then walk a deterministic permutation of the pool so
every puzzle appears once before any repeats. That pushes the first repeat out to about
8.6 years. Left alone for now because §6.2 specifies the formula explicitly — say the
word and it is a small change.

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

JDK 21 (Android Studio JBR), Gradle 9.7.1, AGP 9.4.1, Compose BOM 2026.09.00.

Two things that will bite anyone setting this up fresh:

- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is a
  hard error. The separate `org.jetbrains.kotlin.plugin.compose` plugin is still
  required whenever `buildFeatures.compose` is on.
- **compileSdk is 37, targetSdk is 36.** Compose 1.12 refuses to compile against
  anything below API 37. targetSdk stays at 36 because that is what Play currently
  requires for new uploads; the two are independent.

minSdk 24.
