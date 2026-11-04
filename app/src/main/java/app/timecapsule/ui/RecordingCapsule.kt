package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
        fun microphone(color: Color) {
            // Filled body, cradle, stem and base keep the mark legible at small sizes.
            drawRoundRect(color, Offset(c.x-r*0.19f, c.y-r*0.52f),
                Size(r*0.38f, r*0.66f), CornerRadius(r*0.19f))
            val cradle = Path().apply {
                moveTo(c.x-r*0.36f, c.y-r*0.04f)
                lineTo(c.x-r*0.22f, c.y-r*0.04f)
                lineTo(c.x-r*0.22f, c.y+r*0.10f)
                cubicTo(c.x-r*0.22f, c.y+r*0.36f, c.x+r*0.22f, c.y+r*0.36f, c.x+r*0.22f, c.y+r*0.10f)
                lineTo(c.x+r*0.22f, c.y-r*0.04f)
                lineTo(c.x+r*0.36f, c.y-r*0.04f)
                lineTo(c.x+r*0.36f, c.y+r*0.10f)
                cubicTo(c.x+r*0.36f, c.y+r*0.55f, c.x-r*0.36f, c.y+r*0.55f, c.x-r*0.36f, c.y+r*0.10f)
                close()
            }
            drawPath(cradle, color)
            drawRect(color, Offset(c.x-r*0.07f, c.y+r*0.36f), Size(r*0.14f, r*0.20f))
            drawRoundRect(color, Offset(c.x-r*0.25f, c.y+r*0.51f),
                Size(r*0.50f, r*0.13f), CornerRadius(r*0.065f))
        }
        clipRect(bottom = c.y) { microphone(red) }
        clipRect(top = c.y) { microphone(Color.White) }
    }
}
