package com.example.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.CursorStyle
import com.example.services.TermCell
import com.example.services.TerminalBufferEngine
import com.example.services.TerminalSnapshot
import com.example.ui.theme.CopperLight
import com.example.ui.theme.resolveTerminalFontFamily
import kotlin.math.floor

private const val IME_SENTINEL = " "

@Composable
fun XtermCanvasView(
    engine: TerminalBufferEngine,
    snapshot: TerminalSnapshot,
    scrollbackOffset: Int,
    fontSizeSp: Float,
    fontFamilyName: String,
    cursorStyle: CursorStyle,
    onTypedChar: (Char) -> Unit,
    onSendRaw: (String) -> Unit,
    onPasteText: (String) -> Unit,
    onResizeGrid: (cols: Int, rows: Int) -> Unit,
    onScrollHistoryBy: (deltaLines: Int) -> Unit,
    onJumpToBottom: () -> Unit,
    onClearBuffer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current

    val fontFamily = remember(fontFamilyName) { resolveTerminalFontFamily(fontFamilyName) }
    val normalStyle = remember(fontFamily, fontSizeSp) {
        TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = (fontSizeSp * 1.25f).sp
        )
    }
    val boldStyle = remember(fontFamily, fontSizeSp) {
        normalStyle.copy(fontWeight = FontWeight.Bold)
    }

    val cellMetrics = remember(textMeasurer, normalStyle) {
        val measured = textMeasurer.measure("W", normalStyle)
        val w = measured.size.width.toFloat().coerceAtLeast(6f)
        val h = (measured.size.height.toFloat() * 1.08f).coerceAtLeast(12f)
        w to h
    }
    val cellWidthPx = cellMetrics.first
    val cellHeightPx = cellMetrics.second

    var canvasPixelSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCarryPx by remember { mutableFloatStateOf(0f) }
    var showContextMenu by remember { mutableStateOf(false) }

    var imeValue by remember {
        mutableStateOf(TextFieldValue(text = IME_SENTINEL, selection = TextRange(1)))
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    val padPx = with(density) { 8.dp.toPx() }

    LaunchedEffect(canvasPixelSize, cellWidthPx, cellHeightPx) {
        if (canvasPixelSize.width > 0 && canvasPixelSize.height > 0) {
            val usableW = (canvasPixelSize.width - padPx * 2f).coerceAtLeast(cellWidthPx * 15f)
            val usableH = (canvasPixelSize.height - padPx * 2f).coerceAtLeast(cellHeightPx * 5f)
            val cols = floor(usableW / cellWidthPx).toInt().coerceIn(15, 300)
            val rows = floor(usableH / cellHeightPx).toInt().coerceIn(5, 150)
            onResizeGrid(cols, rows)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(TermCell.DEFAULT_BG_ARGB))
            .onSizeChanged { canvasPixelSize = it }
            .pointerInput(cellHeightPx) {
                detectTapGestures(
                    onTap = {
                        runCatching { focusRequester.requestFocus() }
                        keyboardController?.show()
                    },
                    onLongPress = {
                        showContextMenu = true
                    }
                )
            }
            .pointerInput(cellHeightPx) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragCarryPx += dragAmount.y
                        val lineSteps = (dragCarryPx / cellHeightPx).toInt()
                        if (lineSteps != 0) {
                            onScrollHistoryBy(lineSteps)
                            dragCarryPx -= lineSteps * cellHeightPx
                        }
                    }
                )
            }
            .testTag("xterm_canvas_container")
    ) {
        BasicTextField(
            value = imeValue,
            onValueChange = { updated ->
                val newText = updated.text
                when {
                    newText.isEmpty() -> {
                        onSendRaw("\u007f")
                    }
                    newText.startsWith(IME_SENTINEL) && newText.length > IME_SENTINEL.length -> {
                        val typed = newText.substring(IME_SENTINEL.length)
                        typed.forEach { ch ->
                            if (ch == '\n' || ch == '\r') {
                                onSendRaw("\r")
                            } else {
                                onTypedChar(ch)
                            }
                        }
                    }
                    !newText.startsWith(IME_SENTINEL) -> {
                        newText.forEach { ch ->
                            if (ch == '\n' || ch == '\r') {
                                onSendRaw("\r")
                            } else {
                                onTypedChar(ch)
                            }
                        }
                    }
                }
                imeValue = TextFieldValue(text = IME_SENTINEL, selection = TextRange(1))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.None
            ),
            modifier = Modifier
                .size(1.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onSendRaw("\r")
                            true
                        }
                        Key.Backspace -> {
                            onSendRaw("\u007f")
                            true
                        }
                        Key.Delete -> {
                            onSendRaw("\u001b[3~")
                            true
                        }
                        Key.Tab -> {
                            onSendRaw("\t")
                            true
                        }
                        Key.Escape -> {
                            onSendRaw("\u001b")
                            true
                        }
                        Key.DirectionUp -> {
                            onSendRaw("\u001b[A")
                            true
                        }
                        Key.DirectionDown -> {
                            onSendRaw("\u001b[B")
                            true
                        }
                        Key.DirectionRight -> {
                            onSendRaw("\u001b[C")
                            true
                        }
                        Key.DirectionLeft -> {
                            onSendRaw("\u001b[D")
                            true
                        }
                        Key.MoveHome -> {
                            onSendRaw("\u001b[H")
                            true
                        }
                        Key.MoveEnd -> {
                            onSendRaw("\u001b[F")
                            true
                        }
                        else -> {
                            if (event.isCtrlPressed && event.key == Key.C) {
                                onSendRaw("\u0003")
                                true
                            } else if (event.isCtrlPressed && event.key == Key.D) {
                                onSendRaw("\u0004")
                                true
                            } else if (event.isCtrlPressed && event.key == Key.Z) {
                                onSendRaw("\u001a")
                                true
                            } else if (event.isCtrlPressed && event.key == Key.L) {
                                onSendRaw("\u000c")
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
                .testTag("terminal_hidden_input")
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val revision = snapshot.revision
            if (revision < 0L) return@Canvas

            val totalRows = snapshot.rows
            val totalCols = snapshot.cols
            val scrollCount = snapshot.scrollbackCount
            val clampedOffset = scrollbackOffset.coerceIn(0, scrollCount)

            for (rowIdx in 0 until totalRows) {
                val virtualIdx = (scrollCount - clampedOffset) + rowIdx
                val lineCells: List<TermCell>? = if (virtualIdx < scrollCount) {
                    engine.getScrollbackLine(virtualIdx)
                } else {
                    snapshot.visibleLines.getOrNull(virtualIdx - scrollCount)
                }

                val topY = padPx + rowIdx * cellHeightPx
                if (topY > size.height) break

                if (lineCells != null) {
                    val limit = minOf(totalCols, lineCells.size)
                    for (colIdx in 0 until limit) {
                        val cell = lineCells[colIdx]
                        val leftX = padPx + colIdx * cellWidthPx
                        val effectiveBg = if (cell.inverse) cell.fgArgb else cell.bgArgb
                        val effectiveFg = if (cell.inverse) cell.bgArgb else cell.fgArgb

                        if (effectiveBg != TermCell.DEFAULT_BG_ARGB) {
                            drawRect(
                                color = Color(effectiveBg),
                                topLeft = Offset(leftX, topY),
                                size = Size(cellWidthPx + 0.5f, cellHeightPx + 0.5f)
                            )
                        }

                        if (cell.char != ' ' && cell.char.code >= 32) {
                            val glyphStyle = (if (cell.bold) boldStyle else normalStyle)
                                .copy(color = Color(effectiveFg))
                            drawText(
                                textMeasurer = textMeasurer,
                                text = cell.char.toString(),
                                topLeft = Offset(leftX, topY),
                                style = glyphStyle
                            )
                        }

                        if (cell.underline) {
                            drawLine(
                                color = Color(effectiveFg),
                                start = Offset(leftX, topY + cellHeightPx - 2f),
                                end = Offset(leftX + cellWidthPx, topY + cellHeightPx - 2f),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }
            }

            if (clampedOffset == 0 && snapshot.cursorVisible) {
                val cLeft = padPx + snapshot.cursorCol * cellWidthPx
                val cTop = padPx + snapshot.cursorRow * cellHeightPx
                when (cursorStyle) {
                    CursorStyle.BLOCK -> {
                        drawRect(
                            color = CopperLight.copy(alpha = 0.72f),
                            topLeft = Offset(cLeft, cTop),
                            size = Size(cellWidthPx, cellHeightPx)
                        )
                    }
                    CursorStyle.UNDERLINE -> {
                        drawRect(
                            color = CopperLight,
                            topLeft = Offset(cLeft, cTop + cellHeightPx - 3.dp.toPx()),
                            size = Size(cellWidthPx, 3.dp.toPx())
                        )
                    }
                    CursorStyle.BAR -> {
                        drawRect(
                            color = CopperLight,
                            topLeft = Offset(cLeft, cTop),
                            size = Size(2.5.dp.toPx(), cellHeightPx)
                        )
                    }
                }
            }
        }

        if (scrollbackOffset > 0) {
            FilledTonalButton(
                onClick = onJumpToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .testTag("jump_to_bottom_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text("Live (-$scrollbackOffset lines)")
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy Screen") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    val dump = engine.dumpText(includeScrollback = false)
                    clipboardManager.setText(AnnotatedString(dump))
                }
            )
            DropdownMenuItem(
                text = { Text("Copy Full Scrollback") },
                leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    val dump = engine.dumpText(includeScrollback = true)
                    clipboardManager.setText(AnnotatedString(dump))
                }
            )
            DropdownMenuItem(
                text = { Text("Paste Clipboard") },
                leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrEmpty()) {
                        onPasteText(clip)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Clear Buffer") },
                leadingIcon = { Icon(Icons.Filled.CleaningServices, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    onClearBuffer()
                }
            )
        }
    }
}
