# Daily Nonogram

A daily logic-puzzle app for Android. One nonogram per day plus an archive of 5,000
pre-generated puzzles. No backend, no accounts, no network beyond the ad SDK.

Built against `nonogram-app-build-plan.md`. Phases run in order; see **Status** below.

---

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Puzzle engine — solver, generator, pack | **Complete** |
| 2 | Game screen | **Built**, verified on device — 1 criterion missed |
| 3 | Shell (daily, archive, progress) | **Built**, verified on device |
| 4 | Visual design and theming | **Built**, verified on device |
| 5 | Monetization | **Built**, policy tested; SDK paths need Play test tracks |
| 6 | Hardening | **Built**, verified on device — crash reporting is a human step |
| 7 | Store assets | **Complete** — see `store/` |
| 8 | Publishing (human-operated) | **Start now, in parallel** — see below |
| — | Onboarding and picture puzzles | **Built**, verified on device — see below |

**305 tests, 0 failures**, running in about 7 seconds.

### Phase 4 acceptance criteria

- [x] Light and dark complete across every screen, no hardcoded colours outside the
      theme — enforced by `ThemePurityTest`, which fails the build on a stray
      `Color(0xFF…)` or a `tween(240)` that would dodge the reduce-motion setting
- [x] Contrast ratios meet WCAG AA — `ThemeContrastTest` computes WCAG 2.1 relative
      luminance for all 26 colour pairs in both themes, flattening translucent
      foregrounds first so the ratio reflects what is actually on screen
- [x] Usable at 200% system font scale — confirmed on device. Fixing this found a real
      defect: at 2× the "Undo" label wrapped inside its own button, so the control row
      now stacks above 1.5× instead of being squeezed.
- [x] No grid rendering regression — theming changed colour values only; the canvas
      still hoists its paints and allocates nothing per frame

Dark is authored outright rather than derived by inverting light, and a test pins that.
Animation durations live in `Motion` so reduce-motion applies in one place.

### Phase 3 acceptance criteria

- [x] Daily selection is deterministic — same date yields the same puzzle across fresh
      installs, with the hash pinned by test so past dailies can never silently move
- [x] Streak increments, breaks and freezes correctly across simulated date changes,
      unit-tested against an injected clock (37 tests) — no device date changes involved
- [x] Archive lists all 5,000 with working filters and no jank — measured on device:
      **476 frames, 1 janky (0.21%), p50 12 ms** over sustained flinging
- [x] Progress survives app kill — confirmed on device. *App update* is still untested
      in the sense that matters: there is only a v1 schema, so no migration exists yet.

### Beyond the plan — the hint explains itself

Every nonogram app has a hint button, and in all of them it fills a square and tells you
nothing: it unsticks you once and leaves you no better at the next one.

This one can do better because it already contains a real line solver — the one that
proves every shipped puzzle needs no guessing. It knows not just *which* square is forced
but *which line* forced it, so the hint names the move:

> Row 2 needs 2 + 2 plus a gap between each, which is exactly 5. There is only one way to
> fit that.

Four shapes of argument — a line its clues exactly fill, overlap, a finished clue, a
blank line — each checked against the line before it is offered. Anything that cannot be
justified says nothing rather than something vague: an explanation that turns out to be
wrong is worse than none, because a player who follows it and collects a mistake stops
trusting the app.

It also changed *which* square gets hinted. The solver reaches many answers by playing
rows and columns off each other for several passes; those squares are forced but
unexplainable. The hint now prefers one a single line settles — which is also the move
the player was most likely to have found alone.

### Beyond the plan — the app now opens on a ladder, not a date

The first real tester could not work out where the game started, and could not work out
what "daily" meant. Those are the same failure: the home screen opened on a *concept*.
"Today's puzzle" assumes you already play; "Level 1" assumes nothing.

So **Play** is the home tab: numbered levels, easiest first — twelve 5×5s, then thirty
10×10 easy, then medium, hard, expert. 116 levels, with forty-two before the ramp leaves
the easy end. The first thing on the screen is one bevelled button that says *Start
here*, and the ladder below it is for people who want to choose instead.

