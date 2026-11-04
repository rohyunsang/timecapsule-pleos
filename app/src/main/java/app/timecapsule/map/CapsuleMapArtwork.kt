package app.timecapsule.map

import android.graphics.*

/** Name avatar fallback: no photo URL is available in Profile/Friend yet. */
object CapsuleMapArtwork {
    const val OPEN_COLOR = "#176B53"
    const val LOCKED_COLOR = "#44464E"

    fun bitmap(density: Float, label: String, open: Boolean, mixed: Boolean, count: Int): Bitmap {
        return Bitmap.createBitmap((88 * density).toInt(), (88 * density).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.density = (160 * density).toInt()
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val color = Color.parseColor(if (open) OPEN_COLOR else LOCKED_COLOR)
            p.color = Color.WHITE; canvas.drawCircle(42f,42f,35f,p)
            p.color = color; canvas.drawCircle(42f,42f,32f,p)
            p.color = Color.rgb(245,246,247); canvas.drawCircle(42f,42f,27f,p)
            p.color = Color.rgb(36,39,43); p.textSize = if (label.length > 1) 22f else 30f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textAlign = Paint.Align.CENTER
            canvas.drawText(label,42f,42f-(p.ascent()+p.descent())/2,p)
            fun badge(x: Float, isOpen: Boolean) {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE; canvas.drawCircle(x,66f,16f,p)
                p.color = Color.parseColor(if (isOpen) OPEN_COLOR else LOCKED_COLOR); canvas.drawCircle(x,66f,13f,p)
                p.color = Color.WHITE
                if (isOpen) {
                    canvas.drawPath(Path().apply { moveTo(x-3f,59f); lineTo(x+6f,66f); lineTo(x-3f,73f); close() },p)
                } else {
                    canvas.drawRoundRect(RectF(x-6f,64f,x+6f,73f),2f,2f,p)
                    p.style = Paint.Style.STROKE; p.strokeWidth=2.5f
                    canvas.drawArc(RectF(x-4f,57f,x+4f,68f),180f,180f,false,p)
                    p.style = Paint.Style.FILL
                }
            }
            if (mixed) badge(20f,true)
            badge(66f,open && !mixed)
            if (count > 1) {
                p.color=Color.WHITE; canvas.drawCircle(67f,17f,16f,p)
                p.color=Color.rgb(36,39,43); canvas.drawCircle(67f,17f,13f,p)
                p.color=Color.WHITE; p.textSize=if(count>99) 16f else 20f
                canvas.drawText(if(count>99) "99+" else count.toString(),67f,17f-(p.ascent()+p.descent())/2,p)
            }
        }
    }
}
