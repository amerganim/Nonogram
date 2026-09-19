package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.CellState.CROSSED
import com.ganim.nonogram.puzzle.model.CellState.EMPTY
import com.ganim.nonogram.puzzle.model.CellState.FILLED
import com.ganim.nonogram.puzzle.model.CellState.UNKNOWN
import com.ganim.nonogram.puzzle.model.Clue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Build plan 4.2 acceptance: "Must be deterministic and must never make an incorrect
 * deduction - write property tests that generate random valid lines and assert the
 * solver never contradicts the truth."
 */
class LineSolverTest {

    private val solver = LineSolver()

    // --- worked examples -------------------------------------------------------------

    @Test
    fun `overlap in a blank line forces the shared middle`() {
        // A block of 3 in 5 cells must cover cell 2 wherever it starts.
        solver.solve(Clue.of(3), blank(5)) shouldBe
            listOf(UNKNOWN, UNKNOWN, FILLED, UNKNOWN, UNKNOWN)
    }

    @Test
    fun `a block that exactly fills the line is fully forced`() {
        solver.solve(Clue.of(5), blank(5)) shouldBe List(5) { FILLED }
    }

    @Test
    fun `an empty clue empties the whole line`() {
        solver.solve(Clue.EMPTY, blank(4)) shouldBe List(4) { EMPTY }
    }

    @Test
    fun `clue plus separators exactly filling the line is fully forced`() {
        solver.solve(Clue.of(2, 2), blank(5)) shouldBe listOf(FILLED, FILLED, EMPTY, FILLED, FILLED)
    }

    @Test
    fun `a known filled cell pins an otherwise free block`() {
        val current = listOf(FILLED, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN)
        solver.solve(Clue.of(2), current) shouldBe listOf(FILLED, FILLED, EMPTY, EMPTY, EMPTY)
    }

    @Test
    fun `a known empty cell splits the line`() {
        // With cell 2 empty, a block of 3 can only sit in cells 3..5.
        val current = listOf(UNKNOWN, UNKNOWN, EMPTY, UNKNOWN, UNKNOWN, UNKNOWN)
        solver.solve(Clue.of(3), current) shouldBe
            listOf(EMPTY, EMPTY, EMPTY, FILLED, FILLED, FILLED)
    }

    @Test
    fun `nothing is forced when the clue is loose`() {
        solver.solve(Clue.of(1), blank(5)) shouldBe List(5) { UNKNOWN }
    }

    // --- contradictions --------------------------------------------------------------

    @Test
    fun `a clue too long for the line is a contradiction`() {
        solver.solveOrNull(Clue.of(3, 3), blank(5)).shouldBeNull()
    }

    @Test
    fun `a filled cell that no placement can cover is a contradiction`() {
        solver.solveOrNull(Clue.EMPTY, listOf(UNKNOWN, FILLED, UNKNOWN)).shouldBeNull()
    }

    @Test
    fun `crossing out every cell a block needs is a contradiction`() {
        val current = listOf(CROSSED, CROSSED, CROSSED, CROSSED, UNKNOWN)
        solver.solveOrNull(Clue.of(3), current).shouldBeNull()
    }

    // --- player-state handling -------------------------------------------------------

    @Test
    @DisplayName("a player's crosses survive deduction rather than becoming EMPTY")
    fun `crosses are preserved`() {
        val current = listOf(CROSSED, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN)
        val result = solver.solve(Clue.of(4), current)
        result shouldBe listOf(CROSSED, FILLED, FILLED, FILLED, FILLED)
    }

    @Test
    fun `a cross constrains exactly like a deduced empty`() {
        val withCross = solver.solve(Clue.of(3), listOf(UNKNOWN, UNKNOWN, CROSSED, UNKNOWN, UNKNOWN, UNKNOWN))
        val withEmpty = solver.solve(Clue.of(3), listOf(UNKNOWN, UNKNOWN, EMPTY, UNKNOWN, UNKNOWN, UNKNOWN))
        withCross.map { if (it == CROSSED) EMPTY else it } shouldBe withEmpty
    }

    // --- determinism -----------------------------------------------------------------

    @Test
    fun `repeated calls and cache hits give identical results`() {
        val rng = Random(20260919)
        val shared = LineSolver()
        repeat(2_000) {
            val n = rng.nextInt(1, 21)
            val (clue, current) = randomCase(rng, n)
            val first = shared.solveOrNull(clue, current)
            val second = shared.solveOrNull(clue, current)
            val fresh = LineSolver().solveOrNull(clue, current)
            assertEquals(first, second, "cached call differed for $clue over $current")
            assertEquals(first, fresh, "cold solver differed for $clue over $current")
        }
    }

    @Test
    fun `deduction is idempotent`() {
        val rng = Random(7)
        repeat(2_000) {
            val n = rng.nextInt(1, 21)
            val (clue, current) = randomCase(rng, n)
            val once = solver.solveOrNull(clue, current) ?: return@repeat
            val twice = solver.solveOrNull(clue, once)
            assertEquals(once, twice, "re-solving changed the line for $clue over $current")
        }
    }

