package cn.hdfk7.android.iptv.app

import android.app.Application
import android.app.job.JobScheduler
import android.os.Build
import android.util.Log
import cn.hdfk7.android.iptv.core.data.local.ProxySettingsStore
import cn.hdfk7.android.iptv.core.data.network.PlaylistDownloader
import cn.hdfk7.android.iptv.core.data.repository.PlaylistRepository

class IptvApplication : Application() {
    val proxySettings by lazy(initializer = { ProxySettingsStore(this) })
    val playlistRepository: PlaylistRepository by lazy(initializer = { PlaylistRepository(filesDir, PlaylistDownloader(settings = { proxySettings.current })) })

    override fun onCreate() {
        super.onCreate()
        // Old versions scheduled background refresh; this app no longer schedules any jobs.
        try {
            val scheduler = getSystemService(JobScheduler::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                scheduler.cancelInAllNamespaces()
            } else {
                scheduler.cancelAll()
            }
        } catch (exception: RuntimeException) {
            Log.w("IptvApplication", "Unable to cancel legacy background jobs", exception)
        }
    }
}
