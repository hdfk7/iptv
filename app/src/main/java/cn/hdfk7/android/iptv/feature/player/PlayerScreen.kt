package cn.hdfk7.android.iptv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.app.IptvApplication
import cn.hdfk7.android.iptv.core.model.SourceLibrary
import cn.hdfk7.android.iptv.core.ui.components.LocalTvActionFocused
import cn.hdfk7.android.iptv.core.ui.components.TvAction
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.theme.*
import cn.hdfk7.android.iptv.feature.phone.PhonePlayerScreen
import cn.hdfk7.android.iptv.feature.tv.TvPlayerScreen
import cn.hdfk7.android.iptv.feature.proxy.ProxySettingsDialog
import cn.hdfk7.android.iptv.feature.update.AppUpdateDialog
import cn.hdfk7.android.iptv.feature.source.SourceManagerDialog
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, sourceViewModel: SourceManagerViewModel, television: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sourceUi by sourceViewModel.state.collectAsStateWithLifecycle()
    var settingsPage by remember(calculation = { mutableStateOf<SettingsPage?>(null) })
    val app = LocalContext.current.applicationContext as IptvApplication
    val firstFocus = remember(calculation = { FocusRequester() })
    val selectedRowFocus = remember(key1 = viewModel, calculation = { ChannelListFocus(viewModel.channelFocusMemory) })
    var initialChannelFocusPending by remember(calculation = { mutableStateOf(true) })
    val videoFocus = remember(calculation = { FocusRequester() })
    val density = LocalDensity.current
    var videoBounds by remember(calculation = { mutableStateOf(Rect.Zero) })
    val viewportModifier = if (state.fullscreen || television) {
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
    val dialogOpen = sourceUi.open || settingsPage != null
    LaunchedEffect(television, dialogOpen, block = {
        if (television) {
            viewModel.holdInformation(dialogOpen)
        }
    })
    BackHandler(enabled = state.fullscreen, onBack = viewModel::exitFullscreen)
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(BackdropTop, BackdropMiddle, BackdropBottom))),
        content = {
            // Keep one native video output attached while only changing its bounds.
            Box(modifier = viewportModifier.clip(RoundedCornerShape(if (state.fullscreen || television) 0.dp else 18.dp)).background(Color.Black), content = {
                PlaybackVideo(viewModel = viewModel)
            })
            if (television) {
                TvPlayerScreen(
                    state = state,
                    viewModel = viewModel,
                    sourceViewModel = sourceViewModel,
                    onSettings = { settingsPage = SettingsPage.Menu },
                    firstFocus = firstFocus,
                    selectedRowFocus = selectedRowFocus,
                    videoFocus = videoFocus,
                    dialogOpen = dialogOpen,
                    initialChannelFocusPending = initialChannelFocusPending && !sourceUi.open && settingsPage == null,
                    onInitialFocus = { initialChannelFocusPending = false }
                )
            } else {
                PhonePlayerScreen(
                    state = state,
                    viewModel = viewModel,
                    sourceViewModel = sourceViewModel,
                    onSettings = { settingsPage = SettingsPage.Menu },
                    firstFocus = firstFocus,
                    selectedRowFocus = selectedRowFocus,
                    videoFocus = videoFocus,
                    onVideoBounds = { bounds -> videoBounds = bounds },
                    onInitialFocus = { initialChannelFocusPending = false }
                )
            }
            SourceManagerDialog(viewModel = sourceViewModel)
            when (settingsPage) {
                SettingsPage.Menu -> {
                    HomeSettingsDialog(
                        onClose = { settingsPage = null },
                        onProxy = { settingsPage = SettingsPage.Proxy },
                        onUpdate = { settingsPage = SettingsPage.Update },
                        television = television
                    )
                }
                SettingsPage.Proxy -> {
                    ProxySettingsDialog(store = app.proxySettings, onClose = { settingsPage = SettingsPage.Menu }, onSaved = viewModel::applyProxy)
                }
                SettingsPage.Update -> {
                    AppUpdateDialog(onClose = { settingsPage = SettingsPage.Menu })
                }
                null -> Unit
            }
        }
    )
}

@Composable
fun HomeHeader(state: PlayerState, sourceViewModel: SourceManagerViewModel, focusRequester: FocusRequester, onSettings: () -> Unit, onRefresh: () -> Unit, compact: Boolean, selectedRowFocus: ChannelListFocus, modifier: Modifier = Modifier, topFocus: FocusRequester? = null, settingsFocus: FocusRequester? = null, sourceNavigation: Modifier? = null, refreshNavigation: Modifier = Modifier, settingsNavigation: Modifier = Modifier) {
    val favorites = state.activeSourceId == SourceLibrary.FAVORITES_ID
    val source = state.sources.firstOrNull(predicate = { entry -> entry.id == state.activeSourceId })
    val downToList = if (state.channels.isEmpty()) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent(onPreviewKeyEvent = { event ->
            if (event.key == Key.DirectionDown) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    selectedRowFocus.focusSelected?.invoke()
                }
                true
            } else {
                false
            }
        })
    }
    val sourceTitle = if (favorites) stringResource(R.string.favorites) else source?.name ?: stringResource(R.string.library_title)
    Row(modifier = modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp), content = {
        TvAction(onClick = sourceViewModel::open, modifier = Modifier.focusRequester(focusRequester).then(sourceNavigation ?: downToList).widthIn(max = if (compact) 210.dp else 300.dp), containerColor = ButtonPanel, contentPadding = PaddingValues(horizontal = 14.dp), content = {
            Text(text = sourceTitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = stringResource(if (favorites) R.string.favorites else R.string.library_title),
                tint = if (LocalTvActionFocused.current) FocusInk else Muted,
                modifier = Modifier.size(14.dp)
            )
        })
        Spacer(modifier = Modifier.weight(1f))
        TvIconButton(icon = R.drawable.ic_refresh, description = stringResource(R.string.refresh), onClick = onRefresh, modifier = (if (topFocus != null) Modifier.focusRequester(topFocus) else Modifier).then(refreshNavigation))
        TvIconButton(description = stringResource(R.string.settings_title), icon = R.drawable.ic_settings, onClick = onSettings, modifier = (if (settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier).then(settingsNavigation))
    })
}

private enum class SettingsPage { Menu, Proxy, Update }

@Composable
private fun HomeSettingsDialog(onClose: () -> Unit, onProxy: () -> Unit, onUpdate: () -> Unit, television: Boolean) {
    val proxyFocus = remember(calculation = { FocusRequester() })
    Dialog(onDismissRequest = onClose, content = {
        val inputMode = LocalInputModeManager.current
        Column(
            modifier = Modifier.fillMaxWidth().background(DialogSurface, RoundedCornerShape(22.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, content = {
                    Text(text = stringResource(R.string.settings_title), color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TvIconButton(icon = R.drawable.ic_close, description = stringResource(R.string.settings_close), onClick = onClose)
                })
                SettingsEntry(icon = R.drawable.ic_settings, title = stringResource(R.string.proxy_title), onClick = onProxy, modifier = Modifier.focusRequester(proxyFocus))
                SettingsEntry(icon = R.drawable.ic_update, title = stringResource(R.string.app_update_title), onClick = onUpdate)
                LaunchedEffect(key1 = television, block = {
                    if (television) {
                        inputMode.requestInputMode(InputMode.Keyboard)
                        proxyFocus.requestFocus()
                    }
                })
            }
        )
    })
}

@Composable
private fun SettingsEntry(icon: Int, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvAction(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = ButtonPanel,
        content = {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, modifier = Modifier.weight(1f))
        }
    )
}
