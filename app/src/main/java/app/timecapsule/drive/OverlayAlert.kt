package app.timecapsule.drive

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Pleos does not show heads-up notifications from regular apps over navigation, so while the
 * app is off screen the approach alert would only reach the notification center. With the
 * "display over other apps" permission (설정 > 지도 위에 알림 띄우기) the same card as the in-app
 * alert is drawn at the top of the map instead. Plain views: no Compose lifecycle needed.
 */
class OverlayAlert(context: Context) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val allowed get() = Settings.canDrawOverlays(ctx)

    fun show(title: String, sub: String, onListen: () -> Unit, onLater: () -> Unit) {
        if (!allowed) return
        hide()
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = px(r).toFloat() }
        fun button(label: String, filled: Boolean, click: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(if (filled) 0xFFFFFFFF.toInt() else 0xFF1B1D21.toInt())
            background = round(if (filled) 0xFF3A3D44.toInt() else 0xFFEEF0F3.toInt(), 12)
            setOnClickListener { hide(); click() }
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(32), px(28), px(32), px(28))
            background = round(0xFFFFFFFF.toInt(), 20)
            elevation = px(12).toFloat()
            addView(TextView(ctx).apply { text = title; textSize = 30f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF1B1D21.toInt()) })
            addView(TextView(ctx).apply { text = sub; textSize = 22f; setTextColor(0xFF6B7078.toInt()); setPadding(0, px(4), 0, px(16)) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = { LinearLayout.LayoutParams(0, px(88), 1f).apply { marginEnd = px(6); marginStart = px(6) } }
                addView(button("다음에", false, onLater), lp())
                addView(button("▶ 들려줘", true, onListen), lp())
            })
        }
        val params = WindowManager.LayoutParams(
            px(760), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = px(24) }
        runCatching { wm.addView(card, params); view = card }
    }

    /**
     * While a capsule plays with the map in front: a slim strip at the top of the map, so the
     * driver can see what is talking and stop or pause it without opening the app.
     */
    fun showPlaying(title: String, paused: Boolean, onPause: () -> Unit, onStop: () -> Unit) {
        if (!allowed) return
        hide()
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = px(r).toFloat() }
        fun button(label: String, click: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFF1B1D21.toInt())
            background = round(0xFFEEF0F3.toInt(), 12)
            setOnClickListener { click() }
        }
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(28), px(16), px(16), px(16))
            background = round(0xFFFFFFFF.toInt(), 20)
            elevation = px(12).toFloat()
            addView(TextView(ctx).apply {
                text = (if (paused) "멈춤   " else "듣는 중   ") + title
                textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF1B1D21.toInt())
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val lp = { LinearLayout.LayoutParams(px(150), px(72)).apply { marginStart = px(10) } }
            addView(button(if (paused) "다시" else "일시정지", onPause), lp())
            addView(button("그만") { hide(); onStop() }, lp())
        }
        val params = WindowManager.LayoutParams(
            px(760), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = px(24) }
        runCatching { wm.addView(bar, params); view = bar }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
