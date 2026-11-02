package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.withFrameNanos
import app.timecapsule.weather.Sky
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The weather of the day a capsule was buried, drawn faintly behind the playback screen:
 * drops on the glass for rain, drifting flakes for snow, slow clouds, haze, low sun, or the
 * dark of night. It sits in the upper part of the screen and fades out before the buttons,
 * stays in the app's grey tones, and stands still while driving (motion only when parked).
 */
@Composable
fun WeatherBackdrop(sky: Sky?, night: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    if (sky == null && !night) return
    val ink = Tc.Text
    val paper = Tc.Bg
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t.floatValue += (now - last) / 1e9f; last = now }
        }
    }
    val scene = remember(sky, night) { Scene(sky, night) }
    Canvas(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                // Fade out toward the bottom so the buttons stay on the plain background.
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 0.95f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) { scene.draw(this, t.floatValue, ink, paper) }
}

private class Scene(val sky: Sky?, val night: Boolean) {
    private val rnd = Random(sky?.ordinal ?: 99)
    private val wet = sky == Sky.RAIN || sky == Sky.STORM
    private val beads = List(if (sky == Sky.STORM) 120 else if (wet) 90 else 0) { Bead(rnd) }
    private val runners = List(if (sky == Sky.STORM) 16 else if (wet) 11 else 0) { Runner(rnd) }
    private val mist = List(if (sky == Sky.STORM) 260 else if (wet) 200 else 0) { Mist(rnd) }
    private val streaks = List(if (sky == Sky.STORM) 170 else if (wet) 110 else 0) { Streak(rnd) }
    private val flakes = List(if (sky == Sky.SNOW) 230 else 0) { Flake(rnd) }
    private val clouds = List(
        when (sky) { Sky.CLOUDY -> 9; Sky.STORM -> 5; Sky.PARTLY -> 4; else -> 0 },
    ) { Cloud(rnd, it) }
    private val haze = List(if (sky == Sky.FOG) 7 else 0) { Haze(rnd, it) }
    private val stars = List(if (night && (sky == null || sky == Sky.CLEAR || sky == Sky.PARTLY)) 80 else 0) { Star(rnd) }

    fun draw(d: DrawScope, t: Float, ink: Color, paper: Color) = with(d) {
        pxPerDp = density
        val w = size.width; val h = size.height
        // Tint of the sky: darker at night and in storms, cooler when wet or snowy.
        val tint = when {
            sky == Sky.STORM -> ink.copy(alpha = 0.16f)
            night -> Color(0xFF1F2E4A).copy(alpha = 0.42f)
            wet -> Color(0xFF7D8FA3).copy(alpha = 0.20f)
            sky == Sky.SNOW -> Color(0xFF8A9BAE).copy(alpha = 0.42f)
            sky == Sky.CLOUDY || sky == Sky.FOG -> ink.copy(alpha = 0.09f)
            sky == Sky.CLEAR || sky == Sky.PARTLY -> Color(0xFF6FA8E8).copy(alpha = 0.30f) // blue sky
            else -> Color.Transparent
        }
        if (tint.alpha > 0f) drawRect(Brush.verticalGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.3f))))

        // Low sun (day) or moon (night) behind the glass, breathing very slowly.
        if (sky == Sky.CLEAR || sky == Sky.PARTLY || (night && sky == null)) {
            val c = Offset(w * 0.86f, h * 0.06f)
            val glow = if (night) Color(0xFFC9D4E6) else Color(0xFFFFFFFF) // white sun on the blue
            val breath = 1f + 0.04f * sin(t * 0.4f)
            val rad = w * 0.46f * breath
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (night) 0.65f else 0.85f), Color.Transparent), c, rad), rad, c)
            // Two faint light spots that slide like light through a windshield.
            for (i in 0..1) {
                val k = t * (0.05f + i * 0.03f) + i * 2f
                val p = Offset(w * (0.55f + 0.25f * sin(k)), h * (0.22f + 0.10f * cos(k * 1.3f)))
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.32f), Color.Transparent), p, w * 0.12f), w * 0.12f, p)
            }
        }
        stars.forEach { it.draw(this, t, ink) }
        clouds.forEach { it.draw(this, t, ink) }
        haze.forEach { it.draw(this, t, ink) }
        streaks.forEach { it.draw(this, t, ink) }
        flakes.forEach { it.draw(this, t, ink, paper) }
        mist.forEach { it.draw(this, ink, paper) }
        beads.forEach { it.draw(this, t, ink, paper) }
        runners.forEach { it.draw(this, t, ink, paper) }
    }
}

