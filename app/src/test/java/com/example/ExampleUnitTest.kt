package com.example

import com.example.services.SftpService
import com.example.services.TerminalBufferEngine
import com.example.utils.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun terminalEngine_handlesTrueColorAndScrollbackLimit() {
        val engine = TerminalBufferEngine(initialCols = 40, initialRows = 5, maxScrollbackLines = 100)
        // TrueColor foreground RGB(184, 115, 51) -> 0xFFB87333
        engine.feedLocalText("\u001b[38;2;184;115;51mForge\u001b[0m")
        val snap = engine.snapshot.value
        assertEquals('F', snap.visibleLines[0][0].char)
        assertEquals(0xFFB87333.toInt(), snap.visibleLines[0][0].fgArgb)

        // Overflow scrollback beyond 100 lines
        repeat(130) { idx ->
            engine.feedLocalText("\r\nLine-$idx")
        }
        assertEquals(100, engine.snapshot.value.scrollbackCount)
    }

    @Test
    fun terminalEngine_toleratesMalformedUtf8Sequences() {
        val engine = TerminalBufferEngine(initialCols = 20, initialRows = 4)
        val malformed = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'O'.code.toByte(), 'K'.code.toByte())
        engine.feedBytes(malformed)
        val text = engine.dumpText(includeScrollback = false)
        assertTrue(text.contains("OK"))
    }

    @Test
    fun validators_validateHostPortAndPem() {
        assertNull(Validators.validateHost("192.168.1.42"))
        assertNull(Validators.validateHost("vps.forgeshell.io"))
        assertNotNull(Validators.validateHost("invalid host spaces"))
        assertNull(Validators.validatePort("22"))
        assertNotNull(Validators.validatePort("70000"))
        assertNull(
            Validators.validatePrivateKeyPem(
                "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"
            )
        )
    }

    @Test
    fun sftpPathHelpers_normalizeAndTraverseCleanly() {
        assertEquals("/var/log", SftpService.joinRemotePath("/var/", "log"))
        assertEquals("/var", SftpService.parentRemotePath("/var/log"))
        assertEquals("/", SftpService.parentRemotePath("/var"))
    }
}
