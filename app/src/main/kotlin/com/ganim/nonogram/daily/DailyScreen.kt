package com.ganim.nonogram.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.ui.components.Capsule
import com.ganim.nonogram.ui.components.AccentChip
import com.ganim.nonogram.ui.components.Chip
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.Meter
import com.ganim.nonogram.ui.components.Panel
import com.ganim.nonogram.ui.components.PrimaryButton
import com.ganim.nonogram.ui.components.StatTile
import com.ganim.nonogram.ui.theme.LocalBoardColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The home screen (build plan 6.2, 6.3).
 *
 * Today's puzzle, the streak, then the month. In that order because it is the order of
 * importance: the streak is the reason to come back, and burying it below a calendar
 * would waste the one mechanic that actually drives retention.
 *
 * The streak lives in the header rather than in a tile of its own, because it is the one
 * number a returning player looks for before anything else - and a number you look for
 * first should not be the third thing down the page.
 */
@Composable
fun DailyScreen(
    viewModel: DailyViewModel,
    onPlay: (puzzleId: String, date: LocalDate) -> Unit,
    onOpenPictures: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state)

        // Shown until the habit exists. The first tester could not work out what
        // "daily" meant, and no wonder: the screen showed a date, a grid size and a
        // streak counter, all of which assume you already know the deal. One sentence
        // is cheaper than a tutorial, and it stops appearing once it is redundant.
        if (state.currentStreak == 0 && state.totalCompleted == 0) DailyExplainer()

        TodayCard(
            state = state,
            onPlay = { state.puzzleId?.let { onPlay(it, state.today) } },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Streak", state.currentStreak.toString(), Glyph.FLAME, colors.cellMistake, Modifier.weight(1f))
            StatTile("Best", state.bestStreak.toString(), Glyph.TROPHY, colors.accent, Modifier.weight(1f))
            // "Dailies", not "Solved": this counter only moves for the daily puzzle, and
            // a player who has just solved three from the archive would read "Solved 0"
            // as the app having lost their work. The all-puzzle total is in Settings.
            StatTile("Dailies", state.totalCompleted.toString(), Glyph.CHECK, colors.success, Modifier.weight(1f))
        }

        if (state.pictureCount > 0) PicturesCard(state, onOpenPictures)

        MonthView(
            state = state,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onPickDay = { day ->
                // Future days do not exist yet; everything else is playable with no
                // penalty, which is what drives archive engagement (6.2).
                if (!day.isFuture) {
                    viewModel.puzzleIdFor(day.date)?.let { onPlay(it, day.date) }
                }
            },
        )
    }
}

/** What the daily puzzle actually is, for somebody seeing the word for the first time. */
@Composable
private fun DailyExplainer() {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.info) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.CALENDAR, colors.info, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "One puzzle a day, the same for everyone",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "Solve it and your streak goes up. Miss a day and it resets. " +
                        "Everything else in the app stays open either way.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }
    }
}

/**
 * The locale Compose knows about.
 *
 * `Locale.getDefault()` read during composition is a snapshot: change the phone's
 * language and every date on this screen keeps the old one until the process restarts.
 * Reading it from the configuration makes it observable, so the screen recomposes.
 */
@Composable
private fun currentLocale(): Locale =
    LocalConfiguration.current.locales.get(0)
        // Only reached if the configuration carries no locales at all, which leaves
        // nothing observable to read.
        ?: @Suppress("NonObservableLocale") Locale.getDefault()

@Composable
private fun Header(state: DailyUiState) {
    val colors = LocalBoardColors.current
    val locale = currentLocale()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                state.today.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Text(
                "${state.today.dayOfMonth} " +
                    state.today.month.getDisplayName(TextStyle.FULL, locale),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.clueText,
            )
        }
        Capsule(
            glyph = Glyph.FLAME,
            text = state.currentStreak.toString(),
            tint = colors.cellMistake,
            washed = true,
        )
    }
}

/**
 * The one thing this screen is for.
 *
 * Everything below it is context; this is the action. So it gets the raised surface, the
 * chips that say what you are walking into, and the only bevelled button on the screen.
 */