private fun frac(x: Float) = x - floor(x)
private fun hash(a: Int, b: Int) = frac(sin(a * 12.9898f + b * 78.233f) * 43758.547f)

/** A drop sitting on the glass: appears, stays a while, then is replaced elsewhere. */
private class Bead(r: Random) {
    val seed = r.nextInt(100_000)
    val period = 9f + r.nextFloat() * 14f
    val offset = r.nextFloat() * 40f
    val scale = 0.35f + r.nextFloat() * r.nextFloat() * 1.3f // many small, few large

    fun draw(d: DrawScope, t: Float, ink: Color, paper: Color) = with(d) {
        val phase = (t + offset) / period
        val cycle = floor(phase).toInt()
        val local = phase - cycle
        val a = when {
            local < 0.06f -> local / 0.06f
            local > 0.9f -> (1f - local) / 0.1f
            else -> 1f
        }
        val x = hash(seed, cycle) * size.width
        val y = hash(seed + 7, cycle) * size.height * 0.8f
        drawDrop(Offset(x, y), (2.5f + 6f * scale).dpPx(), a, ink, paper)
    }
}

/** A heavier drop that slides down in small jerks and leaves a thin wet trail. */
private class Runner(r: Random) {
    val x0 = r.nextFloat()
    val speed = 40f + r.nextFloat() * 60f
    val start = r.nextFloat() * 50f
    val radius = 6f + r.nextFloat() * 4f
    val jerk = 1.5f + r.nextFloat() * 2f
    val seed = r.nextFloat() * 100f

    /** Where the drop is sideways at height y: glass is never even, so it wanders a little. */
    private fun DrawScope.xAt(y: Float) =
        x0 * size.width + 4f.dpPx() * sin(y / 31f.dpPx() + seed) + 1.6f.dpPx() * sin(y / 9f.dpPx() + seed * 2f)

    fun draw(d: DrawScope, t: Float, ink: Color, paper: Color) = with(d) {
        val h = size.height
        val tt = t + start
        // Stick-slip: speed swings between almost stopped and fast, never backwards.
        val travel = speed.dpPx() * (tt - 0.8f * sin(tt * jerk) / jerk)
        val span = h * 1.3f
        val y = travel % span - h * 0.15f
        val trail = (80f + speed * 1.6f).dpPx().coerceAtMost(y + h * 0.15f)
        // The wet path is the drop's own wobbly route, very faint and fading upwards,
        // with small beads left behind at uneven gaps (not a straight rod).
        val path = androidx.compose.ui.graphics.Path()
        var k = 0f
        val step = 3f.dpPx()
        path.moveTo(xAt(y), y)
        while (k < trail) { k += step; path.lineTo(xAt(y - k), y - k) }
        drawPath(
            path,
            Brush.verticalGradient(listOf(Color.Transparent, ink.copy(alpha = 0.11f)), y - trail, y),
            style = androidx.compose.ui.graphics.drawscope.Stroke((radius * 0.45f).dpPx(), cap = StrokeCap.Round),
        )
        k = 14f.dpPx()
        var i = 0
        while (k < trail * 0.85f) {
            val fade = 1f - k / trail
            drawDrop(Offset(xAt(y - k), y - k), (0.9f + 0.9f * hash(i, seed.toInt())).dpPx(), 0.7f * fade, ink, paper)
            k += (12f + 26f * hash(seed.toInt(), i)).dpPx(); i++
        }
        drawDrop(Offset(xAt(y), y), radius.dpPx(), 1f, ink, paper)
    }
}

