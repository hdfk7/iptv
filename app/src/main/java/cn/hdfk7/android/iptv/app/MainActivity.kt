package cn.hdfk7.android.iptv.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.ui.theme.IptvTheme
import cn.hdfk7.android.iptv.feature.source.SourceManagerViewModel
import cn.hdfk7.android.iptv.feature.player.PlayerScreen
import cn.hdfk7.android.iptv.feature.player.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val sourceViewModel: SourceManagerViewModel by viewModels()
    private var fullscreenBackPressed = false
    private var television = false
    private var lastBackPressTime = 0L

    private companion object {
        const val DOUBLE_BACK_INTERVAL_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        television = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        if (television) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!television) {
            // Let the app gradient draw behind the bars; Compose adds the insets back as padding.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
        setContent(content = {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(key1 = state.fullscreen, block = {
                if (television || state.fullscreen) {
                    insets.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insets.show(WindowInsetsCompat.Type.systemBars())
                }
            })
            IptvTheme(content = {
                PlayerScreen(viewModel = viewModel, sourceViewModel = sourceViewModel, television = television)
            })
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
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (television) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBackPressTime < DOUBLE_BACK_INTERVAL_MS) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        showBackToast()
                    }
                }
                return true
            }
            if (viewModel.state.value.fullscreen || fullscreenBackPressed) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    fullscreenBackPressed = true
                    viewModel.exitFullscreen()
                } else if (event.action == KeyEvent.ACTION_UP) {
                    fullscreenBackPressed = false
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showBackToast() {
        Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
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
