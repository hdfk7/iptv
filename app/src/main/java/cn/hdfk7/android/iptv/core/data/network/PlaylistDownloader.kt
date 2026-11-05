package cn.hdfk7.android.iptv.core.data.network

import java.io.IOException
import cn.hdfk7.android.iptv.core.model.ProxySettings
import okhttp3.Request

class PlaylistDownloader(private val settings: () -> ProxySettings = { ProxySettings() }) {
    fun download(address: String): String {
        val client = ProxyHttpClient.create(settings()).newBuilder().followRedirects(false).followSslRedirects(false).build()
        try {
            client.newCall(Request.Builder().url(address).header("User-Agent", "iptv/1.0").build()).execute().use(block = { response ->
                if (response.code != 200) {
                    throw IOException("Playlist HTTP ${response.code}")
                }
                val bytes = (response.body ?: throw IOException("Empty playlist")).byteStream().use(block = { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count = input.read(buffer)
                    while (count >= 0) {
                        output.write(buffer, 0, count)
                        if (output.size() > MAX_BYTES) {
                            throw IOException("Playlist exceeds size limit")
                        }
                        count = input.read(buffer)
                    }
                    output.toByteArray()
                })
                return bytes.toString(Charsets.UTF_8)
            })
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
    }
}
