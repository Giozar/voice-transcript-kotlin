package com.dictadoai.desktop.ui

import androidx.compose.ui.awt.ComposeWindow
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window

/** Ventana de Compose sin borde, transparente, siempre encima y que no toma el foco (se crea como NSPanel en macOS). */
fun createFloatingComposeWindow(windowTitle: String, width: Int, height: Int): ComposeWindow =
    ComposeWindow().apply {
        title = windowTitle
        isUndecorated = true
        isTransparent = true
        isResizable = false
        isAlwaysOnTop = true
        type = Window.Type.UTILITY
        focusableWindowState = false
        isAutoRequestFocus = false
        setSize(width, height)
    }

/** Área visible (sin barra de menús ni Dock) de la pantalla que contiene el punto, o de la principal. */
fun visibleScreenBounds(around: Point): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val device = env.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(around) } ?: env.defaultScreenDevice
    val cfg = device.defaultConfiguration
    val b = cfg.bounds
    val i = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(cfg)
    return Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
}

/** True si el punto cae dentro de alguna pantalla conectada. */
fun isOnAnyScreen(p: Point): Boolean =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { it.defaultConfiguration.bounds.contains(p) }
