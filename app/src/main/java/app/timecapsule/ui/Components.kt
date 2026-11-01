package app.timecapsule.ui

import android.view.SoundEffectConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.timecapsule.R
import app.timecapsule.tag.MoodTag

/*
 * Design system, following the Pleos Connect UI Design Guide (document.pleos.ai):
 *  - Colors: Pleos system tokens for day (light) and night (dark), switched with the car's night
 *    mode. One brand accent (amber) is kept for the single main action on a screen.
 *  - Type: Asta Sans (the Pleos system font) on the Pleos scale. Pleos displays are 160 dpi, so
 *    the guide's px values are used as sp/dp directly.
 *  - Buttons: Pleos hierarchy. Basic (light gray) and Filled (dark gray), plus our Primary.
 *  - Feedback: every tap plays the system click (guide: "audio feedback upon touch").
 */

@Immutable
data class Palette(
    val bg: Color,          // background_secondary
    val surface: Color,     // cards, sheets
    val surface2: Color,    // basic buttons, chips
    val line: Color,
    val text: Color,        // text primary
    val textSub: Color,     // text secondary
    val textMute: Color,    // text tertiary (still >= 4.5:1 on bg and surface)
    val accent: Color,      // brand fill
    val onAccent: Color,
    val accentInk: Color,   // brand color used as text/icon on bg (contrast-safe)
    val accentSoft: Color,
    val filled: Color,      // Pleos button_filled
    val filledPressed: Color,
    val onFilled: Color,
    val rec: Color,         // regulation red
    val info: Color,        // regulation blue
    val scrim: Color,
    val isDark: Boolean,
)

private val Day = Palette(
    bg = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEDEEF2), line = Color(0xFFE0E1E6),
    text = Color(0xFF131417), textSub = Color(0xFF393A3C), textMute = Color(0xFF606164),
    accent = Color(0xFFF0B865), onAccent = Color(0xFF1E1505), accentInk = Color(0xFF8A5A12), accentSoft = Color(0xFFFCF1DF),
    filled = Color(0xFF44464E), filledPressed = Color(0xFF313236), onFilled = Color(0xFFFFFFFF),
    rec = Color(0xFFE5281E), info = Color(0xFF0064FF), scrim = Color(0x66131417), isDark = false,
)

private val Night = Palette(
    bg = Color(0xFF131417), surface = Color(0xFF1D1E21), surface2 = Color(0xFF313236), line = Color(0xFF44464E),
    text = Color(0xFFFBFBFB), textSub = Color(0xFFD9D9DA), textMute = Color(0xFFAAAAAB),
    accent = Color(0xFFF0B865), onAccent = Color(0xFF1E1505), accentInk = Color(0xFFF0B865), accentSoft = Color(0xFF2E2719),
    filled = Color(0xFFE7E7E8), filledPressed = Color(0xFFC9C9CB), onFilled = Color(0xFF131417),
    rec = Color(0xFFFF4339), info = Color(0xFF0082FF), scrim = Color(0x99000000), isDark = true,
)

private val LocalPalette = staticCompositionLocalOf { Day }

/** Color tokens of the current (day/night) palette, plus size tokens. */
object Tc {
    val Bg @Composable get() = LocalPalette.current.bg
    val Surface @Composable get() = LocalPalette.current.surface
    val Surface2 @Composable get() = LocalPalette.current.surface2
    val Line @Composable get() = LocalPalette.current.line
    val Text @Composable get() = LocalPalette.current.text
    val TextSub @Composable get() = LocalPalette.current.textSub
    val TextMute @Composable get() = LocalPalette.current.textMute
    val Accent @Composable get() = LocalPalette.current.accent
    val OnAccent @Composable get() = LocalPalette.current.onAccent
    val AccentInk @Composable get() = LocalPalette.current.accentInk
    val AccentSoft @Composable get() = LocalPalette.current.accentSoft
    val Rec @Composable get() = LocalPalette.current.rec
    val Info @Composable get() = LocalPalette.current.info
    val Scrim @Composable get() = LocalPalette.current.scrim
    val P @Composable get() = LocalPalette.current

    val Gutter = 40.dp
    val Gap = 24.dp
    val CardPad = 32.dp
    val Radius = 24.dp
    /** Spec 07 asks for 72 dp while driving; Pleos Large buttons are ~100. */
    val Touch = 88.dp
    val TouchLarge = 112.dp
}