/**
 * One drop on glass, drawn the way a lens looks: mostly clear, a darker rim (thicker on the
 * upper edge), the light gathered into a bright crescent at the bottom, and a tiny glint.
 */
private fun DrawScope.drawDrop(c: Offset, r: Float, a: Float, ink: Color, paper: Color) {
    if (a <= 0f) return
    val w = r * 2f; val h = r * 2.15f
    val tl = Offset(c.x - r, c.y - h / 2f)
    // Faint shadow the drop casts just below itself.
    drawOval(ink.copy(alpha = 0.035f * a), Offset(tl.x + r * 0.08f, tl.y + r * 0.28f), Size(w, h))
    // Lens body: clear centre, darker towards the rim; centre pushed down so the top rim is heavier.
    drawOval(
        Brush.radialGradient(
            0f to Color.Transparent, 0.62f to ink.copy(alpha = 0.035f * a), 1f to ink.copy(alpha = 0.30f * a),
            center = Offset(c.x, c.y + r * 0.22f), radius = r * 1.12f,
        ),
        tl, Size(w, h),
    )
    // Caustic: light focused at the bottom inside the drop.
    if (r > 1.5f) {
        drawOval(
            Brush.radialGradient(listOf(paper.copy(alpha = 0.9f * a), paper.copy(alpha = 0f)), Offset(c.x, c.y + r * 0.5f), r * 0.6f),
            Offset(c.x - r * 0.55f, c.y + r * 0.22f), Size(r * 1.1f, r * 0.55f),
        )
        drawCircle(paper.copy(alpha = 0.95f * a), r * 0.16f, Offset(c.x - r * 0.38f, c.y - r * 0.5f))
    }
}

/** Fine mist on the glass between the larger drops. */
private class Mist(r: Random) {
    val x = r.nextFloat(); val y = r.nextFloat() * 0.85f
    val radius = 0.6f + r.nextFloat() * 0.9f
    fun draw(d: DrawScope, ink: Color, paper: Color) = with(d) {
        drawDrop(Offset(x * size.width, y * size.height), radius.dpPx(), 0.8f, ink, paper)
    }
}

/** Rain falling far behind the glass: thin, quick, barely visible. */
private class Streak(r: Random) {
    val x0 = r.nextFloat()
    val y0 = r.nextFloat()
    val speed = 520f + r.nextFloat() * 300f
    val len = 22f + r.nextFloat() * 26f

    fun draw(d: DrawScope, t: Float, ink: Color) = with(d) {
        val h = size.height
        val y = frac(y0 + t * speed.dpPx() / h) * (h + len.dpPx()) - len.dpPx()
        val x = frac(x0 + y / size.width * 0.12f) * size.width
        drawLine(ink.copy(alpha = 0.09f), Offset(x, y), Offset(x + len.dpPx() * 0.12f, y + len.dpPx()), 1f.dpPx(), StrokeCap.Round)
    }
}

/** Snow in three depths: near flakes are larger, faster and sway more. */
private class Flake(r: Random) {
    val depth = r.nextInt(3)
    val x0 = r.nextFloat()
    val y0 = r.nextFloat()
    val radius = when (depth) { 0 -> 5.5f + r.nextFloat() * 3f; 1 -> 3.4f + r.nextFloat() * 1.6f; else -> 2f + r.nextFloat() * 0.9f }
    val speed = when (depth) { 0 -> 36f; 1 -> 22f; else -> 12f } * (0.8f + r.nextFloat() * 0.4f)
    val sway = when (depth) { 0 -> 14f; 1 -> 9f; else -> 5f }
    val freq = 0.4f + r.nextFloat() * 0.5f
    val ph = r.nextFloat() * 6.28f