**Nothing is locked.** The plan's "nothing is paywalled or locked" holds, and it is the
better design anyway: a beginner stuck on level 9 can go elsewhere rather than being
trapped, and someone who has played nonograms before is not made to grind twelve 5×5s.
Ordering is guidance, not a gate.

Daily is the second tab, and says in one sentence what it is — shown until you have a
streak, then it stops appearing.

A cleared stage folds to one line — a check, "Cleared", and its count — so twelve 5×5s
you have already solved stop standing between you and everything below them. Tapping the
line opens it again.

**The archive stopped being a tab.** Next to the ladder it was redundant, and it was also
wrong about what 5,000 puzzles are: a *supply*, not a catalogue. Nobody scrolls five
thousand thumbnails hunting for one. So Play ends with two sections — **Pictures**, and
**Free play**, a size × level picker whose action is "play a random one" — and the grid
survives behind a *Browse all* link for the rare player who genuinely wants it. Three
tabs now, each with one job: what's next, today, preferences.

Levels come off the front of each pool and pack order is deterministic, so level 7 is the
same puzzle on every device. `LevelLadderTest` checks that, plus the things that would
confuse a new player: a ramp that goes backwards, a level number that skips, a "next"
that points at something already solved.

### Beyond the plan — the visual direction changed

§7 asked for "a well-made physical puzzle book, not a casual game", and phases 1–4 built
exactly that: paper, ink, one teal accent. Correct, and quiet. The brief then changed —
this should feel like a game — so the palette and controls were rebuilt as **Arcade
Night**: deep indigo, chunky bevelled keys, and **one colour per meaning** (gold acts,
coral costs you something, mint confirms, sky explains, violet is the picture
collection).

The board is the exception and stays disciplined: two colours inside the grid and a muted
cross. A nonogram is read, not just looked at, and a tinted square competes with the clue
beside it. The colour is spent on the frame, not the picture.

Full rationale, the contrast arithmetic a saturated palette forces, and what was
deliberately left flat for performance: `docs/design-language.md`.

### Beyond the plan — teaching the game, and giving it a payoff

Neither of these is in the build plan. Both address the same gap: the plan gets a player
to a correct board and stops.

**How to play** (`ui/tutorial/`). There was no explanation of the rules anywhere in the
app. Rather than a page of prose, the screen animates a full solve of a 5×5 puzzle, one
deduction at a time, with the reason on screen and the row or column it applies to
highlighted. The point it has to land is that *no step is a guess* — a player who thinks
nonograms involve guessing will play one badly and quit. It opens itself on first launch
and lives permanently at the top of Settings.

The script is pure Kotlin (`puzzle/tutorial/TutorialLesson.kt`) so it can be checked
rather than trusted: `TutorialLessonTest` runs the real solver over the lesson puzzle,
and asserts every move agrees with the answer and that the steps decide all 25 squares
exactly once. A tutorial that teaches a wrong move is worse than no tutorial.

**Picture puzzles** (`puzzle/pictures/`). The generator makes contiguous blobs, which
look organic but are not *pictures*; recognition is the payoff a nonogram exists for. 49
hand-drawn, named grids ship as a second pack, held to the same bar as a generated
puzzle — logic-solvable, exactly one solution, checked on every test run. They are a
separate collection in the archive rather than part of the daily rotation, for reasons
in `docs/picture-puzzles.md`. The name stays hidden until the picture is solved.

### Phase 7 — store assets

Everything §10 asks for is in `store/`, with `store/README.md` explaining how to
regenerate it and what is still yours to do before upload.

- App icon (512×512) and launcher icons at every density, including the Android 13+
  themed layer. **The app previously had no launcher icon at all** and was shipping with
  the system default.
- Feature graphic (1024×500)
- Six phone screenshots, captured from a real device rather than mocked: puzzles were
  solved over adb by reading the solution out of the bundled pack and drag-painting each
  run
- App name, short and full description (`store/listing.md`)
- Privacy policy (`store/privacy-policy.html`) — needs a contact email before it goes up

