package com.dictadoai.desktop.adapter.clipboard

import com.dictadoai.core.port.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class AwtClipboardWriter : ClipboardWriter {
    override fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
