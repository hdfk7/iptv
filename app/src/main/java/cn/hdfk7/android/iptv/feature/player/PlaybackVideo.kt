package cn.hdfk7.android.iptv.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

/** A single video surface remains attached when entering/leaving fullscreen. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackVideo(viewModel: PlayerViewModel) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val video = PlayerView(context)
            video.useController = false
            video.isFocusable = false
            video.isFocusableInTouchMode = false
            video
        },
        onRelease = { video -> video.player = null },
        update = { video -> video.player = viewModel.player }
    )
}
