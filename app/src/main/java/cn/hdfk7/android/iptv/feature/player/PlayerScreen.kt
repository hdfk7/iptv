package cn.hdfk7.android.iptv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.model.Channel
import cn.hdfk7.android.iptv.core.model.SourceLibrary
import cn.hdfk7.android.iptv.core.ui.components.TvAction
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.theme.Accent
import cn.hdfk7.android.iptv.core.ui.theme.Gold
import cn.hdfk7.android.iptv.core.ui.theme.Muted
import cn.hdfk7.android.iptv.feature.source.SourceManagerDialog
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import cn.hdfk7.android.iptv.app.IptvApplication
import cn.hdfk7.android.iptv.feature.proxy.ProxySettingsDialog

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, sourceViewModel: SourceManagerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var proxyOpen by remember(calculation = { mutableStateOf(false) })
    val app = LocalContext.current.applicationContext as IptvApplication
    val firstFocus = remember(calculation = { FocusRequester() })
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
    BackHandler(enabled = state.fullscreen, onBack = viewModel::exitFullscreen)
    LaunchedEffect(key1 = state.fullscreen, block = {
        if (!state.fullscreen) {
            firstFocus.requestFocus()
        }
    })
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF14283A), Color(0xFF0B1423), Color(0xFF080F1B)))),
        content = {
            // Keep one native video output attached while only changing its bounds.
            Box(modifier = viewportModifier.clip(RoundedCornerShape(if (state.fullscreen) 0.dp else 18.dp)).background(Color.Black), content = {
                PlaybackVideo(viewModel = viewModel)
            })
            if (state.fullscreen) {
                FullscreenPlayer(state = state, viewModel = viewModel)
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = {
                    HomeHeader(state = state, sourceViewModel = sourceViewModel, focusRequester = firstFocus, onProxy = { proxyOpen = true })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp), content = {
                        ChannelPanel(state = state, onSelect = viewModel::select, onFavorite = viewModel::toggleFavorite, onRefresh = viewModel::refresh)
                        PlaybackPanel(state = state, viewModel = viewModel, modifier = Modifier.weight(1f), onVideoBounds = { bounds -> videoBounds = bounds })
                    })
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
private fun HomeHeader(state: PlayerState, sourceViewModel: SourceManagerViewModel, focusRequester: FocusRequester, onProxy: () -> Unit) {
    val favorites = state.activeSourceId == SourceLibrary.FAVORITES_ID
    val source = state.sources.firstOrNull(predicate = { entry -> entry.id == state.activeSourceId })
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = {
        TvIconButton(icon = R.drawable.ic_source, description = stringResource(if (favorites) R.string.favorites else R.string.library_title), onClick = sourceViewModel::open, modifier = Modifier.focusRequester(focusRequester))
        if (!favorites && source != null) {
            Text(text = source.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 230.dp))
        }
        TvIconButton(description = stringResource(R.string.favorites_count, state.favoriteCount), icon = R.drawable.ic_star, selected = favorites, onClick = sourceViewModel::showFavorites)
        Spacer(modifier = Modifier.weight(1f))
        TvIconButton(description = stringResource(R.string.proxy_title), icon = R.drawable.ic_proxy, onClick = onProxy)
        TvIconButton(description = stringResource(R.string.library_title), onClick = sourceViewModel::open, icon = R.drawable.ic_edit)
    })
}

@Composable
private fun ChannelPanel(state: PlayerState, onSelect: (Channel) -> Unit, onFavorite: (Channel) -> Unit, onRefresh: () -> Unit) {
    val favorites = state.activeSourceId == SourceLibrary.FAVORITES_ID
    val listState = rememberLazyListState()
    LaunchedEffect(key1 = state.activeSourceId, block = { listState.scrollToItem(0) })
    Column(
        modifier = Modifier.width(264.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xCC101C2C)).border(1.dp, Color(0xFF253347), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                    Text(text = state.channels.size.toString(), color = Muted, fontSize = 14.sp)
                })
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                }
                TvIconButton(icon = R.drawable.ic_refresh, description = stringResource(R.string.refresh), onClick = onRefresh)
            })
            if (state.refreshFailed || state.message != null) {
                CatalogMessage(state = state)
            }
            if (state.channels.isEmpty()) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = {
                    Icon(painter = painterResource(if (favorites) R.drawable.ic_star else R.drawable.ic_source), contentDescription = null, tint = if (favorites) Gold else Muted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = stringResource(if (favorites) R.string.favorites_empty else R.string.empty), color = Color.White, fontSize = 15.sp)
                })
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = {
                    itemsIndexed(items = state.channels, key = { _, channel -> channel.id }, itemContent = { index, channel ->
                        ChannelRow(channel = channel, index = index, selected = channel.id == state.selectedId, favorite = channel.id in state.favoriteIds, showSource = favorites, onSelect = { onSelect(channel) }, onFavorite = { onFavorite(channel) })
                    })
                })
            }
        }
    )
}

@Composable
private fun ChannelRow(channel: Channel, index: Int, selected: Boolean, favorite: Boolean, showSource: Boolean, onSelect: () -> Unit, onFavorite: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), content = {
        TvAction(modifier = Modifier.weight(1f).height(58.dp), onClick = onSelect, selected = selected, content = {
            Text(text = stringResource(R.string.channel_number, index + 1), fontSize = 10.sp, color = if (selected) Accent else Muted, modifier = Modifier.width(25.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                Text(text = channel.name, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = if (showSource) channel.sourceName else channel.group, color = if (selected) Accent else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
        })
        TvIconButton(icon = R.drawable.ic_star, description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add), onClick = onFavorite, tint = if (favorite) Gold else Muted, selected = favorite)
    })
}

