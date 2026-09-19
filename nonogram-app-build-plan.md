# Daily Nonogram — Build Plan & Spec

**Document purpose:** This is a build specification for an AI coding agent (Claude Code) plus a human-operated publishing checklist. Phases 0–7 are executable engineering work. Phase 8 is human-only (Play Console, testers, payouts) and is included so the whole path from empty repo to published app lives in one document.

**Read this section before starting any phase.**

---

## 0. Ground rules for the coding agent

- **Work phase by phase.** Do not start a phase until the previous phase's acceptance criteria all pass. Each phase ends with a green build and green tests.
- **Phase 1 is the hard part.** The puzzle generator and solver must be finished, tested, and correct before any UI work begins. Do not scaffold screens first.
- **No backend. No accounts. No network calls except the ad SDK.** All state is local. This is a deliberate constraint, not an oversight.
- **Verify library versions before adding dependencies.** This document names libraries but deliberately does not pin most versions — check the current stable release (Play Billing and the Google Mobile Ads SDK both move, and Google deprecates old Billing versions on a schedule that will reject your uploads). If a version in this doc conflicts with what's current, current wins.
- **Write tests as you go**, especially in Phase 1. A generator bug that ships is unfixable for puzzles already bundled.
- **Ask before deviating from the data model or module boundaries.** Small implementation choices inside a module are yours.

---

## 1. Product definition

### What it is

A daily logic-puzzle app. One nonogram (picross) per day, plus an archive of thousands of pre-generated puzzles. Solve, keep a streak, come back tomorrow.

### Why this shape

The distribution strategy is organic Play Store search, not paid acquisition. People type "nonogram" and "picross" into Play with real intent, and the existing apps in that niche are mostly ad-heavy and visually dated. The product bet is: identical puzzle type, much better feel. Therefore **input feel and visual polish are features, not garnish** — they are the entire competitive advantage and should be treated as P0.

### Goals

1. Ship to Play Store production.
2. D1 retention above 25%, D7 above 10%.
3. Monetization wired correctly from launch (not retrofitted).
4. Total cash cost under $30 (the Play Console fee).

### Non-goals (explicitly out of scope for v1)

Accounts, cloud sync, leaderboards, social features, multiplayer, achievements, in-app puzzle editor, user-generated puzzles, colour nonograms, any server component, iOS.

---

## 2. Tech stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose | Custom `Canvas` for the grid — not a `LazyGrid` of composables |
| Min SDK | 24 (Android 7.0) | Covers the low-end devices in target markets |
| Target SDK | Current Play requirement | Play enforces a rolling target-SDK minimum; check before upload |
| Architecture | MVVM, single Activity | `ViewModel` + `StateFlow` |
| DI | Manual (constructor injection) | Hilt is overkill at this size |
| Persistence | Room (progress, streaks) + bundled JSON/binary asset (puzzles) | |
| Prefs | DataStore Preferences | |
| Ads | Google Mobile Ads SDK (AdMob) | No mediation in v1 |
| Billing | Google Play Billing Library, current stable | |
| Build | Gradle, Kotlin DSL, version catalog (`libs.versions.toml`) | |
| Tests | JUnit5 + Kotest assertions (unit), Compose UI test (smoke only) | |

**APK size budget: under 15 MB.** Puzzle data is the main risk; see §4.4 for the encoding.

---

## 3. Module structure

Single Gradle module (`:app`) — multi-module adds build complexity for no benefit at this size. Package structure:

```
com.<yourdomain>.nonogram
├── puzzle/          # Phase 1. Pure Kotlin, zero Android deps.
│   ├── model/       # Puzzle, Clue, Grid, CellState, Difficulty
│   ├── solver/      # LineSolver, PuzzleSolver, SolveResult
│   └── generator/   # PuzzleGenerator, DifficultyRater
├── data/
│   ├── db/          # Room entities, DAOs, database
│   ├── repo/        # PuzzleRepository, ProgressRepository, SettingsRepository
│   └── assets/      # Bundled puzzle pack loader
├── game/            # Play screen: ViewModel, grid Canvas, input handling
├── daily/           # Calendar, streak logic
├── archive/         # Puzzle browser
├── monetize/        # AdManager, BillingManager
├── ui/              # Theme, shared composables, navigation
└── MainActivity.kt
```