### Phase 6 acceptance criteria

All four measured on the minified release build, installed on the Galaxy A15.

- [x] **Release build (minified) runs correctly.** This was the real risk — the plan
      calls R8 breakage that only shows up in release "a classic late-stage disaster".
      Verified end to end: Room wrote and read progress across a force-stop, the puzzle
      pack decoded, DataStore settings persisted, no `ClassNotFoundException` or
      `NoSuchMethodError`. The keep rules are correct. The purchase and ad cycle still
      needs a Play test track and an AdMob account.
- [x] **No crashes across a soak.** 4,000 random events through every screen on the
      minified build: zero fatals, process still alive. Rotation mid-puzzle preserves
      the board and does not crash.
- [x] **App size under 15 MB** — release APK is **4.09 MB**.
- [x] **Cold start under 2 seconds** — **501 ms** measured with `am start -W`.

Accessibility: the board is a single `Canvas`, so a screen reader saw nothing. It now
carries a summary, and each cell gets a real semantics node announcing its coordinates
and state, with activation wired to painting — but **only when touch exploration is
actually on**. Four hundred semantics nodes would undo the reason the grid is a canvas
at all, so with TalkBack off this composes nothing.

Verifying the release build needs a signing key and real AdMob IDs that no fresh clone
has, so there is a local escape hatch:

```bash
./gradlew :app:assembleRelease -PlocalReleaseCheck=true
```

That signs with the debug key and permits Google's test ad units. The output is
deliberately not shippable — Play rejects debug-signed uploads and test ads earn nothing.
Without the flag, a release build **fails** rather than silently shipping test ads.

#### Still to do in Phase 6 — needs your accounts

- **Crash reporting.** Play Console's Android Vitals covers the 99.5% crash-free target
  with no setup once published, which is the plan's own fallback. Firebase Crashlytics
  gives better stack traces but needs a Firebase project and a `google-services.json`
  that cannot be committed, so it is left for you to add.
- **Analytics.** Same constraint. The plan wants only D1/D7 retention, puzzles started
  vs completed, hint usage, ad impressions and IAP conversion — nothing more, and
  whatever is added has to match the Data Safety form filed in Phase 8.

### Board rendering performance

Measured on a Galaxy A15 (Helio G99, 1080x2340). The display runs at **90 Hz**, so
Android's own jank counter is judging against a 12.1 ms deadline, not the 16.7 ms the
plan's "60fps" criterion implies.

| Change | p50 | p90 | Slow-draw frames |
|---|---|---|---|
| Starting point | 18 ms | 21 ms | 108 |
| Cull blank cells, record clue text into a `Picture` | 18 ms | 24 ms | 15 |
| Read board state in the draw phase, not composition | 16 ms | 24 ms | 56 |
| Byte-backed board, O(1) completion, no offscreen layers | **15 ms** | 23 ms | **7** |

Drawing is now essentially free - 7 slow-draw frames out of 138, down from 108.

**The decisive measurement:** dragging over cells that are already crossed, so *no board
state changes at all* and only the highlight moves, still costs **p50 13 ms**. That is
the floor for redrawing a full-screen Compose `Canvas` on this device. All of the game
logic - the board copy, the completion check, the clue-progress scan - accounts for
about 2 ms of the 15.

So: **roughly 66fps median during a drag.** That meets the plan's 60fps at the median
and misses it at p90 (23 ms). It cannot meet the device's own 90 Hz target, because an
empty redraw already exceeds that deadline.

Two things worth knowing before anyone optimises further:

- The remaining cost is Compose's full-surface redraw, not this code. Shaving the game
  logic further will not move it; the next real lever would be redrawing less of the
  screen, and the board already fills most of it.
- These numbers come from `adb input swipe`, which injects events far more slowly than a
  real finger (~15/sec against 120+). Each injected event produces exactly one frame, so
  the frame *count* here says nothing about real smoothness - only the per-frame cost
  does. Re-measure with a real finger and `dumpsys gfxinfo framestats` before drawing
  conclusions.

### Phase 5 acceptance criteria

