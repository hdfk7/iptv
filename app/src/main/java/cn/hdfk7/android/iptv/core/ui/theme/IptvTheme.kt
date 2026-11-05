package cn.hdfk7.android.iptv.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF7CE3CD)
val Muted = Color(0xFF8998B0)
val Panel = Color(0xFF111C2D)
val Gold = Color(0xFFFFD58A)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Gold,
            background = Color(0xFF080F1C),
            surface = Panel,
            surfaceVariant = Color(0xFF1C2A40),
            onPrimary = Color(0xFF0A2923),
            outline = Color(0xFF2A3A50)
        ),
        content = content
    )
}
