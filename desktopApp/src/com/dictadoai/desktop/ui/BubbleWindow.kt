package com.dictadoai.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dictadoai.core.domain.DictationState
import kotlinx.coroutines.flow.StateFlow
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/** La bolita flotante: su color y animación reflejan el estado del dictado. */
class BubbleWindow(
    private val state: StateFlow<DictationState>,
    private val onClick: () -> Unit,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onBoundsChanged: () -> Unit,
) {
    /** Se crea en [create]: ComposeWindow debe construirse en el hilo de la interfaz (EDT). */
    lateinit var window: ComposeWindow
        private set

    fun create(initialLocation: Point?) {
        window = createFloatingComposeWindow(TITLE, SIZE, SIZE).apply {
            location = initialLocation ?: defaultLocation()
            addComponentListener(object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = onBoundsChanged()
            })
            setContent { BubbleContent() }
        }
    }

    fun show() {
        window.isVisible = true
    }

    private fun defaultLocation(): Point {
        val b = visibleScreenBounds(Point(0, 0))
        return Point(b.x + b.width - SIZE - 24, b.y + b.height - SIZE - 24)
    }

    @Composable
    private fun BubbleContent() {
        val s by state.collectAsState()
        val color = colorFor(s)
        val pulsing = s is DictationState.Recording || s is DictationState.Starting
        val pulse by rememberInfiniteTransition().animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        )
        var dragStartPointer by remember { mutableStateOf(Point()) }
        var dragStartWindow by remember { mutableStateOf(Point()) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
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
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(color = color.copy(alpha = if (pulsing) pulse else 1f), radius = r - 2.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(center.x, center.y))
            }
            if (s is DictationState.Transcribing || s is DictationState.LoadingModel) {
                CircularProgressIndicator(Modifier.fillMaxSize().padding(4.dp), color = Color.White, strokeWidth = 3.dp)
            }
        }
    }

    private fun colorFor(s: DictationState): Color = when (s) {
        DictationState.LoadingModel -> DictadoColors.Loading
        is DictationState.ModelError -> DictadoColors.Error
        DictationState.Idle -> DictadoColors.Idle
        is DictationState.Starting, is DictationState.Recording -> DictadoColors.Recording
        is DictationState.Paused -> DictadoColors.Paused
        is DictationState.Transcribing -> DictadoColors.Transcribing
        is DictationState.Result -> DictadoColors.Result
        is DictationState.NoSpeech, is DictationState.Failure -> DictadoColors.Warning
    }

    companion object {
        const val TITLE = "DictadoAI-Bubble"
        const val SIZE = 56
    }
}
