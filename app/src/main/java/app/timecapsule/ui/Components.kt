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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.timecapsule.R

/*
 * Design system. References:
 *  - Pleos UI Design Guide, Page Navigation: a screen header is a title (+ back on depth
 *    pages) and one "more" action. The status bar already shows driving state, so the app
 *    does not repeat it, and the app draws no logo of its own.
 *  - Android for Cars List template: rows sit directly on the background, separated by thin
 *    dividers. No cards, no panel borders, no shadows except for things that float (popups).
 *  - Pleos buttons: Basic (light gray) and Filled (dark gray). Monochrome; red only for
 *    "recording", blue only for switches, as in the system.
 *  - Asta Sans on the Pleos type scale; 160 dpi, so the guide's px are used as sp/dp.
 *  - Every tap plays the system click (guide: audio feedback on touch).
 */

@Immutable
data class Palette(
    val bg: Color,            // background_secondary
    val surface: Color,       // panels, popups
    val basic: Color,         // button_basic
    val basicPressed: Color,
    val line: Color,
    val text: Color,
    val textSub: Color,
    val textMute: Color,      // still >= 4.5:1 on bg and surface
    val filled: Color,        // button_filled (dark gray)
    val filledPressed: Color,
    val onFilled: Color,
    val strong: Color,        // the main action bar
    val onStrong: Color,
    val onStrongSub: Color,
    val rec: Color,           // regulation red
    val info: Color,          // regulation blue
    val isDark: Boolean,
)

private val Day = Palette(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), basic = Color(0xFFF1F2F4), basicPressed = Color(0xFFE0E1E6),
    line = Color(0xFFE0E1E6), text = Color(0xFF131417), textSub = Color(0xFF393A3C), textMute = Color(0xFF68696B),
    filled = Color(0xFF44464E), filledPressed = Color(0xFF313236), onFilled = Color(0xFFFFFFFF),
    strong = Color(0xFF131417), onStrong = Color(0xFFFFFFFF), onStrongSub = Color(0xFFB3B4B5),
    rec = Color(0xFFE5281E), info = Color(0xFF0064FF), isDark = false,
)

private val Night = Palette(
    bg = Color(0xFF131417), surface = Color(0xFF1D1E21), basic = Color(0xFF313236), basicPressed = Color(0xFF44464E),
    line = Color(0xFF313236), text = Color(0xFFFBFBFB), textSub = Color(0xFFD9D9DA), textMute = Color(0xFFAAAAAB),
    filled = Color(0xFF686A72), filledPressed = Color(0xFF44464E), onFilled = Color(0xFFFFFFFF),
    strong = Color(0xFF313236), onStrong = Color(0xFFFFFFFF), onStrongSub = Color(0xFFAAAAAB),
    rec = Color(0xFFFF4339), info = Color(0xFF0082FF), isDark = true,
)

private val LocalPalette = staticCompositionLocalOf { Day }

/** Tokens of the current day/night palette, plus sizes. */
object Tc {
    val Bg @Composable get() = LocalPalette.current.bg
    val Surface @Composable get() = LocalPalette.current.surface
    val Basic @Composable get() = LocalPalette.current.basic
    val Line @Composable get() = LocalPalette.current.line
    val Text @Composable get() = LocalPalette.current.text
    val TextSub @Composable get() = LocalPalette.current.textSub
    val TextMute @Composable get() = LocalPalette.current.textMute
    val Rec @Composable get() = LocalPalette.current.rec
    val Info @Composable get() = LocalPalette.current.info
    val P @Composable get() = LocalPalette.current

    /** Screen edge. Rows add [Inset] inside it, so row text lines up with headers. */
    val Gutter = 16.dp
    val Inset = 16.dp
    val Gap = 16.dp
    val PanelRadius = 20.dp
    val BtnRadius = 20.dp
    /** Spec 07 asks for >= 72 dp while driving; Pleos Large buttons are ~100. */
    val Touch = 88.dp
    val TouchLarge = 104.dp
    val Row = 112.dp
}

