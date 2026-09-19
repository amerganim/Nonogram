package com.ganim.nonogram.archive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.nonogram.puzzle.model.Difficulty
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
 */
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onOpen: (puzzleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current

    Column(modifier.fillMaxSize().background(colors.boardBackground)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "${state.visible.size} of ${state.total} puzzles  ·  ${state.completedCount} solved",
                style = MaterialTheme.typography.labelLarge,
                color = colors.clueTextSatisfied,
            )

            FilterRow(
                labels = state.sizes.map { "${it}x$it" },
                values = state.sizes,
                selected = state.sizeFilter,
                onSelect = viewModel::setSizeFilter,
            )
            FilterRow(
                labels = Difficulty.entries.map { it.name.lowercase() },
                values = Difficulty.entries,
                selected = state.difficultyFilter,
                onSelect = viewModel::setDifficultyFilter,
            )
            FilterRow(
                labels = CompletionFilter.entries.map { it.label },
                values = CompletionFilter.entries,
                selected = state.completionFilter,
                onSelect = viewModel::setCompletionFilter,
                allowNone = false,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visible, key = { it.id }) { entry ->
                ArchiveThumbnail(
                    entry = entry,
                    // Only a visible thumbnail decodes its grid.
                    solution = remember(entry.id) {
                        if (entry.completed) viewModel.solutionFor(entry.index) else null
                    },
                    onClick = { onOpen(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun <T> FilterRow(
    labels: List<String>,
    values: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    allowNone: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (allowNone) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        values.forEachIndexed { i, value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value && allowNone) null else value) },
                label = { Text(labels[i]) },
            )
        }
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
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(colors.cellEmpty)
            .padding(6.dp),
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
                                drawRect(
                                    colors.cellFilled,
                                    Offset(col * cell, row * cell),
                                    Size(cell, cell),
                                )
                            }
                        }
                    }
                }
            }
            if (entry.inProgress) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent)
                        .padding(horizontal = 4.dp),
                ) {
                    Text("···", style = MaterialTheme.typography.labelLarge, color = colors.boardBackground)
                }
            }
        }
        Text(
            "${entry.size}x${entry.size}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.clueTextSatisfied,
        )
    }
}
