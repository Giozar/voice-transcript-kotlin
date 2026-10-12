package org.example.voicetranscriptkotlin

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "voice-transcript-kotlin") {
        App()
    }
}
