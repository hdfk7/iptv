package cn.hdfk7.android.iptv.core.ui.components

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
import cn.hdfk7.android.iptv.core.ui.theme.Accent
import cn.hdfk7.android.iptv.core.ui.theme.Panel

@Composable
fun TvAction(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    var focused by remember(calculation = { mutableStateOf(false) })
    val container = when {
        focused -> {
            Color(0xFF2A4A53)
        }
        selected -> {
            Color(0xFF193C3B)
        }
        else -> {
            Panel
        }
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp).onFocusChanged(onFocusChanged = { focus -> focused = focus.isFocused }),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF253247)),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
fun TvIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, selected: Boolean = false, enabled: Boolean = true) {
    TvAction(modifier = modifier.size(44.dp), onClick = onClick, selected = selected, enabled = enabled, content = {
        Icon(painter = painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    })
}