**Hard rule:** the `puzzle` package must have no Android imports. It is pure Kotlin so it can be unit-tested on the JVM at speed, and so the offline generation tool (§4.5) can reuse it.

---

## 4. Phase 1 — Puzzle engine (the foundation)

> This is the phase that determines whether the app is good. Budget 3 weeks. Do not rush it and do not move on until every acceptance criterion passes.

### 4.1 Domain model

```kotlin
enum class CellState { EMPTY, FILLED, CROSSED, UNKNOWN }

data class Clue(val values: List<Int>)   // e.g. [3, 1, 2]

data class Puzzle(
    val id: String,            // stable hash of the solution grid
    val width: Int,
    val height: Int,
    val rowClues: List<Clue>,
    val colClues: List<Clue>,
    val solution: BooleanArray, // row-major, width*height
    val difficulty: Difficulty,
    val solveDepth: Int         // see 4.3
)

enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }
```

Grid sizes shipped: **5×5, 10×10, 15×15, 20×20**. The daily puzzle rotates size by day of week (see §6.2).

### 4.2 Line solver

The core primitive. Given one line (row or column) — its clue and its current known cell states — deduce every cell that is forced.

Implementation: **enumerate all valid placements** of the clue blocks within the line that are consistent with currently-known cells. Any cell that is filled in every valid placement is forced filled; any cell empty in every valid placement is forced empty. For lines up to 20 cells this is cheap; memoize per (clue, known-state) key.

```kotlin
fun solveLine(clue: Clue, current: List<CellState>): List<CellState>
```

Returns the line with all forced deductions applied. Must be deterministic and must never make an incorrect deduction — write property tests that generate random valid lines and assert the solver never contradicts the truth.

### 4.3 Puzzle solver

Iterate: apply `solveLine` to every row, then every column, repeat until a full pass produces no change.

```kotlin
sealed class SolveResult {
    data class Unique(val depth: Int) : SolveResult()   // solved by logic alone
    object Ambiguous : SolveResult()                    // stalled, multiple solutions
    object Contradiction : SolveResult()                // invalid puzzle
}
```

- **`depth`** = the number of full row+column passes required. This is the primary difficulty signal.
- **Only accept `Unique`.** A puzzle that requires guessing or backtracking is rejected outright. This is the single most important quality rule in the app, and it is the thing most competitor apps get wrong.
- Do **not** implement backtracking search as a fallback for acceptance. It's fine to implement one purely to *verify* uniqueness in tests, but the shipping acceptance criterion is logic-solvability.

### 4.4 Generator

```
1. Generate a candidate solution grid at the target fill density
   (0.45–0.60 filled; outside that range puzzles are dull or trivial).
2. Derive row and column clues from it.
3. Run PuzzleSolver against the clues from a blank state.
4. If SolveResult != Unique → discard, go to 1.
5. Rate difficulty from solveDepth and size (see table).
6. If the rated difficulty != target → discard, go to 1.
7. Emit.
```

Rejection rate will be high — that's expected and fine, because generation happens offline, not on device.

**Difficulty rating** (tune these thresholds empirically during Phase 1 and record the final values in a comment):

| Difficulty | Sizes | Approx. solveDepth |
|---|---|---|
| EASY | 5×5, 10×10 | 2–3 |
| MEDIUM | 10×10, 15×15 | 4–6 |
| HARD | 15×15, 20×20 | 7–10 |
| EXPERT | 20×20 | 11+ |

**Add a shape bias for larger grids.** Purely random grids produce visual noise. For 15×15 and 20×20, bias generation toward contiguous blobs (e.g. random walk fill, or cellular-automaton smoothing) so completed puzzles look like *something*. This materially affects the satisfaction of the completion moment, which is the emotional payload of the whole app.

### 4.5 Offline generation tool

A Gradle task or `main()` that runs the generator on the JVM and writes the bundled puzzle pack.