@OptIn(ExperimentalTextApi::class)
private fun asta(w: Int) = Font(R.font.asta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Asta Sans, the Pleos Connect system font (42dot, SIL OFL). Variable: 300-800. */
val AstaSans = FontFamily(asta(400), asta(500), asta(600), asta(700), asta(800))

/** Pleos type scale (sp). */
object Type {
    // Korean wraps between words (phrase breaking), not in the middle of a word.
    private val koreanBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

    private fun s(size: Int, line: Int, w: FontWeight, track: Float = 0f) =
        TextStyle(fontFamily = AstaSans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp, lineBreak = koreanBreak)

    val Display = s(120, 128, FontWeight.Bold, -3f)
    val HeadlineS = s(48, 64, FontWeight.Bold, -0.8f)
    val TitleL = s(40, 52, FontWeight.Bold, -0.5f)
    val TitleM = s(36, 44, FontWeight.Bold, -0.4f)
    val TitleS = s(32, 40, FontWeight.Bold, -0.3f)
    val BodyL = s(30, 38, FontWeight.Normal)
    val BodyM = s(28, 36, FontWeight.Normal)
    val BodyS = s(26, 34, FontWeight.Normal)
    val BodyStrong = s(30, 38, FontWeight.SemiBold)
    val LabelM = s(24, 30, FontWeight.Normal)
    val LabelStrong = s(24, 30, FontWeight.SemiBold)
    val Button = s(28, 34, FontWeight.SemiBold)
}

@Composable
fun TimeCapsuleTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (night) Night else Day
    val scheme = if (night) {
        darkColorScheme(primary = p.filled, onPrimary = p.onFilled, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.basic, onSurfaceVariant = p.textSub, outline = p.line)
    } else {
        lightColorScheme(primary = p.filled, onPrimary = p.onFilled, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.basic, onSurfaceVariant = p.textSub, outline = p.line)
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

/** onClick that also plays the system click sound. */
@Composable
fun clickWithSound(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return { view.playSoundEffect(SoundEffectConstants.CLICK); onClick() }
}

/**
 * Pleos button hierarchy: Basic (light gray, most actions), Filled (dark gray, the confirming
 * action of a pair), Quiet (text only, headers).
 */
enum class BtnStyle { Basic, Filled, Quiet }

@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.Basic,
    enabled: Boolean = true,
    height: Dp = Tc.Touch,
    onClick: () -> Unit,
) {
    val p = Tc.P
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg) = when (style) {
        BtnStyle.Basic -> (if (pressed) p.basicPressed else p.basic) to p.text
        BtnStyle.Filled -> (if (pressed) p.filledPressed else p.filled) to p.onFilled
        BtnStyle.Quiet -> (if (pressed) p.basic else Color.Transparent) to p.text
    }
    Surface(
        onClick = clickWithSound(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(Tc.BtnRadius),
        color = if (enabled) bg else if (style == BtnStyle.Quiet) Color.Transparent else p.basic,
        contentColor = if (enabled) fg else p.textMute,
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
            Text(label, style = Type.Button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Icon-only button; the label goes to screen readers. */
@Composable
fun IconBtn(icon: ImageVector, description: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Quiet, size: Dp = Tc.Touch, onClick: () -> Unit) {
    val p = Tc.P
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when (style) {
        BtnStyle.Quiet -> if (pressed) p.basic else Color.Transparent
        BtnStyle.Filled -> if (pressed) p.filledPressed else p.filled
        BtnStyle.Basic -> if (pressed) p.basicPressed else p.basic
    }
    Surface(
        onClick = clickWithSound(onClick), modifier = modifier.size(size), shape = RoundedCornerShape(Tc.BtnRadius),
        color = bg, contentColor = if (style == BtnStyle.Filled) p.onFilled else p.text, interactionSource = source,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(36.dp)) }
    }
}

/** A group of rows. No box around it: rows sit on the background (Android for Cars lists). */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), content = content)
}

/**
 * List row (Car App Library / CarPlay list item): leading icon, title, one secondary line,
 * optional trailing. The whole row is the touch target.
 */
