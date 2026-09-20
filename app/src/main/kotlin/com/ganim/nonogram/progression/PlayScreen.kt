package com.ganim.nonogram.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.nonogram.daily.difficultyTint
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.ui.components.Chip
import com.ganim.nonogram.ui.components.GhostButton
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.Meter
import com.ganim.nonogram.ui.components.Panel
import com.ganim.nonogram.ui.components.PrimaryButton
import com.ganim.nonogram.ui.theme.LocalBoardColors

/**
 * The home screen: a ladder of numbered levels.
 *
 * This replaced the daily puzzle as the app's landing screen after the first real tester
 * could not find where the game started. The diagnosis was not that the daily screen was
 * badly built - it was that it opened on a concept. "Today's puzzle" assumes you already
 * play; "Level 1" assumes nothing.
 *
 * So the screen answers one question before any other: **what do I tap?** The continue
 * card is the answer, it is the first thing on the page, and it is the only bevelled
 * button here. Everything below it is for the player who wants to choose instead.
 */
@Composable
fun PlayScreen(
    viewModel: PlayViewModel,
    onPlay: (puzzleId: String) -> Unit,
    onHowToPlay: () -> Unit,
    onPictures: () -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current

    LazyColumn(
        modifier.fillMaxSize().background(colors.boardBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "Nonogram",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.clueText,
                )
                Text(
                    if (state.solved == 0) {
                        "Fill the squares the numbers describe."
                    } else {
                        "${state.solved} of ${state.total} levels solved"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }

        // Before anything has been solved, the walkthrough is the more useful first tap
        // of the two - so it sits above the ladder rather than in Settings where the
        // tester never looked.
        if (state.solved == 0) {
            item { HowToPlayCard(onHowToPlay) }
        }

        state.next?.let { next ->
            item { ContinueCard(next, state.solved == 0) { onPlay(next.level.id) } }
        }

        if (state.loaded && state.next == null) {
            item { AllClearCard() }
        }

        items(state.stages, key = { it.stage.name }) { stage ->
            StageSection(stage, onPlay)
        }

        // Everything the ladder does not cover, at the bottom where somebody who has
        // run out of levels will look for it.
        item { PicturesSection(onPictures) }

        item {
            FreePlaySection(
                free = state.freePlay,
                onSize = viewModel::setFreeSize,
                onDifficulty = viewModel::setFreeDifficulty,
                onPlayOne = { viewModel.pickFreePuzzle()?.let(onPlay) },
                onBrowseAll = onBrowseAll,
            )
        }
    }
}

@Composable
private fun PicturesSection(onOpen: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.collection, onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.IMAGE, colors.collection, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "Pictures",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "Twenty-three drawn by hand. Each one turns into something.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, colors.collection, size = 20.dp)
        }
    }
}

/**
 * The other five thousand.
 *
 * They used to be a tab of their own, which was redundant next to the ladder and wrong
 * about what they are: five thousand puzzles is a *supply*, not a catalogue. Nobody
 * scrolls that many thumbnails hunting for one. So the unit is a bucket - a size and a
 * level - and the action is "play one", with the grid kept behind a link for the rare
 * player who genuinely wants to browse.
 */
@Composable
private fun FreePlaySection(
    free: FreePlayUi,
    onSize: (Int) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onPlayOne: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Text(
            "Free play",
            style = MaterialTheme.typography.titleMedium,
            color = colors.clueText,
        )
        Text(
            "Five thousand more. Pick a size and a level.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textMuted,
        )

        Box(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            free.sizes.forEach { size ->
                Pick(
                    label = "$size × $size",
                    selected = size == free.size,
                    tint = colors.info,
                    modifier = Modifier.weight(1f),
                ) { onSize(size) }
            }
        }

        Box(Modifier.height(7.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            free.difficulties.forEach { difficulty ->
                Pick(
                    label = difficulty.name.lowercase(),
                    selected = difficulty == free.difficulty,
                    tint = difficultyTint(difficulty),
                    modifier = Modifier.weight(1f),
                ) { onDifficulty(difficulty) }
            }
            // The pack ships fewer levels at some sizes than others, so the row would
            // otherwise stretch two chips across the width and look like a different
            // control.
            repeat(MAX_LEVELS_PER_SIZE - free.difficulties.size) { Box(Modifier.weight(1f)) }
        }

        Box(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (free.allSolved) {
                    "All ${free.total} solved"
                } else {
                    "${free.solved} of ${free.total} solved"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (free.allSolved) colors.success else colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "Browse all",
                onClick = onBrowseAll,
                glyph = Glyph.GRID,
            )
        }

        Box(Modifier.height(10.dp))

        PrimaryButton(
            text = "Play a random one",
            onClick = onPlayOne,
            modifier = Modifier.fillMaxWidth(),
            glyph = Glyph.SPARK,
        )
    }
}

