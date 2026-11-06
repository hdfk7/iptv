package cn.hdfk7.android.iptv.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.hdfk7.android.iptv.feature.player.ChannelListFocus
import cn.hdfk7.android.iptv.feature.player.ChannelPanel
import cn.hdfk7.android.iptv.feature.player.FullscreenPlayer
import cn.hdfk7.android.iptv.feature.player.HomeHeader
import cn.hdfk7.android.iptv.feature.player.PlaybackPanel
import cn.hdfk7.android.iptv.feature.player.PlayerState
import cn.hdfk7.android.iptv.feature.player.PlayerViewModel
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel

@Composable
fun PhonePlayerScreen(state: PlayerState, viewModel: PlayerViewModel, sourceViewModel: SourceManagerViewModel, onSettings: () -> Unit, firstFocus: FocusRequester, selectedRowFocus: ChannelListFocus, videoFocus: FocusRequester, onVideoBounds: (Rect) -> Unit, onInitialFocus: () -> Unit) {
    if (state.fullscreen) {
        FullscreenPlayer(state = state, viewModel = viewModel, touchControls = true)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = {
        val stacked = maxHeight > maxWidth
        val previewHeight = if (stacked) minOf((maxWidth - 24.dp) * (9f / 16f), maxHeight * 0.36f) else null
        val listWidth = minOf(264.dp, maxWidth * 0.32f)
        val channels: @Composable (Modifier) -> Unit = { modifier ->
            ChannelPanel(
                state = state,
                onSelect = viewModel::select,
                modifier = modifier,
                compact = true,
                television = false,
                requestInitialFocus = false,
                onInitialFocus = onInitialFocus,
                selectedRowFocus = selectedRowFocus,
                videoFocus = videoFocus
            )
        }
        val playback: @Composable (Modifier) -> Unit = { modifier ->
            PlaybackPanel(
                state = state,
                viewModel = viewModel,
                modifier = modifier,
                videoFocus = videoFocus,
                selectedRowFocus = selectedRowFocus,
                onVideoBounds = onVideoBounds,
                touchControls = true,
                previewHeight = previewHeight,
                narrowTv = false
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                HomeHeader(state = state, sourceViewModel = sourceViewModel, focusRequester = firstFocus, onSettings = onSettings, onRefresh = viewModel::refreshManually, compact = true, selectedRowFocus = selectedRowFocus)
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                if (stacked) {
                    playback(Modifier.fillMaxWidth())
                    channels(Modifier.weight(1f).fillMaxWidth())
                } else {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), content = {
                        channels(Modifier.width(listWidth).fillMaxHeight())
                        playback(Modifier.weight(1f).fillMaxHeight())
                    })
                }
            }
        )
    })
}
