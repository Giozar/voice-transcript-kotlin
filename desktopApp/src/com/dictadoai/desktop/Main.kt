package com.dictadoai.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// Provisional: se reemplaza por la raíz de composición en la fase 6.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "DictadoAI") {
        MaterialTheme {
            Text("DictadoAI")
        }
    }
}
