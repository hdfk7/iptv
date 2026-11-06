package cn.hdfk7.android.iptv.core.model

internal enum class ChannelCategory {
    CCTV, SATELLITE, GREATER_CHINA, SPORTS, OTHER;

    companion object {
        private val cctv = Regex("^CCTV[-_]?(?:[0-9]{1,2}[+]?|[48]K)$")
        private val satellite = Regex("^(?:北京|东方|上海|天津|重庆|河北|山西|内蒙古|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|东南|江西|山东|河南|湖北|湖南|广东|深圳|广西|海南|四川|贵州|云南|西藏|陕西|甘肃|青海|宁夏|新疆|兵团|三沙|厦门|延边)卫视$")
        private val groupAliases = mapOf(
            "央视" to CCTV,
            "央视台" to CCTV,
            "央视频道" to CCTV,
            "央视高清" to CCTV,
            "咪咕央视" to CCTV,
            "CCTV" to CCTV,
            "卫视" to SATELLITE,
            "卫视频道" to SATELLITE,
            "港澳台" to GREATER_CHINA,
            "港澳台频道" to GREATER_CHINA,
            "体育" to SPORTS,
            "体育频道" to SPORTS,
            "咪咕赛事" to SPORTS,
            "SPORTS" to SPORTS
        )

        fun classify(channel: Channel): ChannelCategory {
            return when {
                cctv.matches(channel.key) -> CCTV
                satellite.matches(channel.key) -> SATELLITE
                else -> groupAliases[ChannelNames.key(channel.group)] ?: OTHER
            }
        }
    }
}
