package com.ganim.nonogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.data.repo.Settings
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.Meter
import com.ganim.nonogram.ui.components.Panel
import com.ganim.nonogram.ui.theme.LocalBoardColors

/**
 * Settings (build plan 6.1).
 *
 * Short on purpose. The plan's non-goals list is long, and every switch here is one the
 * plan explicitly asks for: haptics must be disableable (5.2), and dark mode must be a
 * real choice rather than an afterthought (7).
 *
 * How to play leads, because someone who opens Settings mid-puzzle is usually looking
 * for exactly that.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    completedCount: Int,
    totalCount: Int,
    dailyCount: Int,
    pictureCount: Int,
    onHapticsChanged: (Boolean) -> Unit,
    onThemeChanged: (Boolean?) -> Unit,
    onHowToPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = colors.clueText)

        ActionRow(
            glyph = Glyph.SPARK,
            tint = colors.info,
            title = "How to play",
            subtitle = "Watch a puzzle get solved, one deduction at a time.",
            onClick = onHowToPlay,
        )

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Haptic feedback",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.clueText,
                    )
                    Text(
                        "A tick on every square you paint.",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted,
                    )
                }
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccentFill,
                        checkedTrackColor = colors.success,
                        checkedBorderColor = colors.success,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surface,
                        uncheckedBorderColor = colors.stroke,
                    ),
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Text("Theme", style = MaterialTheme.typography.titleSmall, color = colors.clueText)
            Box(Modifier.height(10.dp))
            val options = listOf<Pair<String, Boolean?>>(
                "System" to null,
                "Light" to false,
                "Dark" to true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { (label, value) ->
                    Segment(
                        label = label,
                        selected = settings.darkThemeOverride == value,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeChanged(value) },
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Puzzles solved",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.clueText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // Counted, not hard-coded: the total is the generated pack plus the
                    // hand-drawn pictures, and it moves whenever either pack is rebuilt.
                    "%,d".format(completedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent,
                )
                Text(
                    " / %,d".format(totalCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            Box(Modifier.height(10.dp))
            Meter(
                fraction = completedCount.toFloat() / totalCount.coerceAtLeast(1),
                tint = colors.accent,
            )
            Box(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Tally(dailyCount, "daily", colors.success)
                Tally(pictureCount, "pictures", colors.collection)
            }
        }
    }
}

@Composable
private fun Tally(value: Int, label: String, tint: Color) {
    val colors = LocalBoardColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
    }
}

/** A whole card that is one tap: a glyph, a title, a line of why, and an arrow. */
@Composable
private fun ActionRow(
    glyph: Glyph,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = tint, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.copy(alpha = BADGE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                GameIcon(glyph, tint, size = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.clueText)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, tint, size = 20.dp)
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) colors.accentFill else Color.Transparent)
            .border(1.dp, if (selected) colors.accentDeep else colors.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onAccentFill else colors.textMuted,
        )
    }
}

private const val BADGE_ALPHA = 0.22f
