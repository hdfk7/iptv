package cn.hdfk7.android.iptv.core.data.network

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistParserTests {
    private val parser = PlaylistParser()
    private val base = "https://example.org/catalog/list.m3u8"

    @Test
    fun acceptsRepositoryHeaderAttributesAndMergesProgramsAcrossGroups() {
        val text = "#EXTM3U x-tvg-url=\"https://example.org/epg.xml\"\n#EXTINF:-1 group-title=\"A\",CCTV1\nhttps://example.org/a\n#EXTINF:-1 group-title=\"B\",CCTV-1综合HD\nhttps://example.org/b"
        val channels = parser.parse(text, base)
        assertEquals(1, channels.size)
        assertEquals(2, channels.single().streams.size)
    }

    @Test
    fun mergesAlternativeLinesAndRemovesExactDuplicates() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News" group-title="TV",News
            https://example.org/a.m3u8?token=x,y
            #EXTINF:-1 group-title="TV",News
            ../b.m3u8
            #EXTINF:-1 group-title="TV",News
            ../b.m3u8
        """.trimIndent()
        val channels = parser.parse(text, base)
        assertEquals(1, channels.size)
        assertEquals(listOf("https://example.org/a.m3u8?token=x,y", "https://example.org/b.m3u8"), channels[0].streams)
        assertEquals("TV", channels[0].group)
    }

    @Test
    fun acceptsBomCrLfQuotedCommaAndDisplayNameComma() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"News, live\",Channel, HD\r\nhttps://example.org/live\r\n"
        val channel = parser.parse(text, base).single()
        assertEquals("News, live", channel.group)
        assertEquals("Channel, HD", channel.name)
    }

    @Test
    fun ignoresCommentsOrphansUnsafeSchemesAndMissingUrls() {
        val text = """
            #EXTM3U
            https://example.org/orphan
            #EXTINF:-1,Invalid
            file:///etc/passwd
            #EXTINF:-1,Credential
            https://user:password@example.org/live
            #EXTINF:-1,Missing
            #EXTINF:-1,Valid
            # comment
            https://example.org/live
            #EXTINF:-1,Dangling
        """.trimIndent()
        assertEquals(listOf("Valid"), parser.parse(text, base).map(transform = { channel -> channel.name }))
    }

    @Test
    fun rejectsHtmlAndHlsManifestsInsteadOfTreatingSegmentsAsChannels() {
        assertThrows(IllegalArgumentException::class.java, { parser.parse("<html>error</html>", base) })
        assertThrows(IllegalArgumentException::class.java, { parser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,segment\na.ts", base) })
    }

    @Test
    fun mergesSameProgramAcrossDifferentGroups() {
        val channels = parser.parse("#EXTM3U\n#EXTINF:-1 group-title=\"A\",News\na.m3u8\n#EXTINF:-1 group-title=\"B\",News\nb.m3u8", base)
        assertEquals(1, channels.size)
        assertEquals(2, channels.single().streams.size)
    }
}