- **Target: 5,000 puzzles** — roughly 1,500 easy, 1,500 medium, 1,200 hard, 800 expert.
- **Encoding: pack the solution as a bitset, not JSON booleans.** A 20×20 grid is 400 bits = 50 bytes. Derive clues at load time rather than storing them. 5,000 puzzles should land around 150–250 KB. If the pack exceeds 1 MB, the encoding is wrong.
- Store as a binary asset in `assets/`, with a small header (version, count, index offsets) so individual puzzles can be read without parsing the whole file.
- Commit the generated pack to the repo (it's small and deterministic output shouldn't be rebuilt per-developer).

### Phase 1 acceptance criteria

- [ ] `solveLine` passes property tests over ≥10,000 random valid lines with zero incorrect deductions.
- [ ] `PuzzleSolver` correctly classifies a hand-written suite: ≥5 known-unique puzzles, ≥3 known-ambiguous, ≥2 contradictory.
- [ ] Generator produces 100 puzzles at each difficulty, and an independent backtracking verifier confirms every one has exactly one solution.
- [ ] Generation tool emits the 5,000-puzzle pack; pack is under 1 MB; a round-trip test loads all 5,000 and re-derives clues matching the originals.
- [ ] Zero Android imports in the `puzzle` package (enforce with a test or lint rule).
- [ ] Full `puzzle` test suite runs in under 30 seconds.

---

## 5. Phase 2 — Game screen

The screen where players spend ~100% of their engaged time. Budget 3–4 weeks. Polish here is not optional.

### 5.1 Rendering

Custom Compose `Canvas`, drawing the whole board in one pass. Do not build the grid from individual composables — it will not perform acceptably on low-end devices at 20×20.

Elements: cell grid with heavier separator lines every 5 cells, row clues on the left, column clues on top, clue text greyed out as each clue group is satisfied, subtle highlight on the row and column under the finger.

**Pinch-to-zoom and pan for 15×15 and 20×20.** On a 5-inch screen a 20×20 grid with clues is unusable at fit-to-screen. Clamp zoom to sensible bounds and keep clue gutters pinned during pan.

### 5.2 Input — get this right

- **Tap** cycles the cell through the current paint mode.
- **Drag** paints continuously. The mode is locked by the *first* cell of the drag: if the drag starts on an empty cell in fill mode, the whole drag fills; if it starts on a filled cell, the whole drag clears. This prevents the flickering mess of per-cell toggling.
- **Drag should snap to a straight line** once the gesture has clearly committed to an axis. Players drag across rows constantly and finger wobble should not paint diagonally.
- **Mode toggle** (fill / cross) as a prominent, thumb-reachable button. Also support long-press to paint the opposite mode without switching.
- **Haptics on every cell state change** — `HapticFeedbackConstants.CLOCK_TICK` or a short `VibrationEffect`. This is a large part of why the app feels better than competitors. Make it disableable in settings.
- **Undo stack**, minimum 50 steps.

### 5.3 Rules and feedback

