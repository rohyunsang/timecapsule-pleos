package app.timecapsule.mobile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Flat neutral surfaces shared with the vehicle UI, with red reserved for primary actions. */
@Composable
internal fun TimeCapsuleMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFD92D3A), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFECEE), onPrimaryContainer = Color(0xFF9B1822),
            secondary = Color(0xFF515151), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8E8EB), onSecondaryContainer = Color(0xFF222222),
            tertiary = Color(0xFF515151), tertiaryContainer = Color(0xFFE8E8EB), onTertiaryContainer = Color(0xFF222222),
            background = Color(0xFFF5F5F6), onBackground = Color(0xFF222222),
            surface = Color.White, onSurface = Color(0xFF222222),
            surfaceVariant = Color(0xFFF1F1F3), onSurfaceVariant = Color(0xFF515151),
            surfaceTint = Color.Transparent,
            surfaceContainer = Color(0xFFF5F5F6), surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainerHigh = Color(0xFFF1F1F3), surfaceContainerHighest = Color(0xFFE8E8EB),
            outline = Color(0xFF909096), outlineVariant = Color(0xFFE1E1E5),
            error = Color(0xFFB42318), errorContainer = Color(0xFFFFEDEA), onErrorContainer = Color(0xFF7A2018),
        ),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content,
    )
}
