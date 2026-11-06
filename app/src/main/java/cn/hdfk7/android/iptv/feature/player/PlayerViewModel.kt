package cn.hdfk7.android.iptv.feature.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import cn.hdfk7.android.iptv.core.data.network.ProxyHttpClient
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.BuildConfig
import cn.hdfk7.android.iptv.core.model.ChannelAggregation
import cn.hdfk7.android.iptv.core.model.ChannelFocusMemory
import cn.hdfk7.android.iptv.app.IptvApplication
import cn.hdfk7.android.iptv.core.model.SourceLibrary
import cn.hdfk7.android.iptv.core.model.LibraryException
import cn.hdfk7.android.iptv.core.data.repository.RefreshEngine
import cn.hdfk7.android.iptv.core.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val INFORMATION_TIMEOUT_MS = 6000L
    }

    private val app = application as IptvApplication
    private val repository = app.playlistRepository
    private val audio = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    internal val channelFocusMemory = ChannelFocusMemory()
    private val mutableState = MutableStateFlow(PlayerState(fixedVolume = audio.isVolumeFixed))
    val state = mutableState.asStateFlow()
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    private var httpClient: okhttp3.OkHttpClient? = null
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeChannel: Channel? = null
    private var streamIndex = 0
    private var firstStreamIndex = 0
    private var wantsPlayback = false
    private var timeout: Job? = null
    private var fallback: Job? = null
    private var generation = 0
    private var refreshJob: Job? = null
    private var infoTimer: Job? = null
    private var informationHeld = false
    private var informationDeadline = 0L
    private var catalogToken: String? = null

    init {
        viewModelScope.launch(block = {
            var previousFavorites: List<Channel> = emptyList()
            var favoriteIds: Set<String> = emptySet()
            repository.state.collect(collector = { catalog ->
                val source = catalog.library.sources.firstOrNull(predicate = { entry -> entry.id == catalog.library.activeSourceId })
                val token = "${catalog.library.activeSourceId}:${source?.url.orEmpty()}"
                val switched = catalog.loaded && catalogToken != null && catalogToken != token
                val favoriteRemoved = catalog.library.activeSourceId == SourceLibrary.FAVORITES_ID && activeChannel != null && catalog.channels.none(predicate = { channel -> channel.id == activeChannel?.id })
                if (switched || favoriteRemoved) {
                    clearSelection()
                    if (catalog.channels.isEmpty() && state.value.fullscreen) {
                        exitFullscreen()
                    }
                }
                val playing = activeChannel
                val current = catalog.channels.firstOrNull(predicate = { channel -> channel.id == playing?.id })
                if (playing != null && current != null) {
                    activeChannel = playing.copy(name = current.name, sourceName = current.sourceName)
                }
                val snapshot = catalog.updatedAt
                val favorites = catalog.library.favorites
                if (favorites != previousFavorites) {
                    favoriteIds = (favorites + ChannelAggregation.merge(favorites, SourceLibrary.FAVORITES_ID))
                        .map(transform = { channel -> channel.id }).toSet()
                    previousFavorites = favorites
                }
                mutableState.value = state.value.copy(
                    channels = catalog.channels,
                    sources = catalog.library.sources,
                    activeSourceId = catalog.library.activeSourceId,
                    favoriteIds = favoriteIds,
                    selectedName = activeChannel?.name,
                    selectedSourceName = activeChannel?.sourceName,
                    updatedAt = snapshot,
                    stale = snapshot != null && System.currentTimeMillis() - snapshot >= RefreshEngine.DAY_MILLIS,
                    refreshing = catalog.refreshing,
                    refreshFailed = catalog.refreshFailed,
                    message = if (catalog.storageFailed) R.string.storage_error else state.value.message
                )
                if (catalog.loaded) {
                    catalogToken = token
                }
                val selected = activeChannel
                val selectionMissing = selected != null && catalog.channels.none(predicate = { channel -> channel.id == selected.id })
                if (catalog.channels.isNotEmpty() && (activeChannel == null || selectionMissing)) {
                    select(channel = catalog.channels.first())
                }
                if (switched) {
                    refreshJob?.cancel()
                    refresh()
                }
            })
        })
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = viewModelScope.launch(block = {
            try {
                repository.refresh()
            } catch (exception: LibraryException) {
                mutableState.value = state.value.copy(message = R.string.storage_error)
            }
        })
    }

    fun refreshManually() {
        if (refreshJob?.isActive == true) {
            return
        }
        refresh()
    }

    fun enterFullscreen() {
        if (activeChannel == null) {
            return
        }
        mutableState.value = state.value.copy(fullscreen = true)
        setPlayback(true)
    }

    fun exitFullscreen() {
        infoTimer?.cancel()
        mutableState.value = state.value.copy(fullscreen = false, infoVisible = false)
    }

    fun showInformation() {
        if (!state.value.infoVisible) {
            mutableState.value = state.value.copy(infoVisible = true)
        }
        informationDeadline = SystemClock.elapsedRealtime() + INFORMATION_TIMEOUT_MS
        if (informationHeld || infoTimer?.isActive == true) {
            return
        }
        infoTimer = viewModelScope.launch(block = {
            var remaining = informationDeadline - SystemClock.elapsedRealtime()
            while (remaining > 0) {
                delay(remaining)
                remaining = informationDeadline - SystemClock.elapsedRealtime()
            }
            mutableState.value = state.value.copy(infoVisible = false)
        })
    }

    fun holdInformation(held: Boolean) {
        if (informationHeld == held) {
            return
        }
        informationHeld = held
        if (held) {
            infoTimer?.cancel()
        } else if (state.value.infoVisible) {
            showInformation()
        }
    }

    fun hideInformation() {
        infoTimer?.cancel()
        mutableState.value = state.value.copy(infoVisible = false)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch(block = {
            try {
                repository.toggleFavorite(channel)
                mutableState.value = state.value.copy(message = null)
            } catch (exception: LibraryException) {
                mutableState.value = state.value.copy(message = R.string.storage_error)
            }
        })
    }

    fun toggleCurrentFavorite() {
        val channel = activeChannel ?: return
        toggleFavorite(channel)
    }

    private fun stopStream() {
        generation += 1
        fallback?.cancel()
        timeout?.cancel()
        wantsPlayback = false
        player?.stop()
        player?.clearMediaItems()
        mutableState.value = state.value.copy(playback = PlaybackStatus.IDLE, line = 0, lineCount = 0)
    }

    private fun clearSelection() {
        stopStream()
        activeChannel = null
        mutableState.value = state.value.copy(selectedId = null, selectedName = null, selectedSourceName = null)
    }

    fun applyProxy() {
        stop()
        streamIndex = 0
        start()
        refreshJob?.cancel()
        refresh()
    }

    fun start() {
        if (player != null) {
            return
        }
        val settings = app.proxySettings.current
        val client = ProxyHttpClient.create(settings, BuildConfig.STREAM_CONNECT_TIMEOUT_MS, BuildConfig.STREAM_READ_TIMEOUT_MS)
        httpClient = client
        val http = OkHttpDataSource.Factory(client)
        val created = ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        // Preserve explicit idle/error state during source changes.
                    }
                    Player.STATE_BUFFERING -> {
                        mutableState.value = state.value.copy(playback = PlaybackStatus.LOADING)
                        watchTimeout()
                    }
                    Player.STATE_READY -> {
                        timeout?.cancel()
                        updatePlayingState()
                    }
                    Player.STATE_ENDED -> {
                        nextLine()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (created.playbackState == Player.STATE_READY) {
                    updatePlayingState()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                nextLine()
            }
        })
        player = created
        if (activeChannel != null && wantsPlayback) {
            playLine()
        }
    }

    fun stop() {
        generation += 1
        fallback?.cancel()
        timeout?.cancel()
        val current = player
        player = null
        current?.release()
        val client = httpClient
        httpClient = null
        if (client != null) {
            cleanupScope.launch(block = {
                runCatching(block = { client.connectionPool.evictAll() })
                client.dispatcher.executorService.shutdown()
            })
        }
    }

    fun select(channel: Channel) {
        selectLine(channel, 0)
    }

    private fun selectLine(channel: Channel, index: Int) {
        generation += 1
        fallback?.cancel()
        timeout?.cancel()
        activeChannel = channel
        streamIndex = index
        firstStreamIndex = index
        wantsPlayback = true
        mutableState.value = state.value.copy(selectedId = channel.id, selectedName = channel.name, selectedSourceName = channel.sourceName)
        playLine()
        showInformation()
    }

    fun changeChannel(direction: Int) {
        val channels = state.value.channels
        if (channels.isEmpty()) {
            return
        }
        val current = channels.indexOfFirst(predicate = { channel -> channel.id == state.value.selectedId })
        val next = if (current < 0) {
            0
        } else {
            Math.floorMod(current + direction, channels.size)
        }
        val channel = channels[next]
        channelFocusMemory.remember(state.value.activeSourceId, channel.id)
        select(channel)
    }

    fun changeStreamLine(direction: Int) {
        val channel = activeChannel ?: return
        if (channel.streams.size > 1) {
            val index = Math.floorMod(state.value.line - 1 + direction, channel.streams.size)
            selectLine(channel, index)
        }
    }

    fun retryCurrentLine() {
        val channel = activeChannel ?: return
        val index = state.value.line - 1
        if (index in channel.streams.indices) {
            selectLine(channel, index)
        }
    }

    fun retry() {
        val current = state.value.channels.firstOrNull(predicate = { channel -> channel.id == state.value.selectedId }) ?: activeChannel
        if (current != null) {
            select(current)
        }
    }

    fun togglePlayback() {
        if (activeChannel == null || state.value.playback == PlaybackStatus.ERROR) {
            setPlayback(true)
            return
        }
        val current = player ?: return
        setPlayback(current.currentMediaItem == null || !current.playWhenReady)
    }

    fun setPlayback(playing: Boolean) {
        showInformation()
        if (playing && activeChannel == null) {
            val first = state.value.channels.firstOrNull() ?: return
            select(channel = first)
            return
        }
        if (playing && state.value.playback == PlaybackStatus.ERROR) {
            retry()
            return
        }
        if (playing && player?.currentMediaItem == null) {
            wantsPlayback = true
            playLine()
            return
        }
        wantsPlayback = playing
        player?.playWhenReady = playing
    }

    fun changeVolume(direction: Int) {
        if (audio.isVolumeFixed) {
            mutableState.value = state.value.copy(fixedVolume = true)
            return
        }
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (exception: SecurityException) {
            mutableState.value = state.value.copy(fixedVolume = true)
        }
    }

    private fun playLine() {
        val channel = activeChannel ?: return
        val current = player ?: return
        timeout?.cancel()
        val limit = channel.streams.size
        if (streamIndex >= limit) {
            current.stop()
            mutableState.value = state.value.copy(playback = PlaybackStatus.ERROR)
            return
        }
        mutableState.value = state.value.copy(playback = PlaybackStatus.LOADING, line = streamIndex + 1, lineCount = limit)
        if (current.currentMediaItem != null) {
            // Reset the decoder before switching between independent live streams.
            current.stop()
        }
        current.setMediaItem(MediaItem.fromUri(channel.streams[streamIndex]))
        watchTimeout()
        current.prepare()
        current.playWhenReady = wantsPlayback
    }

    private fun nextLine() {
        if (player == null || fallback?.isActive == true) {
            return
        }
        timeout?.cancel()
        val expectedGeneration = generation
        val count = activeChannel?.streams?.size ?: return
        if (count == 0 || streamIndex >= count) {
            return
        }
        val next = (streamIndex + 1) % count
        streamIndex = if (next == firstStreamIndex) count else next
        // Defer to avoid modifying the player inside its event dispatch.
        fallback = viewModelScope.launch(block = {
            yield()
            if (generation == expectedGeneration) {
                fallback = null
                playLine()
            }
        })
    }

    private fun watchTimeout() {
        if (timeout?.isActive == true) {
            return
        }
        timeout = viewModelScope.launch(block = {
            delay(BuildConfig.STREAM_PROBE_TIMEOUT_MS.toLong())
            nextLine()
        })
    }

    private fun updatePlayingState() {
        val status = if (player?.isPlaying == true) {
            PlaybackStatus.PLAYING
        } else {
            PlaybackStatus.PAUSED
        }
        mutableState.value = state.value.copy(playback = status)
    }

    override fun onCleared() {
        stop()
    }

}