@Composable
fun ListRow(
    title: String,
    sub: String? = null,
    leading: ImageVector? = null,
    leadingTint: Color = Tc.Text,
    enabled: Boolean = true,
    subColor: Color = Tc.TextMute,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val body: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = Tc.Inset, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (leading != null) Icon(leading, null, tint = if (enabled) leadingTint else Tc.TextMute, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = Type.BodyStrong, color = if (enabled) Tc.Text else Tc.TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = Type.LabelM, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
    val m = Modifier.fillMaxWidth().heightIn(min = Tc.Row)
    if (onClick != null) {
        Surface(
            onClick = clickWithSound(onClick), enabled = enabled, modifier = m,
            color = if (pressed) Tc.Basic else Color.Transparent, interactionSource = source,
        ) { body() }
    } else {
        Box(m, contentAlignment = Alignment.CenterStart) { body() }
    }
}

/** Divider inset to the row text, as in system lists. */
@Composable
fun RowDivider() = Box(Modifier.padding(horizontal = Tc.Inset).fillMaxWidth().height(1.dp).background(Tc.Line))

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Tc.Line))

/** Section header above a group of rows (the List template requires one when sections exist). */
@Composable
fun Section(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Tc.Inset, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.LabelStrong, color = Tc.TextMute, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * Floating notice in the Pleos System Notification layout: title, description, two equal
 * buttons (CTA filled, sub-action basic). Text only, no icon. [footer] carries the timer.
 * Only for things that float over the screen; in-page questions use [InlinePrompt].
 */
@Composable
fun SystemNotice(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    cta: Pair<String, () -> Unit>? = null,
    sub: Pair<String, () -> Unit>? = null,
    ctaIcon: ImageVector? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Tc.Surface,
        border = BorderStroke(1.dp, Tc.Line),
        shadowElevation = if (elevated) 12.dp else 0.dp,
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = Type.TitleS, color = Tc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (description != null) Text(description, style = Type.BodyS, color = Tc.TextMute, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (cta != null || sub != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    sub?.let { (l, f) -> Btn(l, Modifier.weight(1f), onClick = f) }
                    cta?.let { (l, f) -> Btn(l, Modifier.weight(1f), icon = ctaIcon, style = BtnStyle.Filled, onClick = f) }
                }
            }
            footer?.invoke()
        }
    }
}

/** An in-page question: text, then two equal buttons under it. No box. */
@Composable
fun InlinePrompt(title: String, description: String?, cta: Pair<String, () -> Unit>, sub: Pair<String, () -> Unit>, ctaIcon: ImageVector? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Tc.Inset, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.BodyStrong, color = Tc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (description != null) Text(description, style = Type.LabelM, color = Tc.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Btn(sub.first, Modifier.weight(1f), onClick = sub.second)
            Btn(cta.first, Modifier.weight(1f), icon = ctaIcon, style = BtnStyle.Filled, onClick = cta.second)
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).background(color, CircleShape))

/** Pleos Page Navigation: title (+ back on depth pages) and trailing actions. Nothing else. */
@Composable
fun Header(title: String, back: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = Tc.Touch), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", onClick = back)
        } else {
            Spacer(Modifier.width(Tc.Inset))
        }
        Text(title, style = Type.TitleM, color = Tc.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** System-style switch (blue when on, as in Pleos settings). */
@Composable
fun TcSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val click = clickWithSound {}
    Switch(
        on, { click(); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedTrackColor = Tc.Info, checkedThumbColor = Color.White, checkedBorderColor = Tc.Info,
            uncheckedTrackColor = Tc.Basic, uncheckedThumbColor = Tc.TextMute, uncheckedBorderColor = Tc.Line,
        ),
    )
}

/** Pleos system popup with two equal buttons; used for destructive confirms. */
@Composable
fun ConfirmPopup(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(max = 720.dp), shape = RoundedCornerShape(32.dp), color = Tc.Surface) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = Type.TitleS, color = Tc.Text)
                    Text(body, style = Type.BodyS, color = Tc.TextSub)
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

/**
 * Pleos window model: portrait = one window (the default, next to navigation),
 * landscape = two windows. Portrait carries the core; landscape adds the list beside it.
 */
enum class WidthClass { Portrait, Landscape }

fun widthClass(maxWidth: Dp) = if (maxWidth < 1100.dp) WidthClass.Portrait else WidthClass.Landscape

/** Between short facts on one line: plain spacing, no middle dots. */
const val Sep = "   "
