package cn.hdfk7.android.iptv.core.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChannelFocusMemoryTests {
    private fun channel(name: String, source: String = "a"): Channel = Channel(name, "", listOf("https://example.org/$name"), source)

    @Test fun restoresIdentityAfterProgramOrderChanges() {
        val first = channel("One")
        val second = channel("Two")
        val memory = ChannelFocusMemory()
        memory.remember("a", second.id)
        assertEquals(second, memory.target("a", listOf(first, second), first.id))
        assertEquals(second, memory.target("a", listOf(second, first), first.id))
    }

    @Test fun keepsSourceFocusSeparate() {
        val memory = ChannelFocusMemory()
        val first = channel("One")
        val second = channel("Two")
        val other = channel("Other", "b")
        memory.remember("a", second.id)
        memory.remember("b", other.id)
        assertEquals(second, memory.target("a", listOf(first, second), first.id))
        assertEquals(other, memory.target("b", listOf(other), null))
    }

    @Test fun removedProgramFallsBackToSelectionThenFirstAndEmpty() {
        val memory = ChannelFocusMemory()
        memory.remember("a", channel("Removed").id)
        val first = channel("One")
        val second = channel("Two")
        assertEquals(second, memory.target("a", listOf(first, second), second.id))
        assertEquals(first, memory.target("a", listOf(first, second), "missing"))
        assertNull(memory.target("a", emptyList(), null))
    }
}