    // --- property: never contradicts the truth ---------------------------------------

    @Test
    @DisplayName("over 20,000 random lines, no deduction ever contradicts the real line")
    fun `deductions are sound`() {
        val rng = Random(424242)
        var casesWithDeductions = 0

        repeat(20_000) {
            val n = rng.nextInt(1, 21)
            val truth = BooleanArray(n) { rng.nextBoolean() }
            val clue = Clue.fromLine(truth)

            // Reveal a random, always-correct subset of the true line.
            val current = List(n) { i ->
                when {
                    rng.nextInt(100) >= 35 -> UNKNOWN
                    truth[i] -> FILLED
                    rng.nextBoolean() -> EMPTY
                    else -> CROSSED
                }
            }

            val deduced = solver.solveOrNull(clue, current)
            requireNotNull(deduced) {
                "solver called a satisfiable line contradictory: clue=$clue current=$current truth=${truth.toList()}"
            }

            for (i in 0 until n) {
                when (deduced[i]) {
                    FILLED -> assertEquals(true, truth[i], soundnessMessage(i, clue, current, truth, deduced))
                    EMPTY, CROSSED -> assertEquals(false, truth[i], soundnessMessage(i, clue, current, truth, deduced))
                    UNKNOWN -> Unit
                }
            }
            if (deduced.count { it == UNKNOWN } < current.count { it == UNKNOWN }) casesWithDeductions++
        }

        // Guard against a solver that is "sound" only because it deduces nothing.
        assert(casesWithDeductions > 10_000) {
            "only $casesWithDeductions of 20,000 cases produced any deduction - solver looks inert"
        }
    }

    // --- property: agrees exactly with enumeration ------------------------------------

    @Test
    @DisplayName("matches brute-force enumeration on every line up to 7 cells")
    fun `exhaustive agreement with brute force`() {
        for (n in 1..7) {
            for (clue in allCluesFor(n)) {
                forEachState(n) { current ->
                    val expected = BruteForceLineSolver.solveOrNull(clue, current)
                    val actual = solver.solveOrNull(clue, current)
                    assertEquals(expected, actual, "clue=$clue current=$current")
                }
            }
        }
    }

    @Test
    @DisplayName("matches brute-force enumeration on random lines up to 20 cells")
    fun `random agreement with brute force`() {
        val rng = Random(31337)
        repeat(20_000) {
            val n = rng.nextInt(1, 21)
            val (clue, current) = randomCase(rng, n)
            val expected = BruteForceLineSolver.solveOrNull(clue, current)
            val actual = solver.solveOrNull(clue, current)
            assertEquals(expected, actual, "clue=$clue current=$current")
        }
    }

    // --- helpers ---------------------------------------------------------------------

    private fun blank(n: Int) = List(n) { UNKNOWN }

    /** A clue derived from a real line, paired with an arbitrary - possibly wrong - board state. */
    private fun randomCase(rng: Random, n: Int): Pair<Clue, List<CellState>> {
        val truth = BooleanArray(n) { rng.nextBoolean() }
        val clue = Clue.fromLine(truth)
        val current = List(n) { i ->
            when (rng.nextInt(10)) {
                0, 1 -> FILLED
                2 -> EMPTY
                3 -> CROSSED
                4, 5 -> if (truth[i]) FILLED else EMPTY
                else -> UNKNOWN
            }
        }
        return clue to current
    }

    private fun soundnessMessage(
        i: Int,
        clue: Clue,
        current: List<CellState>,
        truth: BooleanArray,
        deduced: List<CellState>,
    ): String = buildString {
        appendLine("Unsound deduction at cell $i")
        appendLine("clue    = $clue")
        appendLine("truth   = ${truth.joinToString("") { if (it) "#" else "." }}")
        appendLine("current = ${current.joinToString("") { symbol(it) }}")
        appendLine("deduced = ${deduced.joinToString("") { symbol(it) }}")
    }

    private fun symbol(state: CellState) = when (state) {
        FILLED -> "#"
        EMPTY -> "-"
        CROSSED -> "x"
        UNKNOWN -> "?"
    }

    /** Every clue that could possibly fit in a line of [n] cells. */
    private fun allCluesFor(n: Int): List<Clue> {
        val out = ArrayList<Clue>()
        fun build(prefix: List<Int>, used: Int) {
            out.add(Clue(prefix))
            val base = if (prefix.isEmpty()) 0 else used + 1
            var len = 1
            while (base + len <= n) {
                build(prefix + len, base + len)
                len++
            }
        }
        build(emptyList(), 0)
        return out
    }

    /** Visits all 3^n combinations of UNKNOWN / FILLED / EMPTY for a line of [n] cells. */
    private fun forEachState(n: Int, block: (List<CellState>) -> Unit) {
        val options = listOf(UNKNOWN, FILLED, EMPTY)
        val indices = IntArray(n)
        while (true) {
            block(List(n) { options[indices[it]] })
            var i = 0
            while (i < n) {
                indices[i]++
                if (indices[i] < 3) break
                indices[i] = 0
                i++
            }
            if (i == n) return
        }
    }
}