@Composable
private fun CatalogMessage(state: PlayerState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = {
        if (state.message != null) {
            Text(text = stringResource(state.message), color = Gold, fontSize = 11.sp)
        }
        if (state.refreshFailed) {
            Text(text = stringResource(if (state.channels.isEmpty()) R.string.refresh_error else R.string.cached), color = Gold, fontSize = 11.sp)
        } else if (state.activeSourceId == SourceLibrary.FAVORITES_ID) {
            Text(text = stringResource(R.string.source_aggregate), color = Muted, fontSize = 11.sp)
        } else if (state.updatedAt != null) {
            val timestamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))
            Text(text = stringResource(R.string.updated, timestamp), color = Muted, fontSize = 10.sp)
        }
        if (state.stale) {
            Text(text = stringResource(R.string.stale), color = Gold, fontSize = 11.sp)
        }
    })
}

@Composable
private fun PlaybackPanel(state: PlayerState, viewModel: PlayerViewModel, modifier: Modifier, onVideoBounds: (Rect) -> Unit) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp), content = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                Text(text = state.selectedName.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.selectedSourceName != null) {
                    Text(text = state.selectedSourceName, color = Muted, fontSize = 11.sp)
                }
            })
            if (state.selectedId != null) {
                TvIconButton(icon = R.drawable.ic_star, description = stringResource(if (state.selectedId in state.favoriteIds) R.string.favorite_remove else R.string.favorite_add), onClick = viewModel::toggleCurrentFavorite, tint = if (state.selectedId in state.favoriteIds) Gold else Muted)
            }
        })
        PlaybackSurface(state = state, modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned(onGloballyPositioned = { coordinates ->
            onVideoBounds(Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
        }))
        PlaybackControls(state = state, viewModel = viewModel)
    })
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackSurface(state: PlayerState, modifier: Modifier) {
    val frameModifier = if (state.fullscreen) {
        modifier
    } else {
        modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0xFF2A3C50), RoundedCornerShape(18.dp))
    }
    Box(modifier = frameModifier, contentAlignment = Alignment.Center, content = {
        if (state.playback == PlaybackStatus.IDLE || state.playback == PlaybackStatus.ERROR || state.playback == PlaybackStatus.LOADING) {
            Column(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF18303B), Color(0xFF070D17)))), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = {
                if (state.playback == PlaybackStatus.LOADING) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Accent, strokeWidth = 3.dp)
                } else {
                    Box(modifier = Modifier.size(60.dp).background(Color.White.copy(alpha = 0.05f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center, content = {
                        Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
                    })
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (state.playback != PlaybackStatus.IDLE) {
                    Text(text = playbackMessage(state), color = Color.White, fontSize = 16.sp)
                }
            })
        }
        if (!state.fullscreen) {
            Row(modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 9.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = {
                Box(modifier = Modifier.size(5.dp).background(if (state.playback == PlaybackStatus.PLAYING) Accent else Muted, CircleShape))
                Text(text = stringResource(R.string.live_label), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            })
        }
        if (state.playback == PlaybackStatus.PAUSED) {
            Text(text = stringResource(R.string.paused), color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(14.dp))
        }
    })
}

@Composable
private fun playbackMessage(state: PlayerState): String {
    return when (state.playback) {
        PlaybackStatus.IDLE -> {
            stringResource(R.string.choose_channel)
        }
        PlaybackStatus.LOADING -> {
            stringResource(R.string.loading, state.line, state.lineCount)
        }
        PlaybackStatus.ERROR -> {
            stringResource(R.string.stream_error)
        }
        PlaybackStatus.PAUSED -> {
            stringResource(R.string.paused)
        }
        PlaybackStatus.PLAYING -> {
            stringResource(R.string.playing)
        }
    }
}

@Composable
private fun PlaybackControls(state: PlayerState, viewModel: PlayerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), content = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = {
            TvIconButton(icon = R.drawable.ic_previous, description = stringResource(R.string.previous), onClick = { viewModel.changeChannel(-1) })
            TvIconButton(description = stringResource(if (state.playback == PlaybackStatus.PLAYING) R.string.pause else R.string.play), icon = if (state.playback == PlaybackStatus.PLAYING) R.drawable.ic_pause else R.drawable.ic_play, onClick = viewModel::togglePlayback, selected = true)
            TvIconButton(icon = R.drawable.ic_next, description = stringResource(R.string.next), onClick = { viewModel.changeChannel(1) })
            TvIconButton(icon = R.drawable.ic_refresh, description = stringResource(R.string.retry), onClick = viewModel::retry)
            Spacer(modifier = Modifier.weight(1f))
            TvIconButton(icon = R.drawable.ic_fullscreen, description = stringResource(R.string.fullscreen), onClick = viewModel::enterFullscreen)
            Text(text = if (state.line > 0) stringResource(R.string.line_detail, state.line, state.lineCount) else stringResource(R.string.live_label), fontSize = 11.sp, color = Muted)
            TvIconButton(icon = R.drawable.ic_volume_down, description = stringResource(R.string.volume_down), onClick = { viewModel.changeVolume(android.media.AudioManager.ADJUST_LOWER) })
            TvIconButton(icon = R.drawable.ic_volume_up, description = stringResource(R.string.volume_up), onClick = { viewModel.changeVolume(android.media.AudioManager.ADJUST_RAISE) })
        })
        if (state.fixedVolume) {
            Text(text = stringResource(R.string.fixed_volume), color = Muted, fontSize = 10.sp)
        }
    })
}
