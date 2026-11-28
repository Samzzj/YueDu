package com.yuedu.reader.tts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Local MP3 cache for Edge ReadAloud audio keyed by (voiceName, speechRate, text),
 * ensuring voice previews, online playback, and offline downloaded chapters sound 100% identical.
 */
class TtsAudioCache(context: Context) {

    private val audioRoot = File(context.filesDir, "tts_edge_audio").apply { mkdirs() }
    private val indexRoot = File(context.filesDir, "tts_edge_chapters").apply { mkdirs() }

    private fun rateKey(speechRate: Float): String =
        "%.2f".format(Locale.US, speechRate.coerceIn(0.5f, 2.5f))

    fun clipKey(voiceName: String, speechRate: Float, text: String): String {
        val raw = "$voiceName|${rateKey(speechRate)}|${text.trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun clipFile(voiceName: String, speechRate: Float, text: String): File {
        val key = clipKey(voiceName, speechRate, text)
        val safeVoice = voiceName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(audioRoot, "$safeVoice/${key.substring(0, 2)}")
        dir.mkdirs()
        return File(dir, "$key.mp3")
    }

    fun hasCachedClip(voiceName: String, speechRate: Float, text: String): Boolean {
        val f = clipFile(voiceName, speechRate, text)
        return f.exists() && f.length() > 64L
    }

    fun readCachedClip(voiceName: String, speechRate: Float, text: String): ByteArray? {
        val f = clipFile(voiceName, speechRate, text)
        if (!f.exists() || f.length() <= 64L) return null
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun writeCachedClip(voiceName: String, speechRate: Float, text: String, bytes: ByteArray): File? {
        if (bytes.size <= 64) return null
        val f = clipFile(voiceName, speechRate, text)
        if (f.exists() && f.length() == bytes.size.toLong()) return f
        return runCatching {
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) {
                f.writeBytes(bytes)
                tmp.delete()
            }
            f
        }.getOrNull()
    }

    private fun bookIndexFile(bookId: String): File = File(indexRoot, "$bookId.json")

    @Synchronized
    fun markChapterCached(bookId: String, chapterIndex: Int, voiceId: String, speechRate: Float) {
        val existing = getBookCachedChapters(bookId, voiceId, speechRate).toMutableSet()
        existing.add(chapterIndex)
        val root = runCatching {
            val f = bookIndexFile(bookId)
            if (f.exists()) JSONObject(f.readText(StandardCharsets.UTF_8)) else JSONObject()
        }.getOrElse { JSONObject() }
        val mapKey = "${voiceId}_${rateKey(speechRate)}"
        val arr = JSONArray()
        existing.sorted().forEach { arr.put(it) }
        root.put(mapKey, arr)
        runCatching { bookIndexFile(bookId).writeText(root.toString(), StandardCharsets.UTF_8) }
    }

    @Synchronized
    fun getBookCachedChapters(bookId: String, voiceId: String, speechRate: Float): Set<Int> {
        val f = bookIndexFile(bookId)
        if (!f.exists()) return emptySet()
        return runCatching {
            val root = JSONObject(f.readText(StandardCharsets.UTF_8))
            val arr = root.optJSONArray("${voiceId}_${rateKey(speechRate)}") ?: return@runCatching emptySet()
            buildSet {
                for (i in 0 until arr.length()) add(arr.getInt(i))
            }
        }.getOrDefault(emptySet())
    }

    @Synchronized
    fun getAllCachedChapterCounts(voiceId: String, speechRate: Float): Map<String, Int> {
        val files = indexRoot.listFiles() ?: return emptyMap()
        val key = "${voiceId}_${rateKey(speechRate)}"
        return buildMap {
            for (f in files) {
                if (!f.name.endsWith(".json")) continue
                val bookId = f.name.removeSuffix(".json")
                val cnt = runCatching {
                    JSONObject(f.readText(StandardCharsets.UTF_8)).optJSONArray(key)?.length() ?: 0
                }.getOrDefault(0)
                if (cnt > 0) put(bookId, cnt)
            }
        }
    }

    fun getTotalCacheBytes(): Long {
        var total = 0L
        audioRoot.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    @Synchronized
    fun clearBookIndex(bookId: String) {
        runCatching { bookIndexFile(bookId).delete() }
    }

    @Synchronized
    fun clearAll() {
        runCatching { audioRoot.deleteRecursively() }
        runCatching { indexRoot.deleteRecursively() }
        audioRoot.mkdirs()
        indexRoot.mkdirs()
    }
}
