package app.timecapsule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.timecapsule.tag.MoodTag

/** Warm amber on near-black: readable at night, low glare. */
private val Colors = darkColorScheme(
    primary = Color(0xFFFFB55C),
    onPrimary = Color(0xFF2B1700),
    secondary = Color(0xFFB8C7FF),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF242424),
    background = Color(0xFF0B0B0B),
    error = Color(0xFFFF6B5E),
)

@Composable
fun TimeCapsuleTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)

/** Spec 07: anything touched while driving is at least 72 dp tall. */
@Composable
fun BigButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val m = modifier.heightIn(min = 80.dp)
    val shape = RoundedCornerShape(20.dp)
    if (primary) {
        Button(onClick, m, enabled = enabled, shape = shape, contentPadding = ButtonDefaults.ContentPadding) {
            Text(label, fontSize = 24.sp)
        }
    } else {
        OutlinedButton(onClick, m, enabled = enabled, shape = shape) { Text(label, fontSize = 24.sp) }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) Color(0xFF3A2A12) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<MoodTag>) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { SuggestionChip(onClick = {}, label = { Text(it.label, fontSize = 18.sp) }) }
    }
}

@Composable
fun Title(text: String) = Text(text, fontSize = 34.sp, color = MaterialTheme.colorScheme.onSurface)

@Composable
fun Body(text: String, color: Color = Color.Unspecified) = Text(text, fontSize = 22.sp, color = color)

@Composable
fun Caption(text: String) = Text(text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

fun formatDistance(m: Double): String = if (m >= 1000) "%.1fkm".format(m / 1000) else "${m.toInt()}m"
