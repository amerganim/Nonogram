package com.ganim.nonogram.puzzle.pictures

/**
 * Hand-drawn pictures, as opposed to the generated puzzles.
 *
 * ## Why these exist
 *
 * The generator shapes its grids into contiguous blobs (build plan 4.4, "so completed
 * puzzles look like *something*"), and they do read as organic shapes. But a blob is not
 * a *picture*. Finishing one gives you a satisfying silhouette and no moment of
 * recognition, and recognition is the actual payoff of a nonogram - the reason anyone
 * fills in the last square rather than closing the app.
 *
 * So these are drawn by hand, one at a time, and named. Completing one tells you what
 * you made.
 *
 * ## The catch
 *
 * A picture being nice to look at has nothing to do with being solvable. Every design
 * here still has to pass the same bar as a generated puzzle: solvable by logic alone,
 * with exactly one solution (4.3). A drawing that fails is not shipped - see
 * `PictureLibraryTest`, which checks every single one, and the notes in
 * docs/picture-puzzles.md for the ones that had to be redrawn.
 *
 * Pure Kotlin, no Android, so the generation tool and the tests can use it directly.
 */
object PictureLibrary {

    /** One drawing: a name the player is shown, and the grid itself. */
    data class Picture(val name: String, val rows: List<String>) {
        val size: Int get() = rows.size

        init {
            require(rows.isNotEmpty()) { "$name has no rows" }
            require(rows.all { it.length == rows.size }) {
                "$name is not square: ${rows.size} rows, widths ${rows.map { it.length }.distinct()}"
            }
            require(rows.all { row -> row.all { it == '#' || it == '.' } }) {
                "$name uses characters other than # and ."
            }
        }

        fun cells(): BooleanArray {
            val n = rows.size
            return BooleanArray(n * n) { i -> rows[i / n][i % n] == '#' }
        }
    }

    private fun picture(name: String, vararg rows: String) = Picture(name, rows.toList())

