package com.dictadoai.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dictadoai.core.application.DictationController
import com.dictadoai.core.domain.DictationState
import com.dictadoai.core.domain.FailureKind
import com.dictadoai.core.domain.Notice
import com.dictadoai.core.port.ClipboardWriter
import com.dictadoai.desktop.adapter.macos.MacosBridge
import com.dictadoai.desktop.adapter.macos.configureFloating
import java.awt.Point

/**
 * Panel junto a la bolita: controles durante la grabación y texto editable de la sesión.
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
    private var field by mutableStateOf(TextFieldValue(""))
    private var currentHeight = 0

    fun create() {
        window = createFloatingComposeWindow(TITLE, WIDTH, HEIGHT_COMPACT).apply { setContent { PanelContent() } }
    }

    /** Muestra, oculta y redimensiona el panel según el estado. */
    fun update(state: DictationState) {
        val editable = state is DictationState.Paused || state is DictationState.Result
        if (!editable) leaveEditing()

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

    private fun hasText() = controller.document.value.text.isNotBlank()

    private fun heightFor(s: DictationState): Int? = when (s) {
        DictationState.Idle, DictationState.LoadingModel -> null
        is DictationState.ModelError -> HEIGHT_COMPACT
        is DictationState.Starting -> if (s.resumed) HEIGHT_SESSION else HEIGHT_COMPACT
        is DictationState.Recording -> if (s.resumed) HEIGHT_SESSION else HEIGHT_COMPACT
        is DictationState.Transcribing -> if (hasText()) HEIGHT_SESSION else HEIGHT_TRANSCRIBING
        is DictationState.Paused, is DictationState.Result -> HEIGHT_SESSION
        is DictationState.NoSpeech -> HEIGHT_COMPACT
        is DictationState.Failure -> if (s.kind == FailureKind.MIC_DENIED) HEIGHT_MIC_DENIED else HEIGHT_COMPACT
    }

    /** Al hacer clic en el texto: el panel pasa a aceptar teclado para poder editar. */
    private fun enterEditing() {
        if (editing) return
        editing = true
        setFocusable(true)
        mac.dictado_macos_activate_app()
        window.toFront()
        window.requestFocus()
    }

    /** Al pulsar cualquier botón: el panel deja de aceptar teclado y el foco vuelve a la app anterior. */
    private fun leaveEditing() {
        if (!editing) return
        editing = false
        setFocusable(false)
        mac.dictado_macos_restore_focus() // devuelve el foco a la app donde se va a pegar
    }

    /**
     * En macOS, cambiar `focusableWindowState` con la ventana visible no se aplica de forma fiable
     * (Java solo garantiza el cambio al volver a mostrarla). Por eso se oculta, se cambia y se vuelve a mostrar.
     */
    private fun setFocusable(focusable: Boolean) {
        if (window.focusableWindowState == focusable) return
        val wasVisible = window.isVisible
        window.isVisible = false
        window.focusableWindowState = focusable
        if (wasVisible) {
            window.isVisible = true
            mac.configureFloating(TITLE)
        }
    }

    /** Ejecuta la acción de un botón devolviendo antes el foco a la app anterior si se estaba editando. */
    private fun act(action: () -> Unit) {
        leaveEditing()
        action()
    }

    @Composable
    private fun PanelContent() {
        val state by controller.state.collectAsState()
        val elapsed by controller.elapsedMillis.collectAsState()
        val doc by controller.document.collectAsState()

        // El texto lo cambia el controlador (al insertar un fragmento) o la persona (al escribir).
        LaunchedEffect(doc) {
            val selection = TextRange(doc.selectionStart, doc.selectionEnd)
            if (field.text != doc.text || field.selection != selection) field = TextFieldValue(doc.text, selection)
        }

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
                        is DictationState.ModelError -> Message("No se pudo cargar el modelo de voz", s.message) { CloseButton() }
                        is DictationState.Starting ->
                            if (s.resumed) Session(header = { Title("Preparando micrófono…") }, hint = null, textEditable = false) {}
                            else Title("Preparando micrófono…")
                        is DictationState.Recording -> {
                            val actions: @Composable ColumnScope.() -> Unit = { RecordingActions() }
                            if (s.resumed) Session(header = { RecordingHeader("Grabando", elapsed) }, hint = null, textEditable = false, actions = actions)
                            else { RecordingHeader("Grabando", elapsed); actions() }
                        }
                        is DictationState.Transcribing -> {
                            val header: @Composable () -> Unit = {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 3.dp)
                                    Title("Transcribiendo…")
                                }
                            }
                            if (doc.text.isNotBlank()) Session(header = header, hint = null, textEditable = false) {} else header()
                        }
                        is DictationState.Paused -> Session(
                            header = { Title("En pausa") },
                            hint = noticeText(s.notice) ?: EDIT_HINT,
                            textEditable = true,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { act { controller.resume() } }) { Text("Reanudar") }
                                OutlinedButton(onClick = { act { controller.finish() } }) { Text("Terminar") }
                                TextButton(onClick = { act { controller.cancel() } }) { Text("Descartar") }
                            }
                        }
                        is DictationState.Result -> Session(
                            header = { Title("Listo") },
                            hint = noticeText(s.notice) ?: if (s.limitReached) LIMIT_TEXT else "Copiado. $EDIT_HINT",
                            textEditable = true,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { act { controller.continueDictation() } }) { Text("Seguir dictando") }
                                OutlinedButton(onClick = { act { clipboard.copy(field.text) } }) { Text("Copiar") }
                                CloseButton()
                            }
                        }
                        is DictationState.NoSpeech -> Message(
                            "No se detectó voz",
                            if (s.limitReached) LIMIT_TEXT else "Comprueba el volumen de entrada del micrófono.",
                        ) { CloseButton() }
                        is DictationState.Failure -> FailureView(s.kind)
                    }
                }
            }
        }
    }

    /** Estructura común de las pantallas con texto: cabecera, aviso, cuadro de texto y acciones. */
    @Composable
    private fun Session(
        header: @Composable () -> Unit,
        hint: String?,
        textEditable: Boolean,
        actions: @Composable ColumnScope.() -> Unit,
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(editing) { if (editing) focus.requestFocus() }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            header()
            if (hint != null) Text(hint, fontSize = 12.sp, color = DictadoColors.PanelText.copy(alpha = 0.7f))
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    controller.updateDocument(it.text, it.selection.start, it.selection.end)
                },
                readOnly = !(textEditable && editing),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focus)
                    .pointerInput(textEditable) {
                        if (!textEditable) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type == PointerEventType.Press && !editing) enterEditing()
                            }
                        }
                    },
            )
            actions()
        }
    }

    private fun noticeText(n: Notice): String? = when (n) {
        Notice.NONE -> null
        Notice.NO_SPEECH -> "No se detectó voz en el último fragmento."
        Notice.TRANSCRIPTION_FAILED -> "Falló la transcripción del último fragmento."
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
    private fun RecordingHeader(label: String, elapsedMillis: Long) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title(label)
            Text(formatTime(elapsedMillis), fontSize = 15.sp)
        }
    }

    @Composable
    private fun RecordingActions() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { act { controller.pause() } }) { Text("Pausar") }
            Button(onClick = { act { controller.finish() } }) { Text("Terminar") }
            TextButton(onClick = { act { controller.cancel() } }) { Text("Cancelar") }
        }
    }

    @Composable
    private fun CloseButton() = TextButton(onClick = { act { controller.close() } }) { Text("Cerrar") }

    @Composable
    private fun FailureView(kind: FailureKind) {
        when (kind) {
            FailureKind.MIC_DENIED -> Message(
                "Sin permiso de micrófono",
                "Actívalo en Ajustes del Sistema > Privacidad y seguridad > Micrófono.",
            ) {
                Button(onClick = { openMicrophoneSettings() }) { Text("Abrir ajustes") }
                CloseButton()
            }
            FailureKind.MIC_UNAVAILABLE -> Message("No se encontró un micrófono", "Conecta uno y vuelve a intentarlo.") { CloseButton() }
            FailureKind.TRANSCRIPTION_FAILED -> Message("Falló la transcripción", "Inténtalo de nuevo.") { CloseButton() }
        }
    }

    private fun openMicrophoneSettings() {
        ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone").start()
    }

    private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1000) % 60)

    companion object {
        const val TITLE = "DictadoAI-Panel"
        const val WIDTH = 400
        const val HEIGHT_COMPACT = 130
        const val HEIGHT_TRANSCRIBING = 100
        const val HEIGHT_MIC_DENIED = 190
        const val HEIGHT_SESSION = 300
        const val LIMIT_TEXT = "Se alcanzó el límite de 10 minutos."
        const val EDIT_HINT = "Haz clic en el texto para editarlo; lo nuevo se dicta donde esté el cursor."
    }
}
