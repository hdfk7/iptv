package cn.hdfk7.android.iptv.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.hdfk7.android.iptv.core.data.repository.PlaylistRepository
import cn.hdfk7.android.iptv.core.data.worker.PlaylistRefreshWorker
import java.util.concurrent.TimeUnit

class IptvApplication : Application() {
    val playlistRepository: PlaylistRepository by lazy(initializer = { PlaylistRepository(filesDir) })

    override fun onCreate() {
        super.onCreate()
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily-playlist", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
