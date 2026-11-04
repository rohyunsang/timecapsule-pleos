package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Two spherical shells, with a visible seam and clasp like a capsule-toy container. */
@Composable
fun RecordingCapsule(modifier: Modifier = Modifier, input: Float = 0f) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.40f
            val c = center
            drawCircle(Color(0xFFD92D3A).copy(alpha = 0.06f + input * 0.12f), r * (1.08f + input * 0.13f))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE5EBEF), Color(0xFFB9C5CC)),
                center = Offset(c.x - r * 0.35f, c.y - r * 0.45f), radius = r * 1.8f), r)
            drawArc(Brush.radialGradient(listOf(Color(0xFFFF6E78), Color(0xFFD92D3A), Color(0xFF9C1625)),
                center = Offset(c.x - r * 0.4f, c.y + r * 0.1f), radius = r * 1.8f),
                0f, 180f, true, Offset(c.x-r, c.y-r), Size(r*2, r*2))
            drawCircle(Color(0xFF8A343D).copy(alpha = 0.40f), r, style = Stroke(1.5.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x-r+2.dp.toPx(), c.y),
                Offset(c.x+r-2.dp.toPx(), c.y), 3.dp.toPx(), StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.90f), 210f, 70f, false,
                Offset(c.x-r*0.78f, c.y-r*0.78f), Size(r*1.56f, r*1.56f),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Color.White, r * 0.16f, c)
            drawCircle(Color(0xFFD92D3A), r * 0.08f, c)
        }
        Icon(Icons.Rounded.Mic, null, Modifier.offset(y = 29.dp).size(32.dp), tint = Color.White)
    }
}
