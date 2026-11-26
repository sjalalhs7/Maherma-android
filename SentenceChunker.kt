package com.maherma.app.orchestrator

/** Cuts streamed text into speakable chunks so TTS can start at the first sentence. */
class SentenceChunker {
    private val buf = StringBuilder()

    fun push(delta: String): List<String> {
        buf.append(delta)
        val out = mutableListOf<String>()
        while (true) {
            val cut = findCut()
            if (cut < 0) break
            out += buf.substring(0, cut).trim()
            buf.delete(0, cut)
        }
        return out.filter { it.isNotEmpty() }
    }

    fun flush(): String? {
        val s = buf.toString().trim()
        buf.clear()
        return s.ifEmpty { null }
    }

    private fun findCut(): Int {
        for (i in buf.indices) {
            val c = buf[i]
            if (c == '.' || c == '!' || c == '?' || c == '۔' || c == '؟' || c == '\n') {
                if (i == buf.lastIndex) return if (c == '\n') i + 1 else -1   // wait: "3." may become "3.5"
                if (buf[i + 1].isWhitespace()) return i + 1
            }
        }
        if (buf.length > 180) {
            val sp = buf.lastIndexOf(" ", 160)
            if (sp > 40) return sp + 1
        }
        return -1
    }
}
