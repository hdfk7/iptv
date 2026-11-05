package cn.hdfk7.android.iptv.core.data.local

import cn.hdfk7.android.iptv.core.model.Channel
import org.json.JSONArray
import org.json.JSONObject

internal object ChannelJson {
    fun encode(channel: Channel): JSONObject {
        return JSONObject().put("name", channel.name).put("group", channel.group)
            .put("streams", JSONArray(channel.streams)).put("sourceId", channel.sourceId).put("sourceName", channel.sourceName)
    }

    fun decode(json: JSONObject): Channel {
        val urls = json.getJSONArray("streams")
        return Channel(
            name = json.getString("name"),
            group = json.getString("group"),
            streams = (0 until urls.length()).map(transform = { index -> urls.getString(index) }),
            sourceId = json.optString("sourceId"),
            sourceName = json.optString("sourceName")
        )
    }
}
