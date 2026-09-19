package com.ganim.nonogram.puzzle.model

/**
 * State of a single cell.
 *
 * Declaration order is fixed by the build plan (§4.1) and must not be reordered:
 * nothing persists `ordinal`, but the plan names this enum as part of the data model.
 *
 * Note the distinction between [EMPTY] and [CROSSED]. Both mean "this cell is not
 * filled", and the solver treats them identically. They differ only in provenance:
 * [EMPTY] is a deduction, [CROSSED] is the player's own note. Keeping them separate
 * means running the solver over a player's in-progress board (for hints, §5.4) never
 * destroys their crosses.
 */
enum class CellState {
    EMPTY,
    FILLED,
    CROSSED,
    UNKNOWN;

    /** True when this cell is known not to be filled, however that was established. */
    val isKnownEmpty: Boolean get() = this == EMPTY || this == CROSSED

    /** True when the cell's true value is known. */
    val isKnown: Boolean get() = this != UNKNOWN
}
