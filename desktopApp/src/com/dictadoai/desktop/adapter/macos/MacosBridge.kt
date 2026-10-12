package com.dictadoai.desktop.adapter.macos

import com.dictadoai.desktop.config.AppPaths
import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File

/** Interfaz JNA de `libdictado_macos.dylib` (ver `native/macos_bridge.m`). */
interface MacosBridge : Library {
    fun dictado_macos_ping(): Int
    fun dictado_macos_set_accessory()
    fun dictado_macos_configure_floating(title: String, info: ByteArray, infoLen: Int): Int
    fun dictado_macos_frontmost_app(buf: ByteArray, len: Int): Int
    fun dictado_macos_focus_state(buf: ByteArray, len: Int): Int
    fun dictado_macos_start_focus_guard()
    fun dictado_macos_restore_focus()

    companion object {
        fun load(paths: AppPaths): MacosBridge = Native.load(
            File(paths.libDir, "libdictado_macos.dylib").absolutePath,
            MacosBridge::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
        )
    }
}

/** Resultado de configurar la ventana como flotante. */
data class FloatingResult(val code: Int, val info: String) {
    val isNonActivatingPanel: Boolean get() = code == 0
}

fun MacosBridge.configureFloating(title: String): FloatingResult {
    val buf = ByteArray(256)
    val code = dictado_macos_configure_floating(title, buf, buf.size)
    return FloatingResult(code, buf.decodeToString().substringBefore('\u0000'))
}

fun MacosBridge.frontmostApp(): String {
    val buf = ByteArray(256)
    return if (dictado_macos_frontmost_app(buf, buf.size) == 0) buf.decodeToString().substringBefore('\u0000') else "?"
}

fun MacosBridge.focusState(): String {
    val buf = ByteArray(256)
    return if (dictado_macos_focus_state(buf, buf.size) == 0) buf.decodeToString().substringBefore('\u0000') else "?"
}
