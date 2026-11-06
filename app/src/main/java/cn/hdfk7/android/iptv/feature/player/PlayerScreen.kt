package cn.hdfk7.android.iptv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.app.IptvApplication
import cn.hdfk7.android.iptv.core.model.SourceLibrary
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.theme.*
import cn.hdfk7.android.iptv.feature.proxy.ProxySettingsDialog
import cn.hdfk7.android.iptv.feature.source.SourceManagerDialog
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, sourceViewModel: SourceManagerViewModel, television: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sourceUi by sourceViewModel.state.collectAsStateWithLifecycle()
    var proxyOpen by remember(calculation = { mutableStateOf(false) })
    val app = LocalContext.current.applicationContext as IptvApplication
    val firstFocus = remember(calculation = { FocusRequester() })
    val selectedRowFocus = remember(calculation = { ChannelListFocus() })
    var initialChannelFocusPending by remember(calculation = { mutableStateOf(true) })
    val videoFocus = remember(calculation = { FocusRequester() })
    var returningFromFullscreen by remember(calculation = { mutableStateOf(false) })
    val density = LocalDensity.current
    var videoBounds by remember(calculation = { mutableStateOf(Rect.Zero) })
    val viewportModifier = if (state.fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.offset(offset = { IntOffset(videoBounds.left.toInt(), videoBounds.top.toInt()) }).size(
            width = with(receiver = density, block = { videoBounds.width.toDp() }),
            height = with(receiver = density, block = { videoBounds.height.toDp() })
        )
    }
    val sourceAddress = state.sources.firstOrNull(predicate = { source -> source.id == state.activeSourceId })?.url
    LaunchedEffect(key1 = state.activeSourceId, key2 = sourceAddress, block = {
        initialChannelFocusPending = true
    })
    BackHandler(enabled = state.fullscreen, onBack = viewModel::exitFullscreen)
    LaunchedEffect(key1 = state.fullscreen, block = {
        if (!television) {
            return@LaunchedEffect
        }
        if (state.fullscreen) {
            returningFromFullscreen = true
        } else if (returningFromFullscreen) {
            videoFocus.requestFocus()
        } else if (state.channels.isEmpty()) {
            firstFocus.requestFocus()
        }
    })
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(BackdropTop, BackdropMiddle, BackdropBottom))),
        content = {
            // Keep one native video output attached while only changing its bounds.
            Box(modifier = viewportModifier.clip(RoundedCornerShape(if (state.fullscreen) 0.dp else 18.dp)).background(Color.Black), content = {
                PlaybackVideo(viewModel = viewModel)
            })
            if (state.fullscreen) {
                FullscreenPlayer(state = state, viewModel = viewModel, touchControls = !television)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = {
                    val narrowTv = television && maxWidth < 820.dp
                    val stacked = !television && maxHeight > maxWidth
                    val previewHeight = if (stacked) minOf((maxWidth - 24.dp) * (9f / 16f), maxHeight * 0.36f) else null
                    val listWidth = minOf(264.dp, maxWidth * if (television) 0.28f else 0.32f)
                    val channels: @Composable (Modifier) -> Unit = { modifier ->
                        ChannelPanel(
                            state = state,
                            onSelect = viewModel::select,
                            modifier = modifier,
                            compact = !television,
                            requestInitialFocus = television && initialChannelFocusPending && !sourceUi.open && !proxyOpen,
                            onInitialFocus = { initialChannelFocusPending = false },
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
                            onVideoBounds = { bounds -> videoBounds = bounds },
                            touchControls = !television,
                            previewHeight = previewHeight,
                            narrowTv = narrowTv
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = if (television && !narrowTv) 26.dp else 12.dp, vertical = if (television && !narrowTv) 20.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (television && !narrowTv) 16.dp else 8.dp),
                        content = {
                            HomeHeader(state = state, sourceViewModel = sourceViewModel, focusRequester = firstFocus, onProxy = { proxyOpen = true }, onRefresh = viewModel::refreshManually, compact = !television || narrowTv)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            if (stacked) {
                                playback(Modifier.fillMaxWidth())
                                channels(Modifier.weight(1f).fillMaxWidth())
                            } else {
                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (television && !narrowTv) 20.dp else 10.dp), content = {
                                    channels(Modifier.width(listWidth).fillMaxHeight())
                                    playback(Modifier.weight(1f).fillMaxHeight())
                                })
                            }
                        }
                    )
                })
            }
            SourceManagerDialog(viewModel = sourceViewModel)
            if (proxyOpen) {
                ProxySettingsDialog(store = app.proxySettings, onClose = { proxyOpen = false }, onSaved = viewModel::applyProxy)
            }
        }
    )
}

@Composable
private fun HomeHeader(state: PlayerState, sourceViewModel: SourceManagerViewModel, focusRequester: FocusRequester, onProxy: () -> Unit, onRefresh: () -> Unit, compact: Boolean) {
    val favorites = state.activeSourceId == SourceLibrary.FAVORITES_ID
    val source = state.sources.firstOrNull(predicate = { entry -> entry.id == state.activeSourceId })
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp), content = {
        TvIconButton(icon = R.drawable.ic_source, description = stringResource(if (favorites) R.string.favorites else R.string.library_title), onClick = sourceViewModel::open, modifier = Modifier.focusRequester(focusRequester))
        val sourceTitle = if (compact && favorites) stringResource(R.string.favorites) else source?.name
        if (sourceTitle != null && (compact || !favorites)) {
            Text(text = sourceTitle, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (compact) Modifier.weight(1f) else Modifier.widthIn(max = 230.dp))
        }
        TvIconButton(description = stringResource(R.string.favorites_count, state.favoriteCount), icon = if (favorites) R.drawable.ic_star_filled else R.drawable.ic_star, selected = favorites, onClick = sourceViewModel::showFavorites)
        if (!compact || sourceTitle == null) {
            Spacer(modifier = Modifier.weight(1f))
        }
        TvIconButton(icon = R.drawable.ic_refresh, description = stringResource(R.string.refresh), onClick = onRefresh)
        TvIconButton(description = stringResource(R.string.proxy_title), icon = R.drawable.ic_settings, onClick = onProxy)
        if (!compact) {
            TvIconButton(description = stringResource(R.string.library_title), onClick = sourceViewModel::open, icon = R.drawable.ic_library)
        }
    })
}