@OptIn(ExperimentalTextApi::class)
private fun asta(w: Int) = Font(R.font.asta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Asta Sans, the Pleos Connect system font (42dot, SIL OFL). Variable: 300-800. */
val AstaSans = FontFamily(asta(400), asta(500), asta(600), asta(700), asta(800))

/** Pleos type scale (size/line height in sp). */
object Type {
    private fun s(size: Int, line: Int, w: FontWeight, track: Float = 0f) =
        TextStyle(fontFamily = AstaSans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp)

    val Display = s(140, 148, FontWeight.ExtraBold, -3f)
    val HeadlineM = s(56, 72, FontWeight.ExtraBold, -1f)
    val HeadlineS = s(48, 64, FontWeight.Bold, -0.8f)
    val TitleL = s(40, 52, FontWeight.Bold, -0.5f)
    val TitleM = s(36, 44, FontWeight.Bold, -0.4f)
    val TitleS = s(32, 40, FontWeight.Bold, -0.3f)
    val BodyL = s(30, 38, FontWeight.Normal)
    val BodyM = s(28, 36, FontWeight.Normal)
    val BodyS = s(26, 34, FontWeight.Normal)
    val BodyStrong = s(28, 36, FontWeight.SemiBold)
    val LabelM = s(24, 28, FontWeight.Normal)
    val LabelS = s(20, 24, FontWeight.Normal)
    val Button = s(28, 34, FontWeight.Bold)
}

@Composable
fun TimeCapsuleTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (night) Night else Day
    val scheme = if (night) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.textSub, outline = p.line)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.textSub, outline = p.line)
    }
    val t = Typography()
    val typography = remember {
        fun TextStyle.a() = copy(fontFamily = AstaSans)
        Typography(
            displayLarge = t.displayLarge.a(), displayMedium = t.displayMedium.a(), displaySmall = t.displaySmall.a(),
            headlineLarge = t.headlineLarge.a(), headlineMedium = t.headlineMedium.a(), headlineSmall = t.headlineSmall.a(),
            titleLarge = t.titleLarge.a(), titleMedium = t.titleMedium.a(), titleSmall = t.titleSmall.a(),
            bodyLarge = t.bodyLarge.a(), bodyMedium = t.bodyMedium.a(), bodySmall = t.bodySmall.a(),
            labelLarge = t.labelLarge.a(), labelMedium = t.labelMedium.a(), labelSmall = t.labelSmall.a(),
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** onClick that also plays the system click sound (Pleos: audio feedback on touch). */
@Composable
fun clickWithSound(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return { view.playSoundEffect(SoundEffectConstants.CLICK); onClick() }
}

/**
 * Button hierarchy:
 *  - Primary: our amber, the single main action of a screen.
 *  - Basic:   Pleos "primary" basic button (light gray); most actions.
 *  - Filled:  Pleos "secondary" filled button (dark gray); a strong but non-brand action.
 *  - Quiet:   text-only, for low-priority actions in headers.
 */
enum class BtnStyle { Primary, Basic, Filled, Quiet }

@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.Basic,
    enabled: Boolean = true,
    height: Dp = Tc.Touch,
    textStyle: TextStyle = Type.Button,
    onClick: () -> Unit,
) {
    val p = Tc.P
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> (if (pressed) p.accent.copy(alpha = 0.82f) else p.accent) to p.onAccent
        BtnStyle.Basic -> (if (pressed) p.line else p.surface2) to p.text
        BtnStyle.Filled -> (if (pressed) p.filledPressed else p.filled) to p.onFilled
        BtnStyle.Quiet -> (if (pressed) p.surface2 else Color.Transparent) to p.textSub
    }
    Surface(
        onClick = clickWithSound(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) bg else if (style == BtnStyle.Quiet) Color.Transparent else p.surface2.copy(alpha = 0.6f),
        contentColor = if (enabled) fg else p.textMute.copy(alpha = 0.7f),
        interactionSource = source,
    ) {
        Row(
            Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square icon-only button with a visible label for screen readers (min 88dp touch). */
@Composable
fun IconBtn(icon: ImageVector, description: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Basic, size: Dp = Tc.Touch, onClick: () -> Unit) {
    val p = Tc.P
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when (style) {
        BtnStyle.Quiet -> if (pressed) p.surface2 else Color.Transparent
        else -> if (pressed) p.line else p.surface2
    }
    Surface(
        onClick = clickWithSound(onClick), modifier = modifier.size(size), shape = RoundedCornerShape(20.dp),
        color = bg, contentColor = p.text, interactionSource = source,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(36.dp)) }
    }
}

/** Flat card. [accent] tints it for things that want attention now. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fill: Boolean = false,
    pad: Dp = Tc.CardPad,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tc.Radius),
        color = if (accent) Tc.AccentSoft else Tc.Surface,
        border = if (accent) BorderStroke(2.dp, Tc.Accent) else if (!Tc.P.isDark) BorderStroke(1.dp, Tc.Line) else null,
    ) {
        Column(
            (if (fill) Modifier.fillMaxSize() else Modifier).padding(pad),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Rounded-square icon badge, like the app icon slot of a Pleos system notification. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = Tc.OnAccent, bg: Color = Tc.Accent, size: Dp = 72.dp) {
    Box(Modifier.size(size).background(bg, RoundedCornerShape(size * 0.28f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Pleos System Notification shape: icon, title, one-line description, up to two equal buttons.
 * Used for every "now" message (approach, arrival, passed capsule, route forecast).
 */
@Composable
fun Notice(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    elevated: Boolean = false,
    actions: (@Composable (Modifier) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (elevated) Modifier.shadow(24.dp, RoundedCornerShape(32.dp)) else Modifier),
        shape = RoundedCornerShape(32.dp),
        color = Tc.Surface,
        border = if (accent) BorderStroke(2.dp, Tc.Accent) else if (!Tc.P.isDark) BorderStroke(1.dp, Tc.Line) else null,
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconBadge(icon, bg = if (accent) Tc.Accent else Tc.Surface2, tint = if (accent) Tc.OnAccent else Tc.Text)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = Type.TitleS, color = Tc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (description != null) Text(description, style = Type.BodyS, color = Tc.TextSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions(Modifier.weight(1f)) }
            }
            footer?.invoke()
        }
    }
}

