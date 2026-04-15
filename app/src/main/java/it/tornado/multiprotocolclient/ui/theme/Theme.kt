package it.tornado.multiprotocolclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density

private val ExpressiveDarkFallback = darkColorScheme()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiProtocolClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * textScale.coerceIn(0.8f, 1.3f))
    val canUseDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        canUseDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            ExpressiveDarkFallback
        }
        else -> {
            expressiveLightColorScheme()
        }
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
