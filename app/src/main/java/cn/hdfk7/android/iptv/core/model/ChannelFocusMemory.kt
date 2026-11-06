package cn.hdfk7.android.iptv.core.model

/** Remember program identity rather than its changing visual row position. */
class ChannelFocusMemory {
    private val focusedBySource = mutableMapOf<String, String>()

    fun remember(sourceId: String, channelId: String) {
        focusedBySource[sourceId] = channelId
    }

    fun target(sourceId: String, channels: List<Channel>, selectedId: String?): Channel? {
        return channels.firstOrNull(predicate = { channel -> channel.id == focusedBySource[sourceId] })
            ?: channels.firstOrNull(predicate = { channel -> channel.id == selectedId })
            ?: channels.firstOrNull()
    }
}
