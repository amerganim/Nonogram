package com.ganim.nonogram.daily

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 */
@Composable
fun DailyScreen(
    viewModel: DailyViewModel,
    onPlay: (puzzleId: String, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StreakBanner(state)

        TodayCard(
            state = state,
            onPlay = { state.puzzleId?.let { onPlay(it, state.today) } },
        )

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

@Composable
private fun StreakBanner(state: DailyUiState) {
    val colors = LocalBoardColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile("Streak", state.currentStreak.toString(), Modifier.weight(1f))
        StatTile("Best", state.bestStreak.toString(), Modifier.weight(1f))
        StatTile("Solved", state.totalCompleted.toString(), Modifier.weight(1f))
    }
    if (state.freezeProtecting) {
        // Say it out loud rather than silently spending the freeze - a streak that
        // survived a missed day without explanation just looks like a bug.
        Text(
            text = "A streak freeze is holding your streak. Play today to keep it.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
        }
    }
}

@Composable
private fun TodayCard(state: DailyUiState, onPlay: () -> Unit) {
    val colors = LocalBoardColors.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                state.today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
            )
            Text(
                "${state.size}x${state.size}  ·  ${state.difficulty.name.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(onClick = onPlay, enabled = state.puzzleId != null) {
                Text(
                    when {
                        state.todayCompleted -> "Play again"
                        state.todayStarted -> "Continue"
                        else -> "Play today's puzzle"
                    },
                )
            }
        }
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPrevious) { Text("‹") }
                Text(
                    "${state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.month.year}",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = onNext,
                    enabled = state.month < java.time.YearMonth.from(state.today),
                ) { Text("›") }
            }

            Row(Modifier.fillMaxWidth()) {
                // Monday-first, matching how the grid is built.
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
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
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    val background = when {
        day.completed -> colors.accent
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
