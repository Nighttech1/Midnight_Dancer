package com.midnightdancer.app.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Editable field that selects the whole value on focus and on every double-tap (C-188).
 *
 * Double-tap is observed on [PointerEventPass.Initial] without consuming — so caret /
 * selection gestures still reach [BasicTextField].
 */
@Composable
fun MdSelectAllTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Brush = SolidColor(androidx.compose.ui.graphics.Color.White),
    singleLine: Boolean = true,
    maxLength: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    selectAllOnOpen: Boolean = false,
    /** When false, focus alone does not select-all (C-241 Elements name). Double-tap still selects. */
    selectAllOnFocus: Boolean = true,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        { inner -> inner() },
) {
    // C-241: do not pre-highlight the whole name on open — cursor at end unless selectAllOnOpen.
    var field by remember(selectAllOnOpen) {
        val sel =
            if (selectAllOnOpen) TextRange(0, value.length) else TextRange(value.length)
        mutableStateOf(TextFieldValue(text = value, selection = sel))
    }
    LaunchedEffect(value) {
        if (value != field.text) {
            val sel =
                if (selectAllOnOpen) TextRange(0, value.length) else TextRange(value.length)
            field = TextFieldValue(text = value, selection = sel)
        }
    }
    val focusRequester = remember { FocusRequester() }
    var selectAllTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectAllOnOpen) {
        if (selectAllOnOpen) {
            focusRequester.requestFocus()
            selectAllTick++
        }
    }
    LaunchedEffect(selectAllTick) {
        if (selectAllTick > 0) {
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    BasicTextField(
        value = field,
        onValueChange = { next ->
            val clipped = next.copy(text = next.text.take(maxLength))
            field = clipped
            onValueChange(clipped.text)
        },
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused && selectAllOnFocus) {
                    selectAllTick++
                }
            }
            .pointerInput(doubleTapTimeout) {
                awaitPointerEventScope {
                    var lastUpUptime = 0L
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.changedToUp()) continue
                        val now = change.uptimeMillis
                        if (lastUpUptime > 0L && now - lastUpUptime <= doubleTapTimeout) {
                            selectAllTick++
                            focusRequester.requestFocus()
                            lastUpUptime = 0L
                        } else {
                            lastUpUptime = now
                        }
                    }
                }
            },
        decorationBox = decorationBox,
    )
}
