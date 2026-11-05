package cn.hdfk7.android.iptv.core.data.network

import cn.hdfk7.android.iptv.core.model.Channel
import cn.hdfk7.android.iptv.core.model.ChannelAggregation
import java.net.URI

/** Extended M3U channel catalog; HLS manifests are media, not channel catalogs. */
class PlaylistParser {
    fun parse(text: String, sourceUrl: String): List<Channel> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(transform = { line -> line.trim() }).toList()
        require(value = lines.firstOrNull()?.substringBefore(' ') == "#EXTM3U", lazyMessage = { "Expected an extended M3U catalog" })
        val mediaManifest = lines.any(predicate = { line -> line.startsWith("#EXT-X-") })
        require(value = !mediaManifest, lazyMessage = { "HLS media manifests are not channel catalogs" })
        val channels = linkedMapOf<Pair<String, String>, MutableList<String>>()
        var name: String? = null
        var group = ""
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                val separator = metadataSeparator(line)
                name = if (separator >= 0) {
                    line.substring(separator + 1).trim().ifEmpty(defaultValue = { attribute(line, "tvg-name") })
                } else {
                    null
                }
                group = attribute(line, "group-title")
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val channelName = name
                name = null
                if (channelName.isNullOrBlank()) {
                    continue
                }
                val uri = runCatching(block = { URI(sourceUrl).resolve(line) }).getOrNull()
                val supported = uri != null && uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
                if (supported) {
                    val urls = channels.getOrPut(key = group to channelName, defaultValue = { mutableListOf() })
                    val url = uri.toString()
                    if (!urls.contains(url)) {
                        urls.add(url)
                    }
                }
            }
        }
        return ChannelAggregation.merge(channels.map(transform = { entry -> Channel(entry.key.second, entry.key.first, entry.value.toList()) }))
    }

    private fun attribute(line: String, key: String): String {
        return Regex("""(?:^|\s)${Regex.escape(key)}="([^"]*)"""").find(line)?.groupValues?.get(1).orEmpty()
    }

    private fun metadataSeparator(line: String): Int {
        var quoted = false
        for ((index, character) in line.withIndex()) {
            if (character == '"') {
                quoted = !quoted
            } else if (character == ',' && !quoted) {
                return index
            }
        }
        return -1
    }
}
