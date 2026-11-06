package cn.hdfk7.android.iptv.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.components.tvDirections
import cn.hdfk7.android.iptv.core.ui.theme.*
import cn.hdfk7.android.iptv.feature.player.ChannelListFocus
import cn.hdfk7.android.iptv.feature.player.ChannelPanel
import cn.hdfk7.android.iptv.feature.player.HomeHeader
import cn.hdfk7.android.iptv.feature.player.PlaybackControls
import cn.hdfk7.android.iptv.feature.player.PlaybackSurface
import cn.hdfk7.android.iptv.feature.player.PlayerState
import cn.hdfk7.android.iptv.feature.player.PlayerViewModel
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel

@Composable
fun TvPlayerScreen(state: PlayerState, viewModel: PlayerViewModel, sourceViewModel: SourceManagerViewModel, onSettings: () -> Unit, firstFocus: FocusRequester, selectedRowFocus: ChannelListFocus, videoFocus: FocusRequester, dialogOpen: Boolean, initialChannelFocusPending: Boolean, onInitialFocus: () -> Unit) {
    val surfaceFocus = remember(calculation = { FocusRequester() })
    val controlsFocus = remember(calculation = { FocusRequester() })
    val topFocus = remember(calculation = { FocusRequester() })
    val favoriteFocus = remember(calculation = { FocusRequester() })
    val settingsFocus = remember(calculation = { FocusRequester() })
    val volumeUpFocus = remember(calculation = { FocusRequester() })
    val inputMode = LocalInputModeManager.current
    val hasChannels = state.channels.isNotEmpty()
    LaunchedEffect(state.infoVisible, hasChannels, block = {
        if (dialogOpen) {
            return@LaunchedEffect
        }
        inputMode.requestInputMode(InputMode.Keyboard)
        if (!state.infoVisible) {
            surfaceFocus.requestFocus()
        } else if (!hasChannels) {
            firstFocus.requestFocus()
        } else if (!initialChannelFocusPending) {
            selectedRowFocus.restore?.invoke()
        }
    })
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = {
        val controlsWidth = minOf(560.dp, maxWidth * 0.72f)
        PlaybackSurface(state = state, modifier = Modifier.fillMaxSize().focusRequester(surfaceFocus).focusProperties(scope = { canFocus = !state.infoVisible }).focusable().onPreviewKeyEvent(onPreviewKeyEvent = { event ->
            val key = event.nativeKeyEvent
            val informationKey = key.keyCode == android.view.KeyEvent.KEYCODE_INFO || key.keyCode == android.view.KeyEvent.KEYCODE_MENU
            val directionKey = key.keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER)
            val shouldReveal = key.action == android.view.KeyEvent.ACTION_DOWN && (informationKey || directionKey)
            if (key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                    viewModel.changeChannel(if (key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    if (!hasChannels) {
                        viewModel.showInformation()
                    }
                }
                true
            } else if (shouldReveal) {
                viewModel.showInformation()
                true
            } else {
                false
            }
        }), framed = false)
        if (state.infoVisible) {
            val overlay = Modifier.background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.78f),
                0.24f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.86f)
            ))
            Box(modifier = Modifier.fillMaxSize().then(overlay).onPreviewKeyEvent(onPreviewKeyEvent = { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    viewModel.showInformation()
                }
                false
            }), content = {
                HomeHeader(
                    state = state,
                    sourceViewModel = sourceViewModel,
                    focusRequester = firstFocus,
                    onSettings = onSettings,
                    onRefresh = viewModel::refreshManually,
                    compact = false,
                    selectedRowFocus = selectedRowFocus,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 28.dp),
                    topFocus = topFocus,
                    settingsFocus = settingsFocus,
                    sourceNavigation = Modifier.tvDirections(
                        down = { selectedRowFocus.focusSelected?.invoke() },
                        right = { topFocus.requestFocus() }
                    ),
                    refreshNavigation = Modifier.tvDirections(
                        left = { firstFocus.requestFocus() },
                        right = { settingsFocus.requestFocus() }
                    ),
                    settingsNavigation = Modifier.tvDirections(
                        left = { topFocus.requestFocus() },
                        down = { volumeUpFocus.requestFocus() }
                    )
                )
                ChannelPanel(
                    state = state,
                    onSelect = viewModel::select,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp, top = 110.dp, bottom = 150.dp).width(320.dp).fillMaxHeight(),
                    compact = false,
                    television = true,
                    requestInitialFocus = initialChannelFocusPending,
                    onInitialFocus = onInitialFocus,
                    selectedRowFocus = selectedRowFocus,
                    videoFocus = videoFocus,
                    onConfirmSelect = viewModel::hideInformation,
                    onRight = { controlsFocus.requestFocus() },
                    onLeftToHeader = { firstFocus.requestFocus() },
                    onTopBoundary = { firstFocus.requestFocus() },
                    onBottomBoundary = { favoriteFocus.requestFocus() }
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 26.dp, end = 26.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            content = {
                                Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                                    Text(text = state.selectedName.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = state.selectedSourceName.orEmpty(), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                })
                                if (state.selectedId != null) {
                                    TvIconButton(icon = if (state.selectedId in state.favoriteIds) R.drawable.ic_star_filled else R.drawable.ic_star, description = stringResource(if (state.selectedId in state.favoriteIds) R.string.favorite_remove else R.string.favorite_add), onClick = viewModel::toggleCurrentFavorite, tint = if (state.selectedId in state.favoriteIds) Gold else Color.White, modifier = Modifier.focusRequester(favoriteFocus).tvDirections(
                                        up = { selectedRowFocus.restore?.invoke() },
                                        right = { controlsFocus.requestFocus() }
                                    ))
                                }
                            }
                        )
                        PlaybackControls(
                            state = state,
                            viewModel = viewModel,
                            selectedRowFocus = selectedRowFocus,
                            touchControls = false,
                            narrowTv = false,
                            modifier = Modifier.width(controlsWidth)
                                .background(DialogSurface.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            controlsFocus = controlsFocus,
                            onLeft = { if (state.selectedId != null) { favoriteFocus.requestFocus() } },
                            volumeUpFocus = volumeUpFocus,
                            onSettingsUp = { settingsFocus.requestFocus() }
                        )
                    }
                )
            })
        }
    })
}