    val all: List<Picture> = listOf(

        // --- 10x10 -----------------------------------------------------------------

        picture(
            "Heart",
            "..........",
            "..##..##..",
            ".########.",
            ".########.",
            ".########.",
            "..######..",
            "...####...",
            "....##....",
            "..........",
            "..........",
        ),

        picture(
            "House",
            "....##....",
            "...####...",
            "..######..",
            ".########.",
            "##########",
            ".#......#.",
            ".#.####.#.",
            ".#.####.#.",
            ".#.####.#.",
            ".########.",
        ),

        picture(
            "Sailboat",
            "....#.....",
            "....##....",
            "....###...",
            "....####..",
            "....#####.",
            "....#.....",
            "....#.....",
            ".########.",
            "..######..",
            "..........",
        ),

        picture(
            "Mug",
            "..........",
            "..######..",
            "..#....#..",
            "..#....###",
            "..#....#.#",
            "..#....###",
            "..#....#..",
            "..######..",
            "..........",
            "..........",
        ),

        picture(
            "Key",
            "..........",
            "..####....",
            ".##..##...",
            ".#....#...",
            ".##..##...",
            "..####....",
            "....######",
            "........#.",
            "......#.#.",
            "..........",
        ),

        picture(
            "Tree",
            "....##....",
            "...####...",
            "..######..",
            ".########.",
            "...####...",
            "..######..",
            ".########.",
            "....##....",
            "....##....",
            "..######..",
        ),

        picture(
            "Fish",
            "..........",
            "...####...",
            "..######.#",
            ".#########",
            "##########",
            ".#########",
            "..######.#",
            "...####...",
            "..........",
            "..........",
        ),

        picture(
            "Crown",
            "..........",
            "#........#",
            "##......##",
            "#.#....#.#",
            "#..#..#..#",
            "#..####..#",
            "##########",
            "##########",
            "..........",
            "..........",
        ),

        picture(
            // Redrawn: the first attempt was solvable but read as random blocks. The
            // flap has to be an unbroken V or the eye does not see an envelope.
            "Envelope",
            "..........",
            "##########",
            "##......##",
            "#.##..##.#",
            "#...##...#",
            "#........#",
            "#........#",
            "##########",
            "..........",
            "..........",
        ),

        picture(
            // Redrawn: the first crescent was an outline, and outlines leave the
            // interior underdetermined. Solid shapes constrain their lines far better.
            "Moon",
            "....###...",
            "...####...",
            "..####....",
            "..###.....",
            "..###.....",
            "..###.....",
            "..####....",
            "...####...",
            "....###...",
            "..........",
        ),

        picture(
            // Replaces a teacup that had two valid solutions - pretty, and unshippable.
            "Star",
            "....##....",
            "....##....",
            "...####...",
            "##########",
            ".########.",
            "..######..",
            "..######..",
            ".###..###.",
            ".##....##.",
            "..........",
        ),

        picture(
            "Bell",
            "....##....",
            "...####...",
            "..######..",
            "..######..",
            ".########.",
            ".########.",
            "##########",
            "##########",
            "....##....",
            "..........",
        ),

        picture(
            // Redrawn: the first version was a set of parallel diagonals, which reads
            // as stripes rather than as a leaf. A teardrop silhouette with one vein
            // reads immediately.
            "Leaf",
            "......###.",
            "....#####.",
            "...######.",
            "..#######.",
            ".###.####.",
            ".##.#####.",
            ".#.#####..",
            "..#####...",
            "..###.....",
            ".#........",
        ),

        picture(
            "Ghost",
            "...####...",
            "..######..",
            ".##.##.##.",
            ".##.##.##.",
            ".########.",
            ".########.",
            ".########.",
            ".########.",
            ".#.##.##.#",
            "..........",
        ),

        picture(
            "Apple",
            "....##....",
            "....#.....",
            "..######..",
            ".########.",
            "##########",
            "##########",
            "##########",
            ".########.",
            "..##..##..",
            "..........",
        ),

        picture(
            "Umbrella",
            "..........",
            "...####...",
            "..######..",
            ".########.",
            "##########",
            "....##....",
            "....##....",
            "....##....",
            "...###....",
            "..###.....",
        ),

        picture(
            "Rocket",
            "....##....",
            "...####...",
            "...####...",
            "..######..",
            "..#.##.#..",
            "..######..",
            ".###..###.",
            ".##....##.",
            "....##....",
            "....##....",
        ),

        picture(
            "Music note",
            "......####",
            "......#..#",
            "......####",
            "......#...",
            "......#...",
            "......#...",
            "..#####...",
            ".######...",
            ".#####....",
            "..###.....",
        ),

        // --- 15x15 -----------------------------------------------------------------

        picture(
            "Cat",
            "...............",
            "..##.......##..",
            "..###.....###..",
            "..####...####..",
            "..###########..",
            "..###########..",
            ".#############.",
            ".##.#######.##.",
            ".#############.",
            ".#####...#####.",
            ".####.###.####.",
            ".#############.",
            "..###########..",
            "...#########...",
            "...............",
        ),

        picture(
            // Replaces a flower that read as a lollipop. A five-petal bloom needs more
            // than 15 squares to be legible; an anchor reads at any size.
            "Anchor",
            "......###......",
            ".....##.##.....",
            ".....##.##.....",
            "......###......",
            ".......#.......",
            "..###########..",
            ".......#.......",
            ".......#.......",
            ".#.....#.....#.",
            ".##....#....##.",
            ".###...#...###.",
            "..####...####..",
            "...##########..",
            ".....######....",
            "...............",
        ),

        picture(
            "Butterfly",
            "...............",
            ".###.....###...",
            "#####...#####..",
            "######.######..",
            "#######.######.",
            "###############",
            "..####.#.####..",
            "....##.#.##....",
            "..####.#.####..",
            "###############",
            "#######.######.",
            "######.######..",
            "#####...#####..",
            ".###.....###...",
            "...............",
        ),

        picture(
            "Snowman",
            "......###......",
            ".....#####.....",
            "....#######....",
            "....##...##....",
            "....#.#.#.#....",
            "....##...##....",
            ".....#####.....",
            "...#########...",
            "..###########..",
            "..##.#####.##..",
            "..###########..",
            "..###########..",
            "...#########...",
            ".....#####.....",
            "...............",
        ),

        picture(
            "Mushroom",
            "...............",
            "....#######....",
            "..###########..",
            ".#####...#####.",
            "###...#####..##",
            "###############",
            ".#############.",
            "..###########..",
            ".....#####.....",
            ".....##.##.....",
            ".....##.##.....",
            ".....##.##.....",
            "....#######....",
            "...............",
            "...............",
        ),
    )

    val byName: Map<String, Picture> = all.associateBy { it.name }

    /** Sizes the library draws at, so the archive can offer them as filters. */
    val sizes: List<Int> = all.map { it.size }.distinct().sorted()
}
