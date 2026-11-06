package cn.hdfk7.android.iptv.feature.update

import android.content.Context
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
private val updateClient = OkHttpClient.Builder().followSslRedirects(false).build()

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
                        check(directory.isDirectory || directory.mkdirs())
                        val pending = File.createTempFile("iptv-", ".apk", directory)
                        try {
                            body.byteStream().use(block = { input ->
                                pending.outputStream().use(block = { output ->
                                    copyUpdateBody(input, output, body.contentLength(), isActive = { continuation.isActive }, onProgress = onProgress)
                                })
                            })
                            val archive = checkNotNull(context.packageManager.getPackageArchiveInfo(pending.path, 0))
                            @Suppress("DEPRECATION")
                            check(archive.packageName == context.packageName && archive.versionCode == release.versionCode)
                            if (!continuation.isActive) {
                                throw CancellationException("Update download cancelled")
                            }
                            val apk = File(directory, "iptv-update.apk")
                            check(pending.renameTo(apk))
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