@Composable
private fun TodayCard(state: DailyUiState, onPlay: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccentChip("TODAY")
            Chip("${state.size} × ${state.size}", colors.info)
            Chip(state.difficulty.name.lowercase(), difficultyTint(state.difficulty))
        }

        Spacer(Modifier.size(12.dp))

        Text(
            when {
                state.todayCompleted -> "Solved today"
                state.todayStarted -> "Still going"
                else -> "Daily puzzle"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = colors.clueText,
        )

        Spacer(Modifier.size(4.dp))

        Text(
            if (state.todayCompleted) {
                "Come back tomorrow, or play it again."
            } else {
                "Three lives. No guessing required."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )

        Spacer(Modifier.size(16.dp))

        PrimaryButton(
            text = when {
                state.todayCompleted -> "Play again"
                state.todayStarted -> "Continue"
                else -> "Play"
            },
            onClick = onPlay,
            enabled = state.puzzleId != null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.freezeProtecting) {
            Spacer(Modifier.size(12.dp))
            // Say it out loud rather than silently spending the freeze - a streak that
            // survived a missed day without explanation just looks like a bug.
            Text(
                "A streak freeze is holding your streak. Play today to keep it.",
                style = MaterialTheme.typography.labelLarge,
                color = colors.cellMistake,
            )
        }
    }
}

/**
 * The picture collection, surfaced where someone will see it.
 *
 * Buried behind an archive tab it would go unfound, and a collection nobody knows about
 * pulls nobody back.
 */
@Composable
private fun PicturesCard(state: DailyUiState, onOpen: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.collection, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.IMAGE, colors.collection, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text("Pictures", style = MaterialTheme.typography.titleMedium, color = colors.clueText)
                Text(
                    "${state.picturesRevealed} of ${state.pictureCount} revealed",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, colors.collection, size = 20.dp)
        }
        Spacer(Modifier.size(10.dp))
        Meter(
            fraction = state.picturesRevealed.toFloat() / state.pictureCount.coerceAtLeast(1),
            tint = colors.collection,
        )
    }
}

@Composable
private fun MonthView(
    state: DailyUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDay: (CalendarDay) -> Unit,
) {
    val colors = LocalBoardColors.current
    val locale = currentLocale()
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonthArrow(Glyph.BACK, "Previous month", enabled = true, onClick = onPrevious)
            Text(
                "${state.month.month.getDisplayName(TextStyle.FULL, locale)} ${state.month.year}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.clueText,
            )
            MonthArrow(
                glyph = Glyph.FORWARD,
                description = "Next month",
                enabled = state.month < java.time.YearMonth.from(state.today),
                onClick = onNext,
            )
        }

        Spacer(Modifier.size(8.dp))

        Row(Modifier.fillMaxWidth()) {
            // Monday-first, matching how the grid is built.
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }

        Spacer(Modifier.size(4.dp))

        state.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day, Modifier.weight(1f)) { onPickDay(day) }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GameIcon(
            glyph = glyph,
            tint = if (enabled) colors.clueText else colors.stroke,
            size = 18.dp,
            contentDescription = description,
        )
    }
}

/**
 * One day.
 *
 * Solved is mint and unmistakable; today is a gold ring whether or not it is done, so
 * the eye lands on it first; started is a wash. Nothing else is decorated - a calendar
 * where every cell is styled is a calendar you cannot scan.
 */
@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    val background = when {
        day.completed -> colors.success
        day.started -> colors.highlight
        else -> Color.Transparent
    }
    val textColor = when {
        day.completed -> colors.onAccent
        !day.inCurrentMonth || day.isFuture -> colors.textMuted
        else -> colors.clueText
    }

    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (day.isToday) {
                    Modifier.border(2.dp, colors.accent, CircleShape)
                } else {
                    Modifier
                },
            )
            .then(if (day.isFuture) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * The difficulty colours.
 *
 * Shared with the archive through this one function so a `hard` chip is the same orange
 * in both places. Hard has no palette slot of its own - it sits between the accent and
 * the mistake colour, which is exactly what it means.
 */
@Composable
internal fun difficultyTint(difficulty: Difficulty): Color {
    val colors = LocalBoardColors.current
    return when (difficulty) {
        Difficulty.EASY -> colors.success
        Difficulty.MEDIUM -> colors.info
        Difficulty.HARD -> colors.accent
        Difficulty.EXPERT -> colors.cellMistake
    }
}
