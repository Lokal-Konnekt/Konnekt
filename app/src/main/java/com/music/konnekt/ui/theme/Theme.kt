package com.music.konnekt.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.music.konnekt.R
import com.music.konnekt.data.settings.ThemeMode

// Apple Music's signature red. No longer the primary accent, but kept for the
// spots (Replay's rank badge) that want that specific red regardless of theme.
val AccentRed = Color(0xFFFA2D48)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

// Two restrained dark palettes drawn from the existing artwork and logo.
private val MidnightColors = DarkColors.copy(
    primary = Color(0xFFB9CBFF),
    onPrimary = Color(0xFF101B38),
    background = Color(0xFF090F1D),
    surface = Color(0xFF111B2D),
    surfaceVariant = Color(0xFF1B2940),
    onSurfaceVariant = Color(0xFFACBBD3),
    outline = Color(0xFF35435D),
)

private val EmberColors = DarkColors.copy(
    primary = Color(0xFFFFC59C),
    onPrimary = Color(0xFF32180C),
    background = Color(0xFF15100F),
    surface = Color(0xFF211815),
    surfaceVariant = Color(0xFF30211C),
    onSurfaceVariant = Color(0xFFD1B5AA),
    outline = Color(0xFF49332A),
)

/**
 * The opt-in redesign palette: a deep ink canvas with cyan, lilac and rose
 * accents that stay legible through translucent surfaces. It is kept separate
 * from the established palettes so disabling the setting returns to the exact
 * existing colour path.
 */
private val NewDarkColors = darkColorScheme(
    primary = Color(0xFFB8F5FF),
    onPrimary = Color(0xFF002027),
    secondary = Color(0xFFFFA9E5),
    onSecondary = Color(0xFF2C0B25),
    tertiary = Color(0xFFBEBBFF),
    onTertiary = Color(0xFF17143C),
    background = Color(0xFF070914),
    onBackground = Color(0xFFF3F4FF),
    surface = Color(0xFF101529),
    onSurface = Color(0xFFF3F4FF),
    surfaceVariant = Color(0xFF1A2140),
    onSurfaceVariant = Color(0xFFB5BCD9),
    outline = Color(0xFF46517B),
)

private val NewLightColors = lightColorScheme(
    primary = Color(0xFF006B7A),
    onPrimary = Color.White,
    secondary = Color(0xFF9B2C7A),
    onSecondary = Color.White,
    tertiary = Color(0xFF514CA2),
    onTertiary = Color.White,
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF151521),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF151521),
    surfaceVariant = Color(0xFFEDEEFF),
    onSurfaceVariant = Color(0xFF5E6174),
    outline = Color(0xFFB5B7CB),
)

private val NewDesignShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

/**
 * SF Pro Display, the face Apple Music itself is set in. Only the weights the
 * type scale actually asks for are bundled; Compose synthesises nothing, so a
 * missing weight would silently fall back to the nearest one shipped.
 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

// Heavy, tight typography — the backbone of the Apple Music look.
private val KonnektTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
).withFamily(SFProDisplay)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun KonnektTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    newDesignEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (newDesignEnabled) {
            if (darkTheme) NewDarkColors else NewLightColors
        } else {
            when (themeMode) {
                ThemeMode.MIDNIGHT -> MidnightColors
                ThemeMode.EMBER -> EmberColors
                else -> if (darkTheme) DarkColors else LightColors
            }
        },
        shapes = if (newDesignEnabled) NewDesignShapes else Shapes(),
        typography = KonnektTypography,
        content = content,
    )
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
