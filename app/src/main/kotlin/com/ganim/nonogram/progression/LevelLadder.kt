package com.ganim.nonogram.progression

import com.ganim.nonogram.data.repo.ArchiveEntry
import com.ganim.nonogram.puzzle.model.Difficulty

/**
 * The ordered path through the game.
 *
 * ## Why this exists
 *
 * The app used to open on the daily puzzle. A returning player reads that as "today's
 * puzzle"; a new one reads it as nothing at all. The first real tester could not work
 * out where the game started, and could not work out what "daily" meant - both of which
 * are the same failure: the home screen opened on a *concept* instead of on an action.
 *
 * So there is now a ladder. Numbered levels, easiest first, in an order somebody can
 * follow without being told. "Level 1" needs no explanation; "Sunday, 10x10, medium"
 * needs a paragraph.
 *
 * ## Guided, not gated
 *
 * Nothing here is locked. The build plan's promise that "nothing is paywalled or locked"
 * holds, and it is also the better design: a beginner stuck on level 9 can go look at
 * something else instead of being trapped, and a confident player who has solved
 * nonograms before is not made to grind through twelve 5x5s to reach a real puzzle.
 *
 * Ordering is guidance. The [next] level is always one tap away on the home screen, which
 * is what a new player follows; the rest of the ladder is visible below it, which is what
 * everybody else uses.
 *
 * ## Why the stages line up with the pack
 *
 * The generated pack's difficulty bands are relative within a grid size, so an absolute
 * ramp cannot be built from the band alone (see docs/phase1-calibration.md). It can be
 * built from size *and* band together, and conveniently the pack already sorts that way:
 * 5x5 is all easy, 10x10 is easy and medium, 15x15 is medium and hard, 20x20 is hard and
 * expert. The stages below are that, in order.
 *
 * Levels are taken from the front of each pool, and pack order is stable because the pack
 * is a committed, deterministically generated file. So level 7 is the same puzzle on
 * every device and every fresh install - which matters the moment two people compare
 * progress.
 */
object LevelLadder {

    /**
     * One rung of the ladder.
     *
     * [blurb] is shown once, under the stage title. It exists to answer "what changes
     * here?" rather than to decorate: a player who does not know why 15x15 is harder
     * than 10x10 will assume the game got unfair rather than bigger.
     */
    enum class Stage(val title: String, val blurb: String) {
        WARM_UP("Warm up", "Five by five. Small enough to finish in a minute."),
        EASY("Easy", "Ten by ten. The size most puzzles here are."),
        MEDIUM("Medium", "Still ten by ten, but the clues give less away."),
        HARD("Hard", "Fifteen by fifteen. Longer lines, more to hold in your head."),
        EXPERT("Expert", "Twenty by twenty. These take a while, and that is the point."),
    }

    /** One level: where it sits in the ladder, and which puzzle it is. */
    data class Level(
        /** 1-based, within the whole ladder, so the home screen can say "Level 43". */
        val number: Int,
        /** 1-based within its stage, which is what the tile shows. */
        val numberInStage: Int,
        val stage: Stage,
        val entry: ArchiveEntry,
    ) {
        val id: String get() = entry.id
        val size: Int get() = entry.size
    }

    data class StageLevels(val stage: Stage, val levels: List<Level>)

    /**
     * How each stage is filled: the pools to draw from, in order, and how many levels.
     *
     * Counts are deliberately front-loaded. Forty-two levels pass before the ramp leaves
     * the easy end, because the tester's problem was not that the game was too easy.
     */
    private val recipe: List<Triple<Stage, List<Pair<Int, Difficulty>>, Int>> = listOf(
        Triple(Stage.WARM_UP, listOf(5 to Difficulty.EASY), 12),
        Triple(Stage.EASY, listOf(10 to Difficulty.EASY), 30),
        Triple(Stage.MEDIUM, listOf(10 to Difficulty.MEDIUM), 30),
        Triple(Stage.HARD, listOf(15 to Difficulty.MEDIUM, 15 to Difficulty.HARD), 24),
        Triple(Stage.EXPERT, listOf(20 to Difficulty.HARD, 20 to Difficulty.EXPERT), 20),
    )

    /**
     * Builds the ladder from the archive index.
     *
     * A pool that cannot fill its quota contributes what it has rather than failing: a
     * regenerated pack with a different mix should shorten the ladder, not crash the
     * home screen.
     */
    fun build(entries: List<ArchiveEntry>): List<StageLevels> {
        val byPool = entries.groupBy { it.size to it.difficulty }
        var running = 0
        return recipe.map { (stage, pools, quota) ->
            val picked = pools
                .flatMap { byPool[it].orEmpty() }
                .take(quota)
                .mapIndexed { withinStage, entry ->
                    Level(
                        number = ++running,
                        numberInStage = withinStage + 1,
                        stage = stage,
                        entry = entry,
                    )
                }
            StageLevels(stage, picked)
        }
    }

    /**
     * The level to play next: the first one not yet solved.
     *
     * Not "the first one not yet *started*" - a half-finished level is exactly where
     * somebody should be sent back to. And not the last solved plus one, because
     * somebody who jumped ahead to Expert should still be pointed back at the gap they
     * left, rather than marched off the end of the ladder.
     *
     * Null only when every level is solved.
     */
    fun next(stages: List<StageLevels>, completedIds: Set<String>): Level? =
        stages.asSequence()
            .flatMap { it.levels.asSequence() }
            .firstOrNull { it.id !in completedIds }

    /** Every level, flattened, in ladder order. */
    fun allLevels(stages: List<StageLevels>): List<Level> = stages.flatMap { it.levels }
}