- **Mistake counter: 3 lives.** Filling a cell that is empty in the solution costs a life and marks the cell. Crossing a cell is never a mistake (it's a note, not an assertion).
- At 0 lives: offer "watch an ad to restore a life" (see §8.2) or restart.
- **Completion:** a satisfying animation — clue gutters fade out, the revealed picture animates in, brief particle or shimmer effect, then a results card with time, mistakes, and difficulty.
- **Auto-save on every state change.** The app must survive being killed mid-puzzle with zero loss.

### 5.4 Hints

A hint reveals one correct cell — specifically, pick a cell that is *logically deducible right now* from the current board state (run the solver against the player's current state and choose from the newly forced cells). A hint that reveals a random cell is much less useful and feels arbitrary.

Economy: 3 free hints per day, refilled at local midnight; more via rewarded video.

### Phase 2 acceptance criteria

- [ ] All four grid sizes render and are playable.
- [ ] Sustained 60fps while dragging on a 20×20 grid (profile on a low-end device or equivalent emulator profile).
- [ ] Drag-paint mode-locking and axis-snapping behave as specified.
- [ ] Kill the app mid-puzzle; relaunch restores exact board state, timer, lives, and undo availability.
- [ ] Hint always returns a logically-deducible cell.
- [ ] Undo correctly reverses every action type including hint reveals.

---

## 6. Phase 3 — Shell (daily, archive, progress)

### 6.1 Navigation

Single Activity, Compose Navigation, three top-level destinations: **Daily** (home), **Archive**, **Settings**.

### 6.2 Daily puzzle

- Deterministic selection: `puzzleIndex = hash(dateString) % poolSize`, selecting from the pool matching the day's size/difficulty. Deterministic means no server and no per-device drift.
- Rotation: Mon/Tue easy 10×10, Wed/Thu medium 15×15, Fri hard 15×15, Sat expert 20×20, Sun medium 10×10. Tune after playtesting.
- Calendar month view: completed days marked, today highlighted, past days playable (no penalty for backfilling — this drives archive engagement).

### 6.3 Streak

- Increments on completing the daily puzzle, in device local time.
- Breaks if a day is missed. Backfilling a past day does **not** repair a broken streak — it counts for completion stats only.
- Display current streak and best streak prominently on the Daily screen.
- **One streak freeze per month**, auto-applied, protecting against a single missed day. This measurably helps retention and costs nothing.

### 6.4 Archive

Browse all 5,000 puzzles, filterable by size, difficulty, and completion state. Grid of small thumbnails; completed puzzles show their revealed picture, incomplete show a lock-free placeholder. Nothing is paywalled or locked.

### 6.5 Persistence (Room)

```
PuzzleProgress(puzzleId, state, elapsedMs, mistakes, completedAt, boardSnapshot)
DailyRecord(date, puzzleId, completed, completedAt)
UserStats(currentStreak, bestStreak, totalCompleted, freezesRemaining, freezeMonth)
```

`boardSnapshot` is a packed bitset of in-progress state, same encoding as the puzzle pack.

### Phase 3 acceptance criteria

- [ ] Daily selection is deterministic — same date yields the same puzzle across fresh installs.
- [ ] Streak increments, breaks, and freezes correctly across simulated date changes (unit-test the streak logic against a mockable clock; do not rely on manual device date changes).
- [ ] Archive lists all 5,000 puzzles with working filters and no jank while scrolling.
- [ ] Progress survives app kill, device restart, and app update.

---

## 7. Phase 4 — Visual design and theming

### Direction

Calm, precise, uncluttered. Generous whitespace, one confident accent colour, high-contrast grid. The reference point is a well-made physical puzzle book, not a casual game with gradients and bubble letters.

- **Light and dark themes**, both fully specified. Dark is the default for many puzzle players — do not treat it as an afterthought.
- Material 3 as the base, customised. Avoid stock purple.
- Typography: one clean sans-serif, tabular figures for clue numbers (misaligned clue digits look broken).
- Respect system font scaling; the grid must stay usable at large accessibility text sizes.
- All animations under 300ms. Respect the reduce-motion system setting.

Consult the `frontend-design` skill before starting this phase if it's available in the session.

### Phase 4 acceptance criteria

- [ ] Light and dark themes complete across every screen, no hardcoded colours outside the theme file.
- [ ] Usable at 200% system font scale.
- [ ] Contrast ratios meet WCAG AA for all text.
- [ ] No visual regression in grid rendering performance after theming.

---

## 8. Phase 5 — Monetization

> Wire this correctly now. Retrofitting purchase flow later is painful and error-prone.

### 8.1 Principles

- Never interrupt an in-progress puzzle with an ad. Not once.
- No ads on app cold start.
- Rewarded video is the primary earner and the only ad type players actively choose.
- The "remove ads" purchase is the highest-converting IAP in this genre — make it easy to find and fairly priced.

### 8.2 Ad placements

| Placement | Type | Trigger |
|---|---|---|
| Extra hint | Rewarded | Player out of free hints, taps hint |
| Restore a life | Rewarded | Player hits 0 mistakes remaining |
| Between puzzles | Interstitial | After every 3rd completed puzzle, on the results card dismiss — never mid-puzzle |

- Interstitial frequency cap: **max 4 per session, minimum 3 minutes between.**
- Rewarded ads remain available to ad-free purchasers (standard practice, and they *want* the hints).
- Preload rewarded ads in the background so the hint button never shows a spinner.
- Handle no-fill and offline gracefully: if no ad is available, grant the hint anyway. A broken reward loop is worse than a lost impression.

### 8.3 IAP

| SKU | Type | Price | Effect |
|---|---|---|---|
| `remove_ads` | Non-consumable | ~$2.99 (set local pricing per market) | Removes all interstitials and banners |
| `hint_pack_25` | Consumable | ~$0.99 | 25 hints |

Use the current stable Play Billing Library. Requirements:

- Query and restore purchases on every app launch — entitlement must survive reinstall and device change.
- Acknowledge purchases within Play's required window or they are automatically refunded.
- Handle pending purchases (some payment methods in emerging markets complete asynchronously — this matters for your likely player base).
- Verify entitlement locally against the Billing client. No server, so no server-side receipt validation; accept that tradeoff explicitly for v1.

### 8.4 Configuration

Ad unit IDs and the AdMob app ID go in `local.properties` and are injected via `BuildConfig`. **Never commit real ad unit IDs.** Use Google's official test ad unit IDs in debug builds, wired automatically by build type.

### Phase 5 acceptance criteria

- [ ] Rewarded ads grant their reward reliably, including when the ad is dismissed early (no reward) vs. completed (reward).
- [ ] No-fill and airplane-mode paths grant the hint and never hang.
- [ ] Interstitial frequency caps enforced and unit-tested.
- [ ] `remove_ads` purchase, restore-on-reinstall, and pending-purchase flows all verified against Play's test tracks.
- [ ] Debug builds use test ad units; release builds use real ones; verified by inspecting `BuildConfig` in both.
- [ ] Zero ads observed during any in-progress puzzle.

---

## 9. Phase 6 — Hardening

- **Crash-free target: 99.5%.** Add Firebase Crashlytics (free tier) or accept Play Console's built-in Android Vitals. Crashlytics is worth the SDK weight.
- **Minimal analytics** — enough to answer: D1/D7 retention, puzzles started vs. completed, hint usage, ad impressions, IAP conversion. Nothing more. Respect the Play Data Safety declaration you'll file in Phase 8.
- **ProGuard/R8 enabled for release**, with keep rules for Room entities, Billing, and Ads. Verify the release build actually runs — R8 breakage that only appears in release builds is a classic late-stage disaster.
- **Test on a genuinely low-end device profile** (2 GB RAM, Android 8). This is your median user, not a flagship.
- Accessibility pass: TalkBack labels on all controls; the grid should announce cell coordinates and state.
- Handle: rotation, split-screen, back-gesture, process death, low memory.

### Phase 6 acceptance criteria

- [ ] Release build (minified) runs correctly through a full puzzle, purchase, and ad cycle.
- [ ] No crashes across a 30-minute manual soak covering every screen.
- [ ] App size under 15 MB.
- [ ] Cold start under 2 seconds on the low-end profile.

---

## 10. Phase 7 — Store assets

Produced as files in `store/`, not uploaded by the agent.

- **App icon** — 512×512. The single highest-leverage asset you will make. It determines install rate more than the app does. Make it a recognisable nonogram grid motif, legible at 48px.
- **Feature graphic** — 1024×500.
- **Screenshots** — 6 to 8, phone size. First two matter most: show a completed colourful puzzle and the daily streak screen. Add short text overlays naming the benefit ("5,000 puzzles", "Play offline", "New puzzle every day").
- **Short description** (80 chars) and **full description** (4,000 chars). Front-load the searched keywords naturally: nonogram, picross, griddler, logic puzzle, hanjie. Write for humans; keyword-stuffed descriptions read badly and don't help much.
- **Privacy policy** — required because you serve ads. A static page on GitHub Pages is acceptable and free. It must accurately describe AdMob's data collection.

---

## 11. Phase 8 — Publishing (human-operated)

> The coding agent cannot do this phase. It involves a Play Console account, real testers, and identity documents.

### 11.1 Account setup — do this in parallel with Phase 1, not at the end

1. Create a Play Console developer account, $25 one-time.
2. Complete identity verification (government ID). This can take days.
3. **Decide account type now.** Personal accounts created after 13 November 2023 must complete closed testing before production. Organization accounts with a D-U-N-S number are exempt but verification takes 2–4 weeks. For a solo developer, personal + closed testing is usually the faster path.
4. Set up the payments profile. **The bank account must be registered in the same country as the payments profile, and the profile country cannot be changed afterwards.** Bangladesh is supported for wire transfer payouts. Get this right the first time.
5. Set up AdMob, link it to the Play Console app, and start address verification (PIN by post) early — it's slow and blocks your first payout, which has a $100 threshold.

### 11.2 Closed testing — start recruiting during Phase 3

The requirement: **12 testers, opted in continuously for 14 days, with genuine engagement.** Opt-in alone is not sufficient; Google evaluates whether testers actually used the app.

- Recruit **15** to land 12 — expect dropouts.
- Sources: classmates and juniors, local Android/Flutter developer groups, r/androiddev testing threads, family members with their own devices, reciprocal testing communities.
- Send each tester a short message explaining exactly what to do: install, play a puzzle, open it again on a few different days.
- Push a couple of real updates during the 14 days based on their feedback — Google views that favourably.
- **Do not use emulators or fake accounts.** That risks permanent account suspension. If you use a paid tester service, verify it uses real physical devices.
- After 14 days, apply for production access. Review is typically up to 7 days but can run longer.

**Realistic total: 3–6 weeks from first tester to production approval.** Plan for it.

### 11.3 Pre-upload checklist

- [ ] App signing key generated and **backed up in two places** (losing it means you can never update the app)
- [ ] Play App Signing enrolled
- [ ] `versionCode` / `versionName` set
- [ ] Target SDK meets Play's current minimum
- [ ] Data Safety form completed accurately (AdMob collects device identifiers — declare it)
- [ ] Content rating questionnaire completed
- [ ] Ads declaration set to "contains ads"
- [ ] Privacy policy URL live and reachable
- [ ] Release build tested on a physical device
- [ ] Store listing complete in English; consider adding Bengali, Spanish, Portuguese, Indonesian later

### 11.4 Launch

Roll out to production at 20%, watch Android Vitals and crash rate for 48 hours, then go to 100%.

---

## 12. Post-launch: milestones and kill criteria

Decide these now, while you're not emotionally invested in the outcome.

| Checkpoint | Signal | Action |
|---|---|---|
| Day 30 | D1 retention < 25% | Core loop problem. Fix the game before anything else. |
| Day 30 | D1 ≥ 25% | Loop works. Move to distribution. |
| Day 60 | Organic installs < 20/day | ASO problem. Replace icon and first two screenshots first (cheap). Then reconsider the keyword niche. |
| Day 90 | D7 ≥ 10% and installs growing | Invest: more puzzle types, themes, a second app in the same family. |
| Day 90 | Both flat | Stop polishing. Ship game two with what you learned. |

**Expect very little revenue from this app.** Casual Android rewarded eCPM has been drifting down (roughly $3.60 → $3.02 between H1 2023 and H1 2025), and emerging-market rates sit at the bottom of the $3–8 band. A niche puzzle app that lands its ASO might reach a few hundred to a couple of thousand DAU in six months — tens of dollars a month, not a salary.

**That is the expected outcome and it is not failure.** What this project actually buys you is a working pipeline: generator, publishing, closed testing, ad integration, billing, payouts, ASO. Game two is where that pipeline pays. Plan for three small games over 18 months, not one perfect game.

---

## 13. Quick reference

```bash
./gradlew :app:testDebugUnitTest          # unit tests
./gradlew :app:assembleDebug              # debug build
./gradlew :app:assembleRelease            # release build (verify R8 output runs)
./gradlew :app:bundleRelease              # AAB for Play upload
./gradlew generatePuzzlePack              # regenerate bundled puzzles (Phase 1 tool)
```

**Phase order is not negotiable: 1 → 2 → 3 → 4 → 5 → 6 → 7.** Phase 8 human work (account setup, tester recruitment) runs in parallel starting at Phase 1.