    fun draw(d: DrawScope, t: Float, ink: Color, paper: Color) = with(d) {
        val h = size.height
        val y = frac(y0 + t * speed.dpPx() / h) * (h + 20f) - 10f
        val x = frac(x0) * size.width + sway.dpPx() * sin(t * freq + ph)
        val rr = radius.dpPx()
        val a = if (depth == 2) 0.6f else 1f
        drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = 0.42f * a), Color.Transparent), Offset(x, y + rr * 0.3f), rr * 1.7f), rr * 1.7f, Offset(x, y + rr * 0.3f))
        drawCircle(Brush.radialGradient(listOf(paper.copy(alpha = a), paper.copy(alpha = 0.6f * a), Color.Transparent), Offset(x, y), rr), rr, Offset(x, y))
    }
}

/** A soft cloud made of a few overlapping blurred puffs, drifting slowly sideways. */
private class Cloud(r: Random, i: Int) {
    val x0 = r.nextFloat()
    val y = 0.04f + 0.10f * (i % 3) + r.nextFloat() * 0.08f
    val scale = 0.16f + r.nextFloat() * 0.12f
    val speed = 5f + r.nextFloat() * 8f
    // Flat, wide clouds: many puffs spread sideways, very light.
    val puffs = List(8) { Triple(r.nextFloat() * 2.4f - 1.2f, r.nextFloat() * 0.3f - 0.15f, 0.35f + r.nextFloat() * 0.4f) }
    val alpha = 0.05f + r.nextFloat() * 0.04f

    fun draw(d: DrawScope, t: Float, ink: Color) = with(d) {
        val w = size.width
        val s = w * scale
        val cx = frac(x0 + t * speed.dpPx() / (w + 2 * s)) * (w + 2 * s) - s
        val cy = size.height * y
        puffs.forEach { (dx, dy, k) ->
            val c = Offset(cx + dx * s, cy + dy * s)
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = alpha), Color.Transparent), c, s * k), s * k, c)
        }
    }
}

/** Fog: wide, soft-edged bands that thicken and thin out slowly. */
private class Haze(r: Random, i: Int) {
    val y = 0.06f + i * 0.12f
    val freq = 0.15f + r.nextFloat() * 0.2f
    val ph = r.nextFloat() * 6.28f
    val thick = 0.05f + r.nextFloat() * 0.05f

    fun draw(d: DrawScope, t: Float, ink: Color) = with(d) {
        val a = 0.14f + 0.05f * sin(t * freq + ph)
        val cy = size.height * y + 6f.dpPx() * sin(t * freq * 0.7f + ph)
        val half = size.width * thick
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, ink.copy(alpha = a), Color.Transparent), cy - half, cy + half),
            Offset(0f, cy - half), Size(size.width, half * 2),
        )
    }
}

/** A few faint stars on clear nights, twinkling slowly. */
private class Star(r: Random) {
    val x = r.nextFloat()
    val y = r.nextFloat() * 0.6f
    val radius = 1.0f + r.nextFloat() * r.nextFloat() * 1.6f
    val freq = 0.3f + r.nextFloat() * 0.7f
    val ph = r.nextFloat() * 6.28f

    fun draw(d: DrawScope, t: Float, ink: Color) = with(d) {
        // Light points on the dark night tint, in both day and night themes.
        val a = 0.45f + 0.45f * (0.5f + 0.5f * sin(t * freq + ph))
        val c = Offset(x * size.width, y * size.height)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a * 0.5f), Color.Transparent), c, radius.dpPx() * 4f), radius.dpPx() * 4f, c)
        drawCircle(Color.White.copy(alpha = a), radius.dpPx(), c)
    }
}

/** Pixels per dp of the current draw; drawing happens on one thread. */
private var pxPerDp = 1f
private fun Float.dpPx(): Float = this * pxPerDp
