package cn.hdfk7.android.iptv.core.ui.components

import cn.hdfk7.android.iptv.core.ui.theme.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

val LocalTvActionFocused = staticCompositionLocalOf(defaultFactory = { false })

@Composable
fun TvAction(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, shape: Shape = RoundedCornerShape(16.dp), focusScale: Float = 1f, containerColor: Color = Panel, contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp), content: @Composable RowScope.() -> Unit) {
    var focused by remember(calculation = { mutableStateOf(false) })
    val scale by animateFloatAsState(targetValue = if (focused && enabled) focusScale else 1f, animationSpec = tween(durationMillis = 140), label = "buttonFocusScale")
    val container = when {
        focused -> {
            FocusFill
        }
        selected -> {
            SelectedPanel
        }
        else -> {
            containerColor
        }
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp).graphicsLayer(block = { scaleX = scale; scaleY = scale }).onFocusChanged(onFocusChanged = { focus -> focused = focus.isFocused }),
        shape = shape,
        border = BorderStroke(1.dp, if (focused) Accent else Border.copy(alpha = 0.55f)),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = if (focused) FocusInk else Accent),
        contentPadding = contentPadding,
        content = {
            val scope = this
            CompositionLocalProvider(LocalTvActionFocused provides focused, content = { content(scope) })
        }
    )
}

@Composable
fun TvIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, selected: Boolean = false, enabled: Boolean = true) {
    TvAction(modifier = modifier.size(46.dp), onClick = onClick, selected = selected, enabled = enabled, shape = CircleShape, focusScale = 1.08f, containerColor = ButtonPanel, contentPadding = PaddingValues(0.dp), content = {
        Icon(painter = painterResource(icon), contentDescription = description, tint = if (LocalTvActionFocused.current) FocusInk else tint, modifier = Modifier.size(20.dp))
    })
}
