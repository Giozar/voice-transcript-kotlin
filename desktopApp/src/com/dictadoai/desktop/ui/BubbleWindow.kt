package com.dictadoai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window

/** Ventana flotante de la bolita: sin borde, transparente, siempre encima y sin tomar el foco. */
class BubbleWindow(
    private val sizePx: Int = 56,
    private val onClick: () -> Unit = {},
    private val onMoved: (x: Int, y: Int) -> Unit = { _, _ -> },
) {
    /** Se crea en [create]: ComposeWindow debe construirse en el hilo de la interfaz (EDT). */
    lateinit var window: ComposeWindow
        private set

    fun create(initialLocation: Point? = null) {
        window = ComposeWindow().apply {
            title = TITLE
            isUndecorated = true
            isTransparent = true
            isResizable = false
            isAlwaysOnTop = true
            type = Window.Type.UTILITY // en macOS se crea como NSPanel
            focusableWindowState = false
            isAutoRequestFocus = false
            setSize(sizePx, sizePx)
            location = initialLocation ?: defaultLocation()
            setContent { BubbleContent() }
        }
    }

    fun show() {
        window.isVisible = true
    }

    private fun defaultLocation(): Point {
        val b = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        return Point(b.x + b.width - sizePx - 24, b.y + b.height - sizePx - 24)
    }

    @androidx.compose.runtime.Composable
    private fun BubbleContent() {
        var clicks by remember { mutableStateOf(0) }
        var dragStartPointer by remember { mutableStateOf(Point()) }
        var dragStartWindow by remember { mutableStateOf(Point()) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF3B6FE0))
                .pointerInput(Unit) { detectTapGestures(onTap = { clicks++; onClick() }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragStartPointer = MouseInfo.getPointerInfo().location
                            dragStartWindow = window.location
                        },
                        onDragEnd = { onMoved(window.x, window.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = MouseInfo.getPointerInfo().location
                            window.setLocation(
                                dragStartWindow.x + (p.x - dragStartPointer.x),
                                dragStartWindow.y + (p.y - dragStartPointer.y),
                            )
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(clicks.toString(), color = Color.White)
        }
    }

    companion object {
        const val TITLE = "DictadoAI-Bubble"
    }
}
