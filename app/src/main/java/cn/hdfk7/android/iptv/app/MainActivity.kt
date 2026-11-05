package cn.hdfk7.android.iptv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.hdfk7.android.iptv.core.ui.theme.IptvTheme
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel
import cn.hdfk7.android.iptv.feature.player.PlayerScreen
import cn.hdfk7.android.iptv.feature.player.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val sourceViewModel: SourceManagerViewModel by viewModels()
    private var fullscreenBackPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent(content = {
            IptvTheme(content = { PlayerScreen(viewModel = viewModel, sourceViewModel = sourceViewModel) })
        })
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
        viewModel.refresh()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    // Intercept TV BACK before focused Compose controls; this is Activity's public callback.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && (viewModel.state.value.fullscreen || fullscreenBackPressed)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                fullscreenBackPressed = true
                viewModel.exitFullscreen()
            } else if (event.action == KeyEvent.ACTION_UP) {
                fullscreenBackPressed = false
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = keyCode in listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (handled) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        viewModel.changeChannel(1)
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        viewModel.changeChannel(-1)
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.togglePlayback()
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        viewModel.setPlayback(true)
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        viewModel.setPlayback(false)
                    }
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}
