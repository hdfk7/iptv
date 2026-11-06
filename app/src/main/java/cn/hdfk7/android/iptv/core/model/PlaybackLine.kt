package cn.hdfk7.android.iptv.core.model

data class LineQuality(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val startupMs: Long = 0,
    val playedMs: Long = 0,
    val stalledMs: Long = 0,
    val renderedFrames: Int = 0,
    val droppedFrames: Int = 0,
    val failures: Int = 0
)

data class PlaybackLine(val url: String, val sort: Int, val quality: LineQuality? = null)