- [x] Interstitial frequency caps enforced and unit-tested — every trigger other than
      results-dismiss is provably incapable of showing one
- [x] No-fill and airplane-mode paths grant the hint and never hang
- [x] Debug builds use test ad units; a release built without real ones **fails the
      build** rather than silently shipping test ads
- [ ] Rewarded ads grant reliably — needs a device with a real AdMob account
- [ ] `remove_ads` purchase, restore-on-reinstall and pending purchases — needs Play
      test tracks. The entry points are in: **Remove ads** and **25 hints** rows at the
      bottom of Settings, and the pack beside the rewarded ad when hints run out. Each
      shows Play's localized price, is not tappable until that price has loaded, and
      says "Payment pending" for a cash or carrier payment still settling. The state
      logic is unit-tested (`StoreOffersTest`); **no purchase has been made end to end
      yet** — that still needs the Play Console products, a test track and a licence
      tester on a real device.

### Phase 2 acceptance criteria

Verified on a physical Galaxy A15 (SM-A155M, Android 16, 90 Hz display).

- [x] All four grid sizes render and are playable — confirmed on device
- [~] **Sustained 60fps dragging on 20×20 — met at the median, not at p90.** p50 15 ms
      (~66fps), p90 23 ms. See "Board rendering performance" for why the remaining cost
      is not in this code.
- [x] Drag-paint mode-locking and axis-snapping — confirmed on device: a sweep with
      deliberate vertical wobble painted one row only
- [x] Kill mid-puzzle and relaunch restores board, timer, lives and undo — confirmed by
      `am force-stop` mid-puzzle; the board came back with the timer frozen at 0:43, zero
      lives and all three mistake cells intact
- [x] Hint always returns a logically-deducible cell
- [x] Undo reverses every action type including hint reveals

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
│   ├── pictures/    # 23 hand-drawn named grids
│   ├── tutorial/    # The scripted solve the How to play screen animates
│   └── tools/       # Offline generation entry point
├── daily/           # DailySchedule, DailySelector, StreakCalculator, calendar screen
├── progression/     # LevelLadder and the Play home screen
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
├── ui/              # Navigation, settings screen, AppContainer
│   ├── components/  # Buttons, panels, chips, meters, the drawn icon set
│   ├── theme/       # The only place a colour literal may appear
│   └── tutorial/    # How to play - the animated walkthrough
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

All three are documented in full where they live; summarised here so they are not a surprise.

**1. Difficulty is rated on a composite score, not `solveDepth` alone.**
The plan's table (`EXPERT = 20×20 at depth 11+`) cannot be implemented: measured over
4,000 samples per size, no 20×20 puzzle reached depth 11, and depth turns out *not* to
grow with grid size. `DifficultyRater.hardness` keeps depth dominant and adds two
measured signals to break up the clumps. The plan invites exactly this ("tune these
thresholds empirically during Phase 1"). Numbers in `docs/phase1-calibration.md`.

**2. The daily selector walks each pool instead of sampling it.**
The plan's `hash(dateString) % poolSize` (§6.2) has no memory, so every day is an
independent draw and the birthday paradox applies — measured against the real pack it
handed back an already-solved puzzle **14 times a year**. The requirement behind the
formula is "deterministic, no server, no per-device drift", and that is kept in full:
`DailySelector` counts how many times a weekday slot has come round since a fixed epoch
and steps through a fixed permutation of that slot's pool. No puzzle repeats within any
`poolSize` consecutive draws; the soonest repeat is **7.2 years** out. Still a pure
function of the date.

**3. Bands are relative within a grid size.** A 20×20 `HARD` means "the easier 43% of
20×20 puzzles", not a difficulty comparable to a 15×15 `HARD`. The depth distributions
for different sizes overlap almost completely, so no cross-size absolute scale exists.

---

## Open decisions

**`applicationId` is currently `com.ganim.nonogram`** — a placeholder standing in for
the plan's `com.<yourdomain>.nonogram`. It is a find-and-replace away right now and
**permanently fixed the moment the app is first uploaded to Play**. Change it before
Phase 8 if you want something else.


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
