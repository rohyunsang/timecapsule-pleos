package app.timecapsule.ui

import android.graphics.*

/** Shared capsule silhouette for the map and album. */
object CapsuleArtwork {
    fun bitmap(density: Float, count: Int = 1): Bitmap {
        val size = (64 * density).toInt()
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.density = (160 * density).toInt()
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shell = RectF(8f, 19f, 56f, 45f)
            val shape = Path().apply { addRoundRect(shell, 13f, 13f, Path.Direction.CW) }

            canvas.save()
            canvas.rotate(-35f, 32f, 32f)
            // A white silhouette keeps the capsule legible over detailed map tiles.
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(5f, 16f, 59f, 48f), 16f, 16f, paint)
            canvas.save()
            canvas.clipPath(shape)
            paint.color = Color.rgb(241, 235, 220)
            canvas.drawRect(shell, paint)
            paint.color = Color.rgb(66, 71, 82)
            canvas.drawRect(8f, 19f, 32f, 45f, paint)
            paint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRoundRect(RectF(12f, 22f, 52f, 25f), 1.5f, 1.5f, paint)
            // The seam and short waveform suggest a container holding a voice.
            paint.color = Color.rgb(143, 145, 148)
            paint.strokeWidth = 1.2f
            canvas.drawLine(32f, 19f, 32f, 45f, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            paint.strokeCap = Paint.Cap.ROUND
            for ((x, height) in listOf(17f to 4f, 22f to 9f, 27f to 5f)) {
                canvas.drawLine(x, 33f - height / 2, x, 33f + height / 2, paint)
            }
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            paint.color = Color.rgb(66, 71, 82)
            canvas.drawRoundRect(shell, 13f, 13f, paint)
            canvas.restore()

            // A single capsule needs no number; stacked collections retain their count.
            if (count > 1) {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(51f, 13f, 12f, paint)
                paint.color = Color.rgb(66, 71, 82)
                canvas.drawCircle(51f, 13f, 10f, paint)
                paint.color = Color.WHITE
                paint.textSize = if (count > 99) 9f else 12f
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(if (count > 99) "99+" else count.toString(), 51f,
                    13f - (paint.descent() + paint.ascent()) / 2, paint)
            }
        }
    }

}
