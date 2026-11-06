package cn.hdfk7.android.iptv.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun Modifier.tvDirections(
    up: (() -> Unit)? = null,
    down: (() -> Unit)? = null,
    left: (() -> Unit)? = null,
    right: (() -> Unit)? = null
): Modifier = onPreviewKeyEvent(onPreviewKeyEvent = { event ->
    val action = when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> return@onPreviewKeyEvent false
    }
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
        action?.invoke()
    }
    true
})
