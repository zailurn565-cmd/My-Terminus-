package com.example.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class TermCell(
    val char: Char = ' ',
    val fgArgb: Int = DEFAULT_FG_ARGB,
    val bgArgb: Int = DEFAULT_BG_ARGB,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
) {
    companion object {
        const val DEFAULT_FG_ARGB: Int = 0xFFF2E6DC.toInt()
        const val DEFAULT_BG_ARGB: Int = 0xFF0C0A09.toInt()
        val Blank = TermCell()
    }
}

data class TerminalSnapshot(
    val cols: Int = 80,
    val rows: Int = 24,
    val visibleLines: List<List<TermCell>> = emptyList(),
    val scrollbackCount: Int = 0,
    val cursorCol: Int = 0,
    val cursorRow: Int = 0,
    val cursorVisible: Boolean = true,
    val windowTitle: String = "",
    val revision: Long = 0L
)

class TerminalBufferEngine(
    initialCols: Int = 80,
    initialRows: Int = 24,
    private val maxScrollbackLines: Int = 10_000
) {
    private val lock = Any()

    private var cols: Int = initialCols.coerceIn(10, 300)
    private var rows: Int = initialRows.coerceIn(4, 150)

    private var primaryGrid: Array<Array<TermCell>> = allocGrid(cols, rows)
    private var altGrid: Array<Array<TermCell>> = allocGrid(cols, rows)
    private var usingAltBuffer = false

    private val scrollback = ArrayDeque<List<TermCell>>(1024)

    private var cursorCol = 0
    private var cursorRow = 0
    private var savedCol = 0
    private var savedRow = 0
    private var altSavedCol = 0
    private var altSavedRow = 0
    private var cursorVisible = true
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var wrapPending = false
    private var windowTitle = ""

    private var curFg = TermCell.DEFAULT_FG_ARGB
    private var curBg = TermCell.DEFAULT_BG_ARGB
    private var curBold = false
    private var curUnderline = false
    private var curInverse = false

    private enum class ParseState { GROUND, ESC, CSI, OSC, CHARSET }
    private var parseState = ParseState.GROUND
    private val csiBuf = StringBuilder(64)
    private val oscBuf = StringBuilder(128)

    private val utf8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pendingBytes = ByteArray(0)

    private var revisionCounter = 0L
    private val _snapshot = MutableStateFlow(buildSnapshotLocked())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    fun feedBytes(bytes: ByteArray, length: Int = bytes.size) {
        if (length <= 0) return
        synchronized(lock) {
            val combined = if (pendingBytes.isEmpty()) {
                ByteBuffer.wrap(bytes, 0, length)
            } else {
                val merged = ByteArray(pendingBytes.size + length)
                System.arraycopy(pendingBytes, 0, merged, 0, pendingBytes.size)
                System.arraycopy(bytes, 0, merged, pendingBytes.size, length)
                ByteBuffer.wrap(merged)
            }
            val charBuffer = CharBuffer.allocate(combined.remaining() * 2 + 8)
            utf8Decoder.decode(combined, charBuffer, false)
            pendingBytes = if (combined.hasRemaining()) {
                ByteArray(combined.remaining()).also { combined.get(it) }
            } else {
                ByteArray(0)
            }
            charBuffer.flip()
            for (i in 0 until charBuffer.limit()) {
                processCharLocked(charBuffer.get(i))
            }
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun feedLocalText(text: String) {
        val raw = text.toByteArray(Charsets.UTF_8)
        feedBytes(raw, raw.size)
    }

    fun resize(newCols: Int, newRows: Int): Boolean {
        val targetCols = newCols.coerceIn(15, 300)
        val targetRows = newRows.coerceIn(5, 150)
        synchronized(lock) {
            if (targetCols == cols && targetRows == rows) return false
            primaryGrid = resizeGrid(primaryGrid, targetCols, targetRows)
            altGrid = resizeGrid(altGrid, targetCols, targetRows)
            cols = targetCols
            rows = targetRows
            scrollTop = 0
            scrollBottom = rows - 1
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            cursorRow = cursorRow.coerceIn(0, rows - 1)
            wrapPending = false
            _snapshot.value = buildSnapshotLocked()
            return true
        }
    }

    fun clearAll() {
        synchronized(lock) {
            scrollback.clear()
            primaryGrid = allocGrid(cols, rows)
            altGrid = allocGrid(cols, rows)
            cursorCol = 0
            cursorRow = 0
            wrapPending = false
            resetAttributesLocked()
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun getScrollbackLine(index: Int): List<TermCell>? = synchronized(lock) {
        if (index in 0 until scrollback.size) scrollback[index] else null
    }

    fun dumpText(includeScrollback: Boolean): String = synchronized(lock) {
        val sb = StringBuilder()
        if (includeScrollback && !usingAltBuffer) {
            for (line in scrollback) {
                sb.append(cellsToTrimmedString(line)).append('\n')
            }
        }
        val active = activeGridLocked()
        for (r in 0 until rows) {
            sb.append(cellsToTrimmedString(active[r].toList()))
            if (r < rows - 1) sb.append('\n')
        }
        sb.toString().trimEnd()
    }

    private fun activeGridLocked(): Array<Array<TermCell>> =
        if (usingAltBuffer) altGrid else primaryGrid

    private fun processCharLocked(ch: Char) {
        when (parseState) {
            ParseState.GROUND -> when (ch) {
                '\u001b' -> parseState = ParseState.ESC
                '\r' -> {
                    cursorCol = 0
                    wrapPending = false
                }
                '\n', '\u000b', '\u000c' -> lineFeedLocked()
                '\b' -> {
                    wrapPending = false
                    if (cursorCol > 0) cursorCol--
                }
                '\t' -> {
                    wrapPending = false
                    val nextTab = ((cursorCol / 8) + 1) * 8
                    cursorCol = nextTab.coerceAtMost(cols - 1)
                }
                '\u0007' -> Unit
                else -> if (ch.code >= 32) putGraphicCharLocked(ch)
            }

            ParseState.ESC -> when (ch) {
                '[' -> {
                    csiBuf.clear()
                    parseState = ParseState.CSI
                }
                ']' -> {
                    oscBuf.clear()
                    parseState = ParseState.OSC
                }
                '(', ')', '*', '+' -> parseState = ParseState.CHARSET
                '7' -> {
                    savedCol = cursorCol
                    savedRow = cursorRow
                    parseState = ParseState.GROUND
                }
                '8' -> {
                    cursorCol = savedCol.coerceIn(0, cols - 1)
                    cursorRow = savedRow.coerceIn(0, rows - 1)
                    wrapPending = false
                    parseState = ParseState.GROUND
                }
                'D' -> {
                    lineFeedLocked()
                    parseState = ParseState.GROUND
                }
                'M' -> {
                    reverseIndexLocked()
                    parseState = ParseState.GROUND
                }
                'c' -> {
                    primaryGrid = allocGrid(cols, rows)
                    cursorCol = 0
                    cursorRow = 0
                    scrollTop = 0
                    scrollBottom = rows - 1
                    resetAttributesLocked()
                    parseState = ParseState.GROUND
                }
                else -> parseState = ParseState.GROUND
            }

            ParseState.CHARSET -> parseState = ParseState.GROUND

            ParseState.OSC -> {
                if (ch == '\u0007') {
                    applyOscLocked(oscBuf.toString())
                    parseState = ParseState.GROUND
                } else if (ch == '\\' && oscBuf.endsWith('\u001b')) {
                    oscBuf.setLength(oscBuf.length - 1)
                    applyOscLocked(oscBuf.toString())
                    parseState = ParseState.GROUND
                } else if (oscBuf.length < 512) {
                    oscBuf.append(ch)
                } else {
                    parseState = ParseState.GROUND
                }
            }

            ParseState.CSI -> {
                if (ch.code in 0x40..0x7E) {
                    handleCsiLocked(csiBuf.toString(), ch)
                    parseState = ParseState.GROUND
                } else if (csiBuf.length < 128) {
                    csiBuf.append(ch)
                } else {
                    parseState = ParseState.GROUND
                }
            }
        }
    }

    private fun putGraphicCharLocked(ch: Char) {
        if (wrapPending) {
            cursorCol = 0
            lineFeedLocked()
            wrapPending = false
        }
        val grid = activeGridLocked()
        val cell = TermCell(
            char = ch,
            fgArgb = curFg,
            bgArgb = curBg,
            bold = curBold,
            underline = curUnderline,
            inverse = curInverse
        )
        grid[cursorRow][cursorCol] = cell
        if (cursorCol >= cols - 1) {
            wrapPending = true
        } else {
            cursorCol++
        }
    }

    private fun lineFeedLocked() {
        wrapPending = false
        if (cursorRow == scrollBottom) {
            scrollUpLocked(scrollTop, scrollBottom, 1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndexLocked() {
        wrapPending = false
        if (cursorRow == scrollTop) {
            scrollDownLocked(scrollTop, scrollBottom, 1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun scrollUpLocked(top: Int, bottom: Int, count: Int) {
        val grid = activeGridLocked()
        val steps = count.coerceIn(1, bottom - top + 1)
        repeat(steps) {
            if (!usingAltBuffer && top == 0) {
                if (scrollback.size >= maxScrollbackLines) {
                    scrollback.removeFirst()
                }
                scrollback.addLast(grid[0].toList())
            }
            for (r in top until bottom) {
                grid[r] = grid[r + 1]
            }
            grid[bottom] = blankRow(cols)
        }
    }

    private fun scrollDownLocked(top: Int, bottom: Int, count: Int) {
        val grid = activeGridLocked()
        val steps = count.coerceIn(1, bottom - top + 1)
        repeat(steps) {
            for (r in bottom downTo top + 1) {
                grid[r] = grid[r - 1]
            }
            grid[top] = blankRow(cols)
        }
    }

    private fun applyOscLocked(osc: String) {
        val semi = osc.indexOf(';')
        if (semi > 0) {
            val code = osc.substring(0, semi)
            if (code == "0" || code == "2") {
                windowTitle = osc.substring(semi + 1).take(80)
            }
        }
    }

    private fun handleCsiLocked(rawParams: String, finalChar: Char) {
        val privateMode = rawParams.startsWith('?')
        val clean = if (privateMode) rawParams.drop(1) else rawParams
        val params = if (clean.isEmpty()) {
            emptyList()
        } else {
            clean.split(';').map { it.toIntOrNull() ?: 0 }
        }
        fun p(idx: Int, default: Int): Int =
            params.getOrNull(idx)?.takeIf { it > 0 } ?: default

        if (privateMode) {
            handlePrivateDecModeLocked(params, finalChar)
            return
        }

        val grid = activeGridLocked()
        when (finalChar) {
            'A' -> {
                wrapPending = false
                cursorRow = (cursorRow - p(0, 1)).coerceAtLeast(0)
            }
            'B' -> {
                wrapPending = false
                cursorRow = (cursorRow + p(0, 1)).coerceAtMost(rows - 1)
            }
            'C' -> {
                wrapPending = false
                cursorCol = (cursorCol + p(0, 1)).coerceAtMost(cols - 1)
            }
            'D' -> {
                wrapPending = false
                cursorCol = (cursorCol - p(0, 1)).coerceAtLeast(0)
            }
            'E' -> {
                wrapPending = false
                cursorCol = 0
                cursorRow = (cursorRow + p(0, 1)).coerceAtMost(rows - 1)
            }
            'F' -> {
                wrapPending = false
                cursorCol = 0
                cursorRow = (cursorRow - p(0, 1)).coerceAtLeast(0)
            }
            'G' -> {
                wrapPending = false
                cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1)
            }
            'd' -> {
                wrapPending = false
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
            }
            'H', 'f' -> {
                wrapPending = false
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (p(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'J' -> {
                val mode = params.firstOrNull() ?: 0
                val blank = currentBlankCellLocked()
                when (mode) {
                    0 -> {
                        for (c in cursorCol until cols) grid[cursorRow][c] = blank
                        for (r in (cursorRow + 1) until rows) {
                            grid[r] = Array(cols) { blank }
                        }
                    }
                    1 -> {
                        for (r in 0 until cursorRow) grid[r] = Array(cols) { blank }
                        for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = blank
                    }
                    2, 3 -> {
                        for (r in 0 until rows) grid[r] = Array(cols) { blank }
                        if (mode == 3 && !usingAltBuffer) scrollback.clear()
                    }
                }
            }
            'K' -> {
                val mode = params.firstOrNull() ?: 0
                val blank = currentBlankCellLocked()
                when (mode) {
                    0 -> for (c in cursorCol until cols) grid[cursorRow][c] = blank
                    1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = blank
                    2 -> grid[cursorRow] = Array(cols) { blank }
                }
            }
            'L' -> scrollDownLocked(cursorRow.coerceAtLeast(scrollTop), scrollBottom, p(0, 1))
            'M' -> scrollUpLocked(cursorRow.coerceAtLeast(scrollTop), scrollBottom, p(0, 1))
            'P' -> {
                val count = p(0, 1).coerceAtMost(cols - cursorCol)
                val blank = currentBlankCellLocked()
                for (c in cursorCol until (cols - count)) {
                    grid[cursorRow][c] = grid[cursorRow][c + count]
                }
                for (c in (cols - count) until cols) {
                    grid[cursorRow][c] = blank
                }
            }
            '@' -> {
                val count = p(0, 1).coerceAtMost(cols - cursorCol)
                val blank = currentBlankCellLocked()
                for (c in (cols - 1) downTo (cursorCol + count)) {
                    grid[cursorRow][c] = grid[cursorRow][c - count]
                }
                for (c in cursorCol until (cursorCol + count)) {
                    grid[cursorRow][c] = blank
                }
            }
            'X' -> {
                val count = p(0, 1).coerceAtMost(cols - cursorCol)
                val blank = currentBlankCellLocked()
                for (c in cursorCol until (cursorCol + count)) {
                    grid[cursorRow][c] = blank
                }
            }
            'S' -> scrollUpLocked(scrollTop, scrollBottom, p(0, 1))
            'T' -> scrollDownLocked(scrollTop, scrollBottom, p(0, 1))
            'r' -> {
                val top = (p(0, 1) - 1).coerceIn(0, rows - 1)
                val bot = (p(1, rows) - 1).coerceIn(top, rows - 1)
                scrollTop = top
                scrollBottom = bot
                cursorCol = 0
                cursorRow = 0
                wrapPending = false
            }
            's' -> {
                savedCol = cursorCol
                savedRow = cursorRow
            }
            'u' -> {
                cursorCol = savedCol.coerceIn(0, cols - 1)
                cursorRow = savedRow.coerceIn(0, rows - 1)
                wrapPending = false
            }
            'm' -> applySgrLocked(if (params.isEmpty()) listOf(0) else params)
        }
    }

    private fun handlePrivateDecModeLocked(params: List<Int>, finalChar: Char) {
        val enable = finalChar == 'h'
        val disable = finalChar == 'l'
        if (!enable && !disable) return
        for (code in params) {
            when (code) {
                25 -> cursorVisible = enable
                47, 1047, 1049 -> {
                    if (enable && !usingAltBuffer) {
                        altSavedCol = cursorCol
                        altSavedRow = cursorRow
                        altGrid = allocGrid(cols, rows)
                        usingAltBuffer = true
                        cursorCol = 0
                        cursorRow = 0
                        scrollTop = 0
                        scrollBottom = rows - 1
                        wrapPending = false
                    } else if (disable && usingAltBuffer) {
                        usingAltBuffer = false
                        cursorCol = altSavedCol.coerceIn(0, cols - 1)
                        cursorRow = altSavedRow.coerceIn(0, rows - 1)
                        scrollTop = 0
                        scrollBottom = rows - 1
                        wrapPending = false
                    }
                }
            }
        }
    }

    private fun applySgrLocked(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> resetAttributesLocked()
                1 -> curBold = true
                22 -> curBold = false
                4 -> curUnderline = true
                24 -> curUnderline = false
                7 -> curInverse = true
                27 -> curInverse = false
                39 -> curFg = TermCell.DEFAULT_FG_ARGB
                49 -> curBg = TermCell.DEFAULT_BG_ARGB
                in 30..37 -> curFg = xterm256ToArgb((code - 30) + if (curBold) 8 else 0)
                in 90..97 -> curFg = xterm256ToArgb((code - 90) + 8)
                in 40..47 -> curBg = xterm256ToArgb(code - 40)
                in 100..107 -> curBg = xterm256ToArgb((code - 100) + 8)
                38, 48 -> {
                    val isFg = code == 38
                    val mode = params.getOrNull(i + 1) ?: 0
                    if (mode == 5) {
                        val colorIdx = (params.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                        val argb = xterm256ToArgb(colorIdx)
                        if (isFg) curFg = argb else curBg = argb
                        i += 2
                    } else if (mode == 2) {
                        val r = (params.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                        val g = (params.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                        val b = (params.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
                        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        if (isFg) curFg = argb else curBg = argb
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun resetAttributesLocked() {
        curFg = TermCell.DEFAULT_FG_ARGB
        curBg = TermCell.DEFAULT_BG_ARGB
        curBold = false
        curUnderline = false
        curInverse = false
    }

    private fun currentBlankCellLocked(): TermCell = TermCell(
        char = ' ',
        fgArgb = curFg,
        bgArgb = curBg
    )

    private fun buildSnapshotLocked(): TerminalSnapshot {
        revisionCounter++
        val grid = activeGridLocked()
        val lines = ArrayList<List<TermCell>>(rows)
        for (r in 0 until rows) {
            lines.add(grid[r].toList())
        }
        return TerminalSnapshot(
            cols = cols,
            rows = rows,
            visibleLines = lines,
            scrollbackCount = if (usingAltBuffer) 0 else scrollback.size,
            cursorCol = cursorCol.coerceIn(0, cols - 1),
            cursorRow = cursorRow.coerceIn(0, rows - 1),
            cursorVisible = cursorVisible,
            windowTitle = windowTitle,
            revision = revisionCounter
        )
    }

    companion object {
        private val ANSI_16 = intArrayOf(
            0xFF1A1715.toInt(), // 0 Black
            0xFFEF4444.toInt(), // 1 Red
            0xFF22C55E.toInt(), // 2 Green
            0xFFEAB308.toInt(), // 3 Yellow
            0xFF3B82F6.toInt(), // 4 Blue
            0xFFA855F7.toInt(), // 5 Magenta
            0xFF06B6D4.toInt(), // 6 Cyan
            0xFFD6CEC6.toInt(), // 7 White
            0xFF6B5E54.toInt(), // 8 Bright Black
            0xFFF87171.toInt(), // 9 Bright Red
            0xFF4ADE80.toInt(), // 10 Bright Green
            0xFFFACC15.toInt(), // 11 Bright Yellow
            0xFF60A5FA.toInt(), // 12 Bright Blue
            0xFFC084FC.toInt(), // 13 Bright Magenta
            0xFF22D3EE.toInt(), // 14 Bright Cyan
            0xFFFAF5F0.toInt()  // 15 Bright White
        )

        private val CUBE_STEPS = intArrayOf(0, 95, 135, 175, 215, 255)

        fun xterm256ToArgb(index: Int): Int {
            val idx = index.coerceIn(0, 255)
            if (idx < 16) return ANSI_16[idx]
            if (idx < 232) {
                val cube = idx - 16
                val r = CUBE_STEPS[(cube / 36) % 6]
                val g = CUBE_STEPS[(cube / 6) % 6]
                val b = CUBE_STEPS[cube % 6]
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val gray = 8 + (idx - 232) * 10
            return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        private fun blankRow(cols: Int): Array<TermCell> = Array(cols) { TermCell.Blank }

        private fun allocGrid(cols: Int, rows: Int): Array<Array<TermCell>> =
            Array(rows) { blankRow(cols) }

        private fun resizeGrid(
            old: Array<Array<TermCell>>,
            newCols: Int,
            newRows: Int
        ): Array<Array<TermCell>> {
            return Array(newRows) { r ->
                Array(newCols) { c ->
                    old.getOrNull(r)?.getOrNull(c) ?: TermCell.Blank
                }
            }
        }

        private fun cellsToTrimmedString(cells: List<TermCell>): String {
            val chars = CharArray(cells.size) { cells[it].char }
            return String(chars).trimEnd()
        }
    }
}
