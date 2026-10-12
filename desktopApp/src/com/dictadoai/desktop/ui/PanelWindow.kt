package com.dictadoai.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dictadoai.core.application.DictationController
import com.dictadoai.core.domain.DictationState
import com.dictadoai.core.domain.FailureKind
import com.dictadoai.core.port.ClipboardWriter
import com.dictadoai.desktop.adapter.macos.MacosBridge
import com.dictadoai.desktop.adapter.macos.configureFloating
import java.awt.Point

/**
 * Panel junto a la bolita: controles durante la grabación y texto editable al terminar.
 * Normalmente no toma el foco; solo lo toma mientras la persona edita el texto (botón "Editar").
 */
class PanelWindow(
    private val controller: DictationController,
    private val clipboard: ClipboardWriter,
    private val mac: MacosBridge,
    private val bubble: BubbleWindow,
) {
    lateinit var window: ComposeWindow
        private set

    private var editing by mutableStateOf(false)
    private var editedText by mutableStateOf("")
    private var previous: DictationState = DictationState.LoadingModel
    private var currentHeight = 0

    fun create() {
        window = createFloatingComposeWindow(TITLE, WIDTH, 130).apply { setContent { PanelContent() } }
    }

    /** Muestra, oculta y redimensiona el panel según el estado. */
    fun update(state: DictationState) {
        if (state !is DictationState.Result) leaveEditing()
        if (state is DictationState.Result && previous !is DictationState.Result) editedText = state.text
        previous = state

        val height = heightFor(state)
        if (height == null) {
            window.isVisible = false
            return
        }
        currentHeight = height
        window.setSize(WIDTH, height)
        reposition()
        if (!window.isVisible) {
            window.isVisible = true
            mac.configureFloating(TITLE)
        }
    }

    /** Coloca el panel junto a la bolita, hacia el lado con más espacio en pantalla. */
    fun reposition() {
        if (!::window.isInitialized || currentHeight == 0) return
        val b = bubble.window.bounds
        val screen = visibleScreenBounds(Point(b.centerX.toInt(), b.centerY.toInt()))
        val gap = 10
        val x = if (b.centerX > screen.centerX) b.x - gap - WIDTH else b.x + b.width + gap
        val y = if (b.centerY > screen.centerY) b.y + b.height - currentHeight else b.y
        window.setLocation(
            x.coerceIn(screen.x, screen.x + screen.width - WIDTH),
            y.coerceIn(screen.y, screen.y + screen.height - currentHeight),
        )
    }

    private fun heightFor(s: DictationState): Int? = when (s) {
        DictationState.Idle, DictationState.LoadingModel -> null
        is DictationState.ModelError -> 130
        DictationState.Starting, DictationState.Recording, DictationState.Paused -> 130
        is DictationState.Transcribing -> 100
        is DictationState.Result -> 290
        is DictationState.NoSpeech -> 130
        is DictationState.Failure -> if (s.kind == FailureKind.MIC_DENIED) 190 else 130
    }

    private fun enterEditing() {
        editing = true
        window.focusableWindowState = true
        mac.dictado_macos_activate_app()
        window.toFront()
        window.requestFocus()
    }

    private fun leaveEditing() {
        if (!editing) return
        editing = false
        window.focusableWindowState = false
        mac.dictado_macos_restore_focus() // devuelve el foco a la app donde se va a pegar
    }

    @Composable
    private fun PanelContent() {
        val state by controller.state.collectAsState()
        val elapsed by controller.elapsedMillis.collectAsState()
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DictadoColors.Panel,
                contentColor = DictadoColors.PanelText,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val s = state) {
                        DictationState.Idle, DictationState.LoadingModel -> Unit
                        is DictationState.ModelError -> Message("No se pudo cargar el modelo de voz", s.message) {
                            TextButton(onClick = { controller.close() }) { Text("Cerrar") }
                        }
                        DictationState.Starting -> Title("Preparando micrófono…")
                        DictationState.Recording -> Controls("Grabando", elapsed, paused = false)
                        DictationState.Paused -> Controls("En pausa", elapsed, paused = true)
                        is DictationState.Transcribing -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 3.dp)
                            Title("Transcribiendo…")
                        }
                        is DictationState.Result -> ResultView(s)
                        is DictationState.NoSpeech -> Message(
                            "No se detectó voz",
                            if (s.limitReached) LIMIT_TEXT else "Comprueba el volumen de entrada del micrófono.",
                        ) { TextButton(onClick = { controller.close() }) { Text("Cerrar") } }
                        is DictationState.Failure -> FailureView(s.kind)
                    }
                }
            }
        }
    }

    @Composable
    private fun Title(text: String) = Text(text, fontSize = 15.sp, color = DictadoColors.PanelText)

    @Composable
    private fun Message(title: String, detail: String, actions: @Composable () -> Unit) {
        Title(title)
        Text(detail, fontSize = 12.sp, color = DictadoColors.PanelText.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }

    @Composable
    private fun Controls(label: String, elapsedMillis: Long, paused: Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title(label)
            Text(formatTime(elapsedMillis), fontSize = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (paused) controller.resume() else controller.pause() }) {
                Text(if (paused) "Reanudar" else "Pausar")
            }
            Button(onClick = { controller.finish() }) { Text("Terminar") }
            TextButton(onClick = { controller.cancel() }) { Text("Cancelar") }
        }
    }

    @Composable
    private fun ResultView(s: DictationState.Result) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(editing) { if (editing) focus.requestFocus() }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (s.limitReached) LIMIT_TEXT else "Texto copiado al portapapeles",
                fontSize = 12.sp, color = DictadoColors.PanelText.copy(alpha = 0.7f),
            )
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                readOnly = !editing,
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.copy(editedText) }) { Text("Copiar") }
                OutlinedButton(onClick = { if (editing) leaveEditing() else enterEditing() }) {
                    Text(if (editing) "Listo" else "Editar")
                }
                TextButton(onClick = { controller.close() }) { Text("Cerrar") }
            }
        }
    }

    @Composable
    private fun FailureView(kind: FailureKind) {
        when (kind) {
            FailureKind.MIC_DENIED -> Message(
                "Sin permiso de micrófono",
                "Actívalo en Ajustes del Sistema > Privacidad y seguridad > Micrófono.",
            ) {
                Button(onClick = { openMicrophoneSettings() }) { Text("Abrir ajustes") }
                TextButton(onClick = { controller.close() }) { Text("Cerrar") }
            }
            FailureKind.MIC_UNAVAILABLE -> Message("No se encontró un micrófono", "Conecta uno y vuelve a intentarlo.") {
                TextButton(onClick = { controller.close() }) { Text("Cerrar") }
            }
            FailureKind.TRANSCRIPTION_FAILED -> Message("Falló la transcripción", "Inténtalo de nuevo.") {
                TextButton(onClick = { controller.close() }) { Text("Cerrar") }
            }
        }
    }

    private fun openMicrophoneSettings() {
        ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone").start()
    }

    private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1000) % 60)

    companion object {
        const val TITLE = "DictadoAI-Panel"
        const val WIDTH = 320
        const val LIMIT_TEXT = "Se alcanzó el límite de 10 minutos."
    }
}
