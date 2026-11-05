package cn.hdfk7.android.iptv.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.hdfk7.android.iptv.app.IptvApplication
import cn.hdfk7.android.iptv.core.model.LibraryException

class PlaylistRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as IptvApplication
        return try {
            val result = application.playlistRepository.refresh()
            if (result.failed) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (exception: LibraryException) {
            Result.retry()
        }
    }
}