/** Small label above content. */
@Composable
fun Eyebrow(text: String, icon: ImageVector? = null, color: Color = Tc.TextMute) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.LabelM, color = color)
    }
}

@Composable
fun Headline(text: String, color: Color = Tc.Text, align: TextAlign? = null) = Text(text, style = Type.HeadlineS, color = color, textAlign = align)

@Composable
fun Title(text: String, color: Color = Tc.Text, align: TextAlign? = null) = Text(text, style = Type.TitleM, color = color, textAlign = align)

@Composable
fun Body(text: String, color: Color = Tc.TextSub, align: TextAlign? = null) = Text(text, style = Type.BodyM, color = color, textAlign = align)

@Composable
fun Meta(text: String, color: Color = Tc.TextMute, align: TextAlign? = null) = Text(text, style = Type.LabelM, color = color, textAlign = align)

/** Read-only mood tag. Not a button, so a plain box (no ripple, no focus). */
@Composable
fun Tag(text: String) {
    Box(Modifier.background(Tc.Surface2, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = Type.LabelM, color = Tc.TextSub)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<MoodTag>, center: Boolean = false) {
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { tags.forEach { Tag(it.label) } }
}

@Composable
fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).background(color, CircleShape))

@Composable
fun Divider() = HorizontalDivider(color = Tc.Line, thickness = 1.dp)

/** Screen header: back arrow + title, like the built-in Pleos apps. */
@Composable
fun Header(title: String, back: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = Tc.Touch), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", style = BtnStyle.Quiet, onClick = back)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = Type.TitleL, color = Tc.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** On/off switch in our tokens. */
@Composable
fun TcSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val click = clickWithSound {}
    Switch(
        on, { click(); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedTrackColor = Tc.Accent, checkedThumbColor = Tc.OnAccent, checkedBorderColor = Tc.Accent,
            uncheckedTrackColor = Tc.Surface2, uncheckedThumbColor = Tc.TextMute, uncheckedBorderColor = Tc.Line,
        ),
    )
}

/** Pleos-style popup (background_popup) with two equal buttons; used for destructive confirms. */
@Composable
fun ConfirmPopup(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(max = 720.dp), shape = RoundedCornerShape(32.dp), color = Tc.Surface) {
            Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = Type.TitleM, color = Tc.Text)
                    Text(body, style = Type.BodyM, color = Tc.TextSub)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Btn("취소", Modifier.weight(1f), onClick = onDismiss)
                    Btn(confirm, Modifier.weight(1f), style = BtnStyle.Filled, onClick = onConfirm)
                }
            }
        }
    }
}

fun formatDistance(m: Double): String = if (m >= 1000) "%.1fkm".format(m / 1000) else "${m.toInt()}m"

/** Width class for the Pleos window policy: full width (2:0) or half (1:1 next to navigation). */
enum class WidthClass { Compact, Wide }

fun widthClass(maxWidth: Dp) = if (maxWidth < 1100.dp) WidthClass.Compact else WidthClass.Wide
