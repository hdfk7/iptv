package cn.hdfk7.android.iptv.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.model.Channel
import cn.hdfk7.android.iptv.core.model.ChannelFocusMemory
import cn.hdfk7.android.iptv.core.model.SourceLibrary
import cn.hdfk7.android.iptv.core.ui.components.LocalTvActionFocused
import cn.hdfk7.android.iptv.core.ui.components.TvAction
import cn.hdfk7.android.iptv.core.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private object NoFocusScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

internal class ChannelListFocus {
    val requesters = mutableStateMapOf<String, FocusRequester>()
    val memory = ChannelFocusMemory()
    var restore: (() -> Unit)? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelPanel(state: PlayerState, onSelect: (Channel) -> Unit, modifier: Modifier, compact: Boolean, requestInitialFocus: Boolean, onInitialFocus: () -> Unit, selectedRowFocus: ChannelListFocus, videoFocus: FocusRequester) {
    val favorites = state.activeSourceId == SourceLibrary.FAVORITES_ID
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusSelection by remember(calculation = { mutableStateOf<Job?>(null) })
    SideEffect(effect = {
        selectedRowFocus.restore = {
            scope.launch(block = {
                val target = selectedRowFocus.memory.target(state.activeSourceId, state.channels, state.selectedId)
                if (target != null) {
                    val index = state.channels.indexOf(target)
                    if (listState.layoutInfo.visibleItemsInfo.none(predicate = { item -> item.index == index })) {
                        listState.scrollToItem(index)
                    }
                    withTimeoutOrNull(1000, block = {
                        val requester = snapshotFlow(block = { selectedRowFocus.requesters[target.id] }).first(predicate = { requester -> requester != null })
                        withFrameNanos(onFrame = { })
                        requester?.requestFocus()
                    })
                }
            })
        }
    })
    var enteringList by remember(calculation = { mutableStateOf(false) })
    val selectChannel: (Channel) -> Unit = { channel ->
        if (channel.id != state.selectedId || state.playback == PlaybackStatus.IDLE || state.playback == PlaybackStatus.ERROR) {
            onSelect(channel)
        }
    }
    LaunchedEffect(key1 = state.activeSourceId, block = { listState.scrollToItem(0) })
    LaunchedEffect(key1 = state.selectedId, key2 = state.channels.size, block = {
        val index = state.channels.indexOfFirst(predicate = { channel -> channel.id == state.selectedId })
        if (index >= 0) {
            listState.centerItem(index = index)
        }
    })
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Border, RoundedCornerShape(18.dp)).padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                    Text(text = if (compact) stringResource(R.string.channel_count, state.channels.size) else state.channels.size.toString(), color = Muted, fontSize = if (compact) 12.sp else 14.sp)
                })
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                }
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
                CompositionLocalProvider(LocalBringIntoViewSpec provides NoFocusScrollSpec) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).onFocusChanged(onFocusChanged = { focus ->
                        if (!focus.hasFocus) {
                            enteringList = true
                            focusSelection?.cancel()
                        }
                    }), verticalArrangement = Arrangement.spacedBy(6.dp), content = {
                        itemsIndexed(items = state.channels, key = { _, channel -> channel.id }, itemContent = { index, channel ->
                            ChannelRow(channel = channel, index = index, selected = channel.id == state.selectedId, showSource = favorites, requestFocus = requestInitialFocus && index == 0, selectedRowFocus = selectedRowFocus, onInitialFocus = onInitialFocus, onVideoFocus = { focusSelection?.cancel(); selectChannel(channel); videoFocus.requestFocus() }, onFocused = {
                                if (enteringList) {
                                    enteringList = false
                                } else {
                                    focusSelection?.cancel()
                                    focusSelection = scope.launch(block = {
                                        delay(300)
                                        selectChannel(channel)
                                    })
                                }
                            }, onSelect = { focusSelection?.cancel(); selectChannel(channel) })
                        })
                    })
                }
            }
        }
    )
}

private suspend fun LazyListState.centerItem(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo.firstOrNull(predicate = { item -> item.index == index })
    if (visible != null) {
        val delta = visible.offset + visible.size / 2 - info.viewportSize.height / 2
        if (delta != 0) {
            scrollBy(value = delta.toFloat())
        }
        return
    }
    scrollToItem(index = index)
    val after = layoutInfo
    val item = after.visibleItemsInfo.firstOrNull(predicate = { visibleItem -> visibleItem.index == index }) ?: return
    val delta = item.offset + item.size / 2 - after.viewportSize.height / 2
    if (delta != 0) {
        scrollBy(value = delta.toFloat())
    }
}

@Composable
private fun ChannelRow(channel: Channel, index: Int, selected: Boolean, showSource: Boolean, requestFocus: Boolean, selectedRowFocus: ChannelListFocus, onInitialFocus: () -> Unit, onVideoFocus: () -> Unit, onFocused: () -> Unit, onSelect: () -> Unit) {
    val channelFocus = remember(calculation = { FocusRequester() })
    val view = LocalView.current
    val inputMode = LocalInputModeManager.current
    DisposableEffect(key1 = channel.id, effect = {
        selectedRowFocus.requesters[channel.id] = channelFocus
        onDispose(onDisposeEffect = { selectedRowFocus.requesters.remove(channel.id) })
    })
    LaunchedEffect(key1 = requestFocus, block = {
        if (!requestFocus) {
            return@LaunchedEffect
        }
        var waited = 0
        while (isActive && waited < 3000) {
            if (view.hasWindowFocus()) {
                inputMode.requestInputMode(InputMode.Keyboard)
                withFrameNanos(onFrame = { })
                if (runCatching(block = { channelFocus.requestFocus() }).getOrDefault(false)) {
                    onInitialFocus()
                    return@LaunchedEffect
                }
            }
            delay(50)
            waited += 50
        }
    })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), content = {
        TvAction(modifier = Modifier.fillMaxWidth().height(58.dp).focusRequester(channelFocus).onPreviewKeyEvent(onPreviewKeyEvent = { event ->
            if (event.key == Key.DirectionRight) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    onVideoFocus()
                }
                true
            } else {
                false
            }
        }).onFocusChanged(onFocusChanged = { focus ->
            if (focus.isFocused) {
                selectedRowFocus.memory.remember(channel.sourceId, channel.id)
                onFocused()
            }
        }), onClick = onSelect, selected = selected, content = {
            Text(text = stringResource(R.string.channel_number, index + 1), fontSize = 10.sp, color = if (LocalTvActionFocused.current) FocusInk.copy(alpha = 0.65f) else if (selected) Accent else Muted, modifier = Modifier.width(25.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = {
                Text(text = channel.name, color = if (LocalTvActionFocused.current) FocusInk else Accent, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = if (showSource) channel.sourceName else channel.group, color = if (LocalTvActionFocused.current) FocusInk.copy(alpha = 0.65f) else if (selected) Accent else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
        })
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
