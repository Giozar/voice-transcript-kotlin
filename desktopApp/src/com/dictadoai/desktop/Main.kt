package com.dictadoai.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dictadoai.core.Version

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = Version.NAME) {
        MaterialTheme {
            Text(Version.NAME)
        }
    }
}
