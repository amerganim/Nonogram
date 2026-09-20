package com.ganim.nonogram.archive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.nonogram.daily.difficultyTint
import com.ganim.nonogram.data.repo.PuzzleCollection
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.Meter
import com.ganim.nonogram.ui.theme.LocalBoardColors

/**
 * The archive (build plan 6.4).
 *
 * > "Browse all 5,000 puzzles, filterable by size, difficulty, and completion state.
 * > Grid of small thumbnails; completed puzzles show their revealed picture, incomplete
 * > show a lock-free placeholder. Nothing is paywalled or locked."
 *
 * Scrolling 5,000 entries without jank is the acceptance criterion, so nothing here
 * decodes a grid until its thumbnail is actually on screen, and the filtered list comes
 * from a pre-built index of record headers rather than from decoded puzzles.
 *
 * The Pictures tab is the second collection: hand-drawn grids that resolve into a
 * recognisable thing. Their names stay hidden until they are solved - a thumbnail that
 * announced "Cat" would give away the answer, and the recognition is the whole point.
 */
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onOpen: (puzzleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current
    val showingPictures = state.collection == PuzzleCollection.PICTURE
    val tabTint = if (showingPictures) colors.collection else colors.accent

    Column(modifier.fillMaxSize().background(colors.boardBackground)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                if (showingPictures) "Pictures" else "Archive",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.clueText,
            )
            Text(
                if (showingPictures) {
                    "Hand-drawn grids. Names stay hidden until you solve them."
                } else {
                    "Every puzzle, unlocked, forever."
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
            )

            Box(Modifier.height(12.dp))

            CollectionTabs(
                selected = state.collection,
                pictureCount = state.pictureCount,
                onSelect = viewModel::setCollection,
            )

            Box(Modifier.height(12.dp))

            if (showingPictures) {
                // A meter, not a count: with twenty-three entries the interesting
                // question is how many are left, not how to narrow them down.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.completedHere} of ${state.total} revealed",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.clueText,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${percent(state.completedHere, state.total)}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.collection,
                    )
                }
                Box(Modifier.height(8.dp))
                Meter(
                    fraction = state.completedHere.toFloat() / state.total.coerceAtLeast(1),
                    tint = colors.collection,
                )
            } else {
                Text(
                    "${state.visible.size} of ${state.total}  ·  ${state.completedCount} solved",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }

            Box(Modifier.height(6.dp))

            // Twenty-three drawings fit on two screens, so size and difficulty chips
            // would only be clutter there.
            if (!showingPictures) {
                FilterRow(
                    labels = state.sizes.map { "$it × $it" },
                    values = state.sizes,
                    selected = state.sizeFilter,
                    tints = state.sizes.map { colors.info },
                    onSelect = viewModel::setSizeFilter,
                )
                FilterRow(
                    labels = Difficulty.entries.map { it.name.lowercase() },
                    values = Difficulty.entries,
                    selected = state.difficultyFilter,
                    tints = Difficulty.entries.map { difficultyTint(it) },
                    onSelect = viewModel::setDifficultyFilter,
                )
            }
            FilterRow(
                labels = CompletionFilter.entries.map { it.label },
                values = CompletionFilter.entries,
                selected = state.completionFilter,
                tints = CompletionFilter.entries.map { tabTint },
                onSelect = viewModel::setCompletionFilter,
                allowNone = false,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.visible, key = { it.id }) { entry ->
                ArchiveThumbnail(
                    entry = entry,
                    // Only a visible thumbnail decodes its grid.
                    solution = remember(entry.id, entry.completed) {
                        if (entry.completed) viewModel.solutionFor(entry) else null
                    },
                    onClick = { onOpen(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun CollectionTabs(
    selected: PuzzleCollection,
    pictureCount: Int,
    onSelect: (PuzzleCollection) -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.stroke, shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Tab(
            label = "Puzzles",
            glyph = Glyph.GRID,
            active = selected == PuzzleCollection.GENERATED,
            tint = colors.accent,
            modifier = Modifier.weight(1f),
        ) { onSelect(PuzzleCollection.GENERATED) }
        Tab(
            label = "Pictures ($pictureCount)",
            glyph = Glyph.IMAGE,
            active = selected == PuzzleCollection.PICTURE,
            tint = colors.collection,
            modifier = Modifier.weight(1f),
        ) { onSelect(PuzzleCollection.PICTURE) }
    }
}

@Composable
private fun Tab(
    label: String,
    glyph: Glyph,
    active: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(15.dp)
    val content = if (active) colors.onAccent else colors.textMuted
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (active) tint else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(glyph, content, size = 17.dp)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> FilterRow(
    labels: List<String>,
    values: List<T>,
    selected: T?,
    tints: List<Color>,
    onSelect: (T?) -> Unit,
    allowNone: Boolean = true,
) {
    val colors = LocalBoardColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (allowNone) {
            FilterChip("All", selected == null, colors.accent) { onSelect(null) }
        }
        values.forEachIndexed { i, value ->
            FilterChip(labels[i], selected == value, tints.getOrElse(i) { colors.accent }) {
                onSelect(if (selected == value && allowNone) null else value)
            }
        }
    }
}

/**
 * A filter chip.
 *
 * Selected wears the filter's own colour rather than one shared highlight, so a row of
 * difficulty chips teaches the difficulty palette just by being looked at - and the
 * `expert` chip on this screen is the same red as the last life on the play screen.
 */
@Composable
private fun FilterChip(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(38.dp)
            .clip(shape)
            .background(if (selected) tint else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.stroke, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onAccent else colors.textMuted,
            maxLines = 1,
        )
    }
}

/**
 * One archive tile.
 *
 * A completed puzzle shows the picture it revealed, which is the reward for finishing it
 * and the reason to scroll the archive at all. An unfinished one shows a plain grid
 * outline - no lock, no teaser, because nothing here is gated.
 */
@Composable
private fun ArchiveThumbnail(
    entry: ArchiveItem,
    solution: BooleanArray?,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val revealed = entry.completed && entry.name.isNotEmpty()
    val tint = if (entry.collection == PuzzleCollection.PICTURE) colors.collection else colors.accent
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .clip(shape)
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = if (entry.completed) tint.copy(alpha = EDGE_ALPHA) else colors.stroke,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val cells = entry.size
                val cell = minOf(size.width, size.height) / cells
                if (solution == null) {
                    // Placeholder: the grid's shape, nothing about its content.
                    for (i in 0..cells) {
                        val at = i * cell
                        drawLine(colors.gridLine, Offset(at, 0f), Offset(at, cell * cells), 1f)
                        drawLine(colors.gridLine, Offset(0f, at), Offset(cell * cells, at), 1f)
                    }
                } else {
                    for (row in 0 until cells) {
                        for (col in 0 until cells) {
                            if (solution[row * cells + col]) {
                                drawRect(tint, Offset(col * cell, row * cell), Size(cell, cell))
                            }
                        }
                    }
                }
            }
            if (entry.inProgress) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(colors.info)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        "···",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onAccent,
                    )
                }
            }
        }
        Box(Modifier.height(6.dp))
        // The name is a reward, not a label: revealed once the picture is solved.
        Text(
            if (revealed) entry.name else "${entry.size} × ${entry.size}",
            style = MaterialTheme.typography.labelMedium,
            color = if (revealed) tint else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun percent(part: Int, whole: Int): Int =
    if (whole <= 0) 0 else (part * 100) / whole

private const val EDGE_ALPHA = 0.5f
