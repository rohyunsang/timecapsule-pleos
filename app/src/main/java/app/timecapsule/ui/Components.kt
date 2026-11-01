package app.timecapsule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.timecapsule.R
import app.timecapsule.tag.MoodTag

/**
 * Design tokens. One warm accent on a quiet near-black: readable at night, nothing competing
 * with the road. Text colors keep >= 4.5:1 contrast on every surface below.
 */
object Tc {
    val Bg = Color(0xFF0F1012)
    val Surface = Color(0xFF181A1D)
    val Surface2 = Color(0xFF22252A)
    val Line = Color(0xFF2C3036)
    val Text = Color(0xFFF1F2F4)
    val TextSub = Color(0xFFA9AEB6)
    val TextMute = Color(0xFF8A9099)
    val Accent = Color(0xFFF0B865)
    val OnAccent = Color(0xFF1E1505)
    val AccentSoft = Color(0xFF2A2318)
    val Rec = Color(0xFFFF6257)
    val Danger = Color(0xFFFF8277)

    val Gutter = 40.dp
    val Gap = 24.dp
    val CardPad = 28.dp
    val Radius = 20.dp
    /** Spec 07: touch targets used while driving are at least 72 dp; we use 80. */
    val Touch = 80.dp
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** Type scale (Pretendard): Korean reads best with slightly tight tracking on large sizes. */
object Type {
    val Display = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 112.sp, lineHeight = 120.sp, letterSpacing = (-2).sp)
    val Headline = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 46.sp, letterSpacing = (-0.6).sp)
    val Title = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp)
    val Body = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp)
    val Label = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp)
    val Meta = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val Tag = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
}

private val Colors = darkColorScheme(
    primary = Tc.Accent,
    onPrimary = Tc.OnAccent,
    secondary = Tc.Surface2,
    onSecondary = Tc.Text,
    background = Tc.Bg,
    onBackground = Tc.Text,
    surface = Tc.Bg,
    onSurface = Tc.Text,
    surfaceVariant = Tc.Surface,
    onSurfaceVariant = Tc.TextSub,
    outline = Tc.Line,
    error = Tc.Danger,
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.p() = copy(fontFamily = Pretendard)
    Typography(
        displayLarge = t.displayLarge.p(), displayMedium = t.displayMedium.p(), displaySmall = t.displaySmall.p(),
        headlineLarge = t.headlineLarge.p(), headlineMedium = t.headlineMedium.p(), headlineSmall = t.headlineSmall.p(),
        titleLarge = t.titleLarge.p(), titleMedium = t.titleMedium.p(), titleSmall = t.titleSmall.p(),
        bodyLarge = t.bodyLarge.p(), bodyMedium = t.bodyMedium.p(), bodySmall = t.bodySmall.p(),
        labelLarge = t.labelLarge.p(), labelMedium = t.labelMedium.p(), labelSmall = t.labelSmall.p(),
    )
}

@Composable
fun TimeCapsuleTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)

enum class BtnStyle { Primary, Tonal, Quiet }

/** The one button. Primary = the single main action on a screen; Tonal for the rest. */
@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.Tonal,
    enabled: Boolean = true,
    height: Dp = Tc.Touch,
    onClick: () -> Unit,
) {
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> Tc.Accent to Tc.OnAccent
        BtnStyle.Tonal -> Tc.Surface2 to Tc.Text
        BtnStyle.Quiet -> Color.Transparent to Tc.TextSub
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = if (style == BtnStyle.Quiet) Color.Transparent else Tc.Surface,
            disabledContentColor = Tc.TextMute.copy(alpha = 0.55f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, style = Type.Label)
    }
}

/** Flat card. [accent] gives the warm tint used for things that want attention now. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tc.Radius),
        color = if (accent) Tc.AccentSoft else Tc.Surface,
        border = if (accent) BorderStroke(1.dp, Tc.Accent.copy(alpha = 0.35f)) else null,
    ) {
        Column(
            (if (fill) Modifier.fillMaxSize() else Modifier).padding(Tc.CardPad),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Small label above content: "오늘 길", "가장 오래된 캡슐". */
@Composable
fun Eyebrow(text: String, icon: ImageVector? = null, color: Color = Tc.TextMute) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.Meta, color = color)
    }
}

@Composable
fun Headline(text: String, color: Color = Tc.Text) = Text(text, style = Type.Headline, color = color)

@Composable
fun Title(text: String, color: Color = Tc.Text) = Text(text, style = Type.Title, color = color)

@Composable
fun Body(text: String, color: Color = Tc.TextSub) = Text(text, style = Type.Body, color = color)

@Composable
fun Meta(text: String, color: Color = Tc.TextMute) = Text(text, style = Type.Meta, color = color)

/** Read-only mood tag. Not a button, so it is a plain box (no ripple, no focus). */
@Composable
fun Tag(text: String) {
    Box(
        Modifier.background(Tc.Surface2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = Type.Tag, color = Tc.TextSub) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<MoodTag>) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { Tag(it.label) }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).background(color, CircleShape))

@Composable
fun Divider() = HorizontalDivider(color = Tc.Line, thickness = 1.dp)

/** Screen header: title on the left, optional action on the right. */
@Composable
fun Header(title: String, back: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Btn("뒤로", icon = Icons.AutoMirrored.Rounded.ArrowBack, style = BtnStyle.Quiet, onClick = back)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = Type.Headline, color = Tc.Text)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

fun formatDistance(m: Double): String = if (m >= 1000) "%.1fkm".format(m / 1000) else "${m.toInt()}m"
