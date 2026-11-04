package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/** Flat capsule-toy shells with a continuous, contrasting microphone across the seam. */
@Composable
fun RecordingCapsule(modifier: Modifier = Modifier, input: Float = 0f) {
    val outline = Tc.TextSub
    Canvas(modifier) {
        val r = size.minDimension * 0.40f
        val c = center
        val red = Color(0xFFD92D3A)
        // Only live input produces the outer pulse; the idle button stays completely flat.
        if (input > 0f) drawCircle(red.copy(alpha = input * 0.12f), r * (1.08f + input * 0.13f))
        drawCircle(Color.White, r)
        drawArc(red, 0f, 180f, true, Offset(c.x-r, c.y-r), Size(r*2, r*2))
        drawCircle(outline.copy(alpha = 0.35f), r, style = Stroke(1.5.dp.toPx()))
        val micWidth = r * 0.24f
        val micHeight = r * 0.43f
        val top = c.y - r * 0.37f
        val stroke = r * 0.065f
        fun microphone(color: Color) {
            drawRoundRect(color, Offset(c.x-micWidth/2, top), Size(micWidth, micHeight), CornerRadius(micWidth/2))
            val arcWidth = r * 0.44f
            val arcHeight = r * 0.44f
            drawArc(color, 0f, 180f, false,
                Offset(c.x-arcWidth/2, top+micHeight-arcHeight/2), Size(arcWidth, arcHeight),
                style = Stroke(stroke, cap = StrokeCap.Round))
            val baseY = top + micHeight + arcHeight/2
            drawLine(color, Offset(c.x, baseY), Offset(c.x, baseY+r*0.15f), stroke, StrokeCap.Round)
            drawLine(color, Offset(c.x-r*0.12f, baseY+r*0.15f), Offset(c.x+r*0.12f, baseY+r*0.15f), stroke, StrokeCap.Round)
        }
        clipRect(bottom = c.y) { microphone(red) }
        clipRect(top = c.y) { microphone(Color.White) }
    }
}
