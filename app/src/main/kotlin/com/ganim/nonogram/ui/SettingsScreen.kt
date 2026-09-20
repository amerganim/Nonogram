package com.ganim.nonogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.data.repo.Settings
import com.ganim.nonogram.ui.theme.LocalBoardColors

/**
 * Settings (build plan 6.1).
 *
 * Short on purpose. The plan's non-goals list is long, and every switch here is one the
 * plan explicitly asks for: haptics must be disableable (5.2), and dark mode must be a
 * real choice rather than an afterthought (7).
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    completedCount: Int,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // First, because someone who opens Settings while stuck is looking for this.
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onHowToPlay),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("How to play", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Watch a puzzle get solved, one deduction at a time.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Haptic feedback", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A tick on every cell you paint.",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted,
                    )
                }
                Switch(checked = settings.hapticsEnabled, onCheckedChange = onHapticsChanged)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                val options = listOf<Pair<String, Boolean?>>(
                    "System" to null,
                    "Light" to false,
                    "Dark" to true,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = settings.darkThemeOverride == value,
                            onClick = { onThemeChanged(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Puzzles solved", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$completedCount of 5,000",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }
    }
}
