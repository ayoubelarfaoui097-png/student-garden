package com.jdinohaila.studentgarden.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import com.jdinohaila.studentgarden.R
import java.util.Locale

val Blue = Color(0xFF0052CC)

private val hexColorPattern = Regex("#[0-9a-fA-F]{6}")

fun normalizedHexColor(value: String): String? =
    value.trim().takeIf { hexColorPattern.matches(it) }?.uppercase(Locale.ROOT)

fun accent(name: String) =
    when (name) {
        "gold" -> Color(0xFFFFC933)
        "coral" -> Color(0xFFF56C61)
        "blue" -> Color(0xFF3895F5)
        "green" -> Color(0xFF20B66A)
        "teal" -> Color(0xFF16B8A6)
        "cyan" -> Color(0xFF16A6C2)
        "orange" -> Color(0xFFF59B38)
        "rose" -> Color(0xFFFF82A8)
        else -> normalizedHexColor(name)?.let { Color(0xFF000000L or it.drop(1).toLong(16)) }
            ?: Color(0xFF8FADDE)
    }

fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
}

fun contrastingColor(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background))
        Color.Black else Color.White

/** Keeps agenda markers and checkbox outlines visible for very light or dark custom colors. */
fun agendaAccent(name: String, background: Color): Color {
    val color = accent(name)
    val target = contrastingColor(background)
    return (0..20).asSequence().map { lerp(color, target, it / 20f) }
        .first { contrastRatio(it, background) >= 3f }
}

fun colorLabel(name: String) =
    when (name) {
        "green" -> R.string.color_green
        "blue" -> R.string.color_blue
        "gold" -> R.string.color_gold
        "coral" -> R.string.color_coral
        "teal" -> R.string.color_teal
        "cyan" -> R.string.color_cyan
        "orange" -> R.string.color_orange
        else -> R.string.color_rose
    }

val featureColors = listOf("green", "blue", "gold", "coral", "teal", "cyan", "orange", "rose")

/** Separate font files ensure Android resolves a visibly distinct face for each weight. */
fun bundledFamily(resource: Int): FontFamily {
    val faces = if (resource == R.font.noto_arabic) listOf(R.font.noto_arabic_regular, R.font.noto_arabic_medium, R.font.noto_arabic_semibold, R.font.noto_arabic_bold)
        else listOf(R.font.source_sans_regular, R.font.source_sans_medium, R.font.source_sans_semibold, R.font.source_sans_bold)
    return FontFamily(faces.mapIndexed { i, face -> Font(face, weight = FontWeight(400 + i * 100)) })
}

@Composable
fun readingFamily(): FontFamily {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    return remember(arabic) {
        bundledFamily(if (arabic) R.font.noto_arabic else R.font.source_sans)
    }
}

@Composable
fun GardenTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle =
                if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
            navigationBarStyle =
                if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29)
            activity?.window?.isNavigationBarContrastEnforced = false
    }
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFA9CAFF),
                onPrimary = Color(0xFF002E76),
                primaryContainer = Color(0xFF183D71),
                onPrimaryContainer = Color(0xFFDCEAFF),
                secondary = Color(0xFFAAC7F5),
                onSecondary = Color(0xFF002F65),
                secondaryContainer = Color(0xFF203E65),
                onSecondaryContainer = Color(0xFFDDEAFF),
                tertiary = Color(0xFF72DECA),
                onTertiary = Color(0xFF003E35),
                tertiaryContainer = Color(0xFF005247),
                onTertiaryContainer = Color(0xFF9CF2DA),
                onBackground = Color(0xFFEEF3FA),
                surfaceTint = Color(0xFFA9CAFF),
                surfaceContainerLowest = Color(0xFF0B121D),
                surfaceContainerLow = Color(0xFF141F2D),
                surfaceContainerHigh = Color(0xFF27364A),
                surfaceContainerHighest = Color(0xFF304156),
                surfaceBright = Color(0xFF314256),
                surfaceDim = Color(0xFF101824),
                surfaceVariant = Color(0xFF304156),
                outline = Color(0xFF8D9FB8),
                inverseSurface = Color(0xFFEEF3FA),
                inverseOnSurface = Color(0xFF1C1E21),
                inversePrimary = Blue,
                surface = Color(0xFF172130),
                background = Color(0xFF101824),
                surfaceContainer = Color(0xFF202D3F),
                onSurface = Color(0xFFEEF3FA),
                onSurfaceVariant = Color(0xFFB9C7DA),
                outlineVariant = Color(0xFF3A485C),
            )
        else
            lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCEAFF),
                onPrimaryContainer = Color(0xFF003B91),
                secondary = Blue,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE1ECFA),
                onSecondaryContainer = Color(0xFF1C1E21),
                tertiary = Color(0xFF00685F),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFD2F8EA),
                onTertiaryContainer = Color(0xFF00493C),
                onBackground = Color(0xFF1C1E21),
                surfaceTint = Blue,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF6F7F9),
                surfaceContainerHigh = Color(0xFFE9ECF0),
                surfaceContainerHighest = Color(0xFFE4E8ED),
                surfaceBright = Color.White,
                surfaceDim = Color(0xFFD8E2F0),
                surfaceVariant = Color(0xFFE4E8ED),
                outline = Color(0xFF72839A),
                inverseSurface = Color(0xFF25344B),
                inverseOnSurface = Color.White,
                inversePrimary = Color(0xFFA9CAFF),
                surface = Color.White,
                background = Color(0xFFF6F7F9),
                surfaceContainer = Color(0xFFEEF0F3),
                onSurface = Color(0xFF1C1E21),
                onSurfaceVariant = Color(0xFF606770),
                outlineVariant = Color(0xFFDFE3E8),
            )
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val family =
        remember(arabic) { bundledFamily(if (arabic) R.font.noto_arabic else R.font.source_sans) }
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                displayLarge = base.displayLarge.copy(fontFamily = family),
                displayMedium = base.displayMedium.copy(fontFamily = family),
                headlineSmall =
                    base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                titleSmall =
                    base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                displaySmall =
                    base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                headlineLarge =
                    base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                headlineMedium =
                    base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                titleLarge =
                    base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                titleMedium =
                    base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
                bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSize = 16.sp),
                bodyMedium = base.bodyMedium.copy(fontFamily = family, fontSize = 15.sp),
                bodySmall = base.bodySmall.copy(fontFamily = family),
                labelLarge = base.labelLarge.copy(fontFamily = family, fontSize = 15.sp),
                labelMedium = base.labelMedium.copy(fontFamily = family),
                labelSmall = base.labelSmall.copy(fontFamily = family),
            ),
        content = content,
    )
}
