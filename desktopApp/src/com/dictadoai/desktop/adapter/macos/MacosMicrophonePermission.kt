package com.dictadoai.desktop.adapter.macos

import com.dictadoai.core.port.MicrophonePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Permiso de micrófono de macOS (TCC) a través de AVFoundation. */
class MacosMicrophonePermission(private val bridge: MacosBridge) : MicrophonePermission {

    override suspend fun ensureGranted(): Boolean = withContext(Dispatchers.IO) {
        when (bridge.dictado_macos_mic_status()) {
            STATUS_AUTHORIZED -> true
            STATUS_NOT_DETERMINED -> bridge.dictado_macos_request_mic() == 1 // muestra el diálogo del sistema
            else -> false // denegado o restringido
        }
    }

    private companion object {
        const val STATUS_NOT_DETERMINED = 0
        const val STATUS_AUTHORIZED = 3
    }
}