@Composable
private fun Pick(
    label: String,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) tint else Color.Transparent)
            .border(1.dp, if (selected) colors.accentDeep else colors.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onAccentFill else colors.textMuted,
            maxLines = 1,
        )
    }
}

/** The widest level row any size has, so narrower ones keep the same chip width. */
private const val MAX_LEVELS_PER_SIZE = 2

@Composable
private fun HowToPlayCard(onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.info, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.SPARK, colors.info, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "New here? Watch one get solved",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "Ninety seconds, one deduction at a time.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, colors.info, size = 20.dp)
        }
    }
}

/**
 * The one tap this screen exists to offer.
 *
 * The wording changes on the very first run - "Start here" rather than "Continue" -
 * because a player with nothing solved is not continuing anything, and being told they
 * are is the small kind of wrong that makes an app feel like it is not talking to you.
 */
@Composable
private fun ContinueCard(next: LevelUi, firstRun: Boolean, onPlay: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(next.level.stage.title.uppercase(), colors.accent)
            Chip("${next.level.size} × ${next.level.size}", colors.info)
            if (next.started) Chip("in progress", colors.success)
        }

        Box(Modifier.height(12.dp))

        Text(
            "Level ${next.level.number}",
            style = MaterialTheme.typography.displaySmall,
            color = colors.clueText,
        )

        Box(Modifier.height(4.dp))

        Text(
            when {
                next.started -> "Pick up where you left off."
                firstRun -> "Tap below and fill in the squares the numbers describe."
                else -> next.level.stage.blurb
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )

        Box(Modifier.height(16.dp))

        PrimaryButton(
            text = when {
                next.started -> "Resume"
                firstRun -> "Start here"
                else -> "Play"
            },
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AllClearCard() {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.success) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.TROPHY, colors.success, size = 24.dp)
            Column {
                Text(
                    "Ladder cleared",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "Every level solved. The archive has 5,000 more.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StageSection(stage: StageUi, onPlay: (String) -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        stage.stage.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.clueText,
                    )
                    if (stage.cleared) {
                        GameIcon(Glyph.CHECK, colors.success, size = 17.dp)
                    }
                }
                Text(
                    stage.stage.blurb,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            Text(
                "${stage.completedCount}/${stage.total}",
                style = MaterialTheme.typography.titleMedium,
                color = if (stage.cleared) colors.success else colors.textMuted,
            )
        }

        Box(Modifier.height(10.dp))
        Meter(stage.fraction, if (stage.cleared) colors.success else colors.accent)
        Box(Modifier.height(12.dp))

        // Laid out by hand in rows of five rather than with a nested lazy grid, which
        // cannot be measured inside a LazyColumn item without a fixed height.
        stage.levels.chunked(LEVELS_PER_ROW).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { level ->
                    LevelTile(level, Modifier.weight(1f)) { onPlay(level.level.id) }
                }
                // Keeps the last row's tiles the same size as every other row's.
                repeat(LEVELS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One level.
 *
 * Three states worth distinguishing and no more: solved, the one to play next, and not
 * yet. A started-but-unfinished level is the next one by definition, so it does not need
 * a fourth look.
 */
@Composable
private fun LevelTile(level: LevelUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        level.completed -> colors.success
        level.isNext -> colors.accentFill
        else -> Color.Transparent
    }
    val content = when {
        level.completed || level.isNext -> colors.onAccentFill
        else -> colors.textMuted
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = when {
                    level.completed -> colors.success
                    level.isNext -> colors.accentDeep
                    else -> colors.stroke
                },
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (level.completed) {
            GameIcon(
                Glyph.CHECK,
                content,
                size = 18.dp,
                contentDescription = "Level ${level.level.number}, solved",
            )
        } else {
            Text(
                level.level.numberInStage.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val LEVELS_PER_ROW = 5
