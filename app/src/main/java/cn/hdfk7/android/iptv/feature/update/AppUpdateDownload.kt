package cn.hdfk7.android.iptv.feature.update

import android.content.Context
import cn.hdfk7.android.iptv.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private val progressInterval = TimeUnit.MILLISECONDS.toNanos(120)
private val updateClient = OkHttpClient.Builder().followSslRedirects(false).readTimeout(3, TimeUnit.MINUTES).build()

internal class AppUpdateDownloadException(val messageRes: Int) : IOException()

internal data class AppDownloadProgress(val downloaded: Long, val total: Long) {
    val fraction: Float?
        get() = if (total > 0) (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
}

internal fun copyUpdateBody(input: InputStream, output: OutputStream, total: Long, isActive: () -> Boolean, onProgress: (AppDownloadProgress) -> Unit) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloaded = 0L
    var reportedAt = System.nanoTime()
    onProgress(AppDownloadProgress(downloaded, total))
    while (isActive()) {
        val count = input.read(buffer)
        if (count < 0) {
            if (total >= 0 && downloaded != total) {
                throw IOException("Incomplete update download")
            }
            onProgress(AppDownloadProgress(downloaded, total))
            return
        }
        output.write(buffer, 0, count)
        downloaded += count
        val now = System.nanoTime()
        if (now - reportedAt >= progressInterval) {
            reportedAt = now
            onProgress(AppDownloadProgress(downloaded, total))
        }
    }
    throw CancellationException("Update download cancelled")
}

internal suspend fun downloadApk(context: Context, release: AppRelease, onProgress: (AppDownloadProgress) -> Unit): File =
    suspendCancellableCoroutine(block = { continuation ->
        val call = updateClient.newCall(Request.Builder().url(release.downloadUrl).build())
        continuation.invokeOnCancellation(handler = { call.cancel() })
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching(block = {
                    response.use(block = { reply ->
                        check(reply.isSuccessful)
                        val body = checkNotNull(reply.body)
                        val directory = File(context.cacheDir, "updates")
                        if (!directory.isDirectory && !directory.mkdirs()) {
                            throw AppUpdateDownloadException(R.string.app_update_save_failed)
                        }
                        val pending = try {
                            File.createTempFile("iptv-", ".apk", directory)
                        } catch (_: IOException) {
                            throw AppUpdateDownloadException(R.string.app_update_save_failed)
                        }
                        try {
                            body.byteStream().use(block = { input ->
                                pending.outputStream().use(block = { output ->
                                    copyUpdateBody(input, output, body.contentLength(), isActive = { continuation.isActive }, onProgress = onProgress)
                                })
                            })
                            val archive = context.packageManager.getPackageArchiveInfo(pending.path, 0)
                                ?: throw AppUpdateDownloadException(R.string.app_update_archive_unreadable)
                            @Suppress("DEPRECATION")
                            if (archive.packageName != context.packageName || archive.versionCode != release.versionCode) {
                                throw AppUpdateDownloadException(R.string.app_update_archive_mismatch)
                            }
                            if (!continuation.isActive) {
                                throw CancellationException("Update download cancelled")
                            }
                            val apk = File(directory, "iptv-update.apk")
                            if (!pending.renameTo(apk)) {
                                throw AppUpdateDownloadException(R.string.app_update_save_failed)
                            }
                            apk
                        } finally {
                            pending.delete()
                        }
                    })
                })
                continuation.resumeWith(result)
            }
        })
    })
