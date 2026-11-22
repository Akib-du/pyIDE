package com.example.pyide

import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue

/** Python calls these methods: write() for output, readLine() for input(), plot() for matplotlib figures. */
class Bridge(
    private val onOut: (String) -> Unit,
    private val onPlotCb: (String) -> Unit,
    private val onInput: (Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val queue = LinkedBlockingQueue<String>()

    fun write(s: String) { main.post { onOut(s) } }

    fun plot(path: String) { main.post { onPlotCb(path) } }

    /** Blocks the Python thread until the user submits a line (or Stop is pressed). */
    fun readLine(prompt: String): String {
        queue.clear()
        main.post { onOut(prompt); onInput(true) }
        val v = queue.take()
        main.post { onInput(false) }
        return v
    }

    fun submit(line: String) { queue.offer(line) }

    fun cancel() { queue.offer(CANCEL) }

    companion object { const val CANCEL = "__PYIDE_CANCEL__" }
}
