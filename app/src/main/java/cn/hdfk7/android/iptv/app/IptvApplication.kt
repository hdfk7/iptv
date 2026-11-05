package cn.hdfk7.android.iptv.app

import android.app.Application
import androidx.work.WorkManager
import cn.hdfk7.android.iptv.core.data.local.ProxySettingsStore
import cn.hdfk7.android.iptv.core.data.network.PlaylistDownloader
import cn.hdfk7.android.iptv.core.data.repository.PlaylistRepository

class IptvApplication : Application() {
    val proxySettings by lazy(initializer = { ProxySettingsStore(this) })
    val playlistRepository: PlaylistRepository by lazy(initializer = { PlaylistRepository(filesDir, PlaylistDownloader(settings = { proxySettings.current })) })

    override fun onCreate() {
        super.onCreate()
        // Upgrade cleanup only; no new background refresh is scheduled.
        WorkManager.getInstance(this).cancelUniqueWork("daily-playlist")
    }
}
