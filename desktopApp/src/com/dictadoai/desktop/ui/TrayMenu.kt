package com.dictadoai.desktop.ui

import java.awt.Color
import java.awt.PopupMenu
import java.awt.MenuItem
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/** Icono en la barra de menús con la opción "Salir". */
class TrayMenu(private val onExit: () -> Unit) {
    fun install() {
        if (!SystemTray.isSupported()) return
        val menu = PopupMenu().apply {
            add(MenuItem("Salir").apply { addActionListener { onExit() } })
        }
        val icon = TrayIcon(createIcon(), "DictadoAI", menu).apply { isImageAutoSize = true }
        SystemTray.getSystemTray().add(icon)
    }

    private fun createIcon(): BufferedImage {
        val size = 36
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x3B, 0x6F, 0xE0)
        g.fillOval(4, 4, size - 8, size - 8)
        g.dispose()
        return img
    }
}
