package io.github.ftatc.tinkertaphome.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNamesTest {

    @Test
    fun `keeps an ordinary name unchanged`() {
        assertEquals("Lampa i köket", DeviceNames.clean("Lampa i köket"))
    }

    @Test
    fun `removes control characters`() {
        assertEquals("Lampa", DeviceNames.clean("La\u0000m\u0007pa"))
    }

    @Test
    fun `turns line breaks and tabs into single spaces`() {
        assertEquals("Lampa i köket", DeviceNames.clean("Lampa\n\ti   köket"))
    }

    @Test
    fun `removes characters that reverse the text direction`() {
        // U+202E vänder textriktningen, så att "Lampa\u202Eexe.gnp" visas som "Lampapng.exe".
        assertEquals("Lampaexe.gnp", DeviceNames.clean("Lampa\u202Eexe.gnp"))
    }

    @Test
    fun `removes zero width characters`() {
        assertEquals("Lampa", DeviceNames.clean("La\u200Bmp\uFEFFa"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("Lampa", DeviceNames.clean("  Lampa \n"))
    }

    @Test
    fun `keeps emoji`() {
        assertEquals("Lampa 💡", DeviceNames.clean("Lampa 💡"))
    }

    @Test
    fun `a name of only hidden characters becomes empty`() {
        assertEquals("", DeviceNames.clean("\u202E\u200B\u0000 \n"))
    }

    @Test
    fun `length counts an emoji as one character`() {
        assertEquals(1, DeviceNames.length("💡"))
    }

    @Test
    fun `truncate keeps names within the limit unchanged`() {
        val name = "a".repeat(DeviceNames.MAX_LENGTH)

        assertEquals(name, DeviceNames.truncate(name))
    }

    @Test
    fun `truncate shortens long names to the limit`() {
        assertEquals("a".repeat(DeviceNames.MAX_LENGTH), DeviceNames.truncate("a".repeat(100)))
    }

    @Test
    fun `truncate never splits an emoji`() {
        val name = "💡".repeat(DeviceNames.MAX_LENGTH + 5)

        val truncated = DeviceNames.truncate(name)

        assertEquals(DeviceNames.MAX_LENGTH, DeviceNames.length(truncated))
        assertEquals("💡".repeat(DeviceNames.MAX_LENGTH), truncated)
    }

    @Test
    fun `truncate does not leave a trailing space`() {
        val name = "a".repeat(DeviceNames.MAX_LENGTH - 1) + " b"

        assertEquals("a".repeat(DeviceNames.MAX_LENGTH - 1), DeviceNames.truncate(name))
    }
}
