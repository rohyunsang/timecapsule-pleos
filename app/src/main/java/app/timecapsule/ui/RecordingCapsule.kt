package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Simple red recording button, shared by the home and live recording screens. */
@Composable
fun RecordingCapsule(modifier: Modifier = Modifier, input: Float = 0f) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.40f
            val red = Color(0xFFD92D3A)
            val level = input.coerceIn(0f, 1f)
            if (level > 0f) drawCircle(red.copy(alpha = level * 0.12f), radius * (1.08f + level * 0.13f))
            drawCircle(red, radius)
            drawCircle(Color.White.copy(alpha = 0.85f), radius * (112f / 144f), style = Stroke(3.dp.toPx()))
        }
        Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.32f))
    }
}
