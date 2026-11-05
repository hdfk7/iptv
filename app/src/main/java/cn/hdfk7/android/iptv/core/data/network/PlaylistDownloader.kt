package cn.hdfk7.android.iptv.core.data.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class PlaylistDownloader {
    fun download(address: String): String {
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "iptv/1.0")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Playlist HTTP ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use(block = { input ->
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
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
    }
}
