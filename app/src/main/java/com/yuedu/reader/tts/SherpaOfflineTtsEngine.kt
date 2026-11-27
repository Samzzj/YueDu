package com.yuedu.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.yuedu.reader.data.CommercialFontsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Offline neural TTS engine powered by `k2-fsa/sherpa-onnx`.
 * Uses FP32 VITS / Matcha models, Chinese text normalization (preventing skipped chars),
 * lookahead pre-synthesis, and a persistent 16-bit PCM AudioTrack for smooth gapless playback.
 */
class SherpaOfflineTtsEngine(private val context: Context) {

    enum class PackKind { VITS, MATCHA }

    data class OfflineModelPack(
        val id: String,
        val title: String,
        val sizeLabel: String,
        val kind: PackKind,
        val downloadUrls: List<String>
    )

    data class SynthesizedAudio(
        val pcm: ShortArray,
        val sampleRate: Int
    )

    companion object {
        const val BUILTIN_YUNXI_KEY = "__builtin_yunxi_vits__"
        const val BUILTIN_YUNXI_ASSET_DIR = "tts_models/vits-zh-hf-fanchen-wnj"
        const val YUNXI_BUILTIN_SID = 0

        const val PACK_MATCHA_BAKER = "matcha_baker"
        const val PACK_VITS_THERESA = "vits_theresa"
        const val PACK_VITS_UNITY = "vits_unity"
        const val PACK_VITS_ZHIHUI = "vits_zhihui"
        const val PACK_VITS_KEQING = "vits_keqing"
        const val PACK_VITS_EULA = "vits_eula"
        const val PACK_VITS_ECHO = "vits_echo"

        private fun modelUrls(archiveName: String): List<String> = listOf(
            "https://gh-proxy.com/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName"
        )

        val modelPacks: Map<String, OfflineModelPack> = mapOf(
            PACK_MATCHA_BAKER to OfflineModelPack(
                id = PACK_MATCHA_BAKER,
                title = "小贝离线包",
                sizeLabel = "72MB",
                kind = PackKind.MATCHA,
                downloadUrls = modelUrls("matcha-icefall-zh-baker.tar.bz2")
            ),
            PACK_VITS_THERESA to OfflineModelPack(
                id = PACK_VITS_THERESA,
                title = "晓晓离线包",
                sizeLabel = "115MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-theresa.tar.bz2")
            ),
            PACK_VITS_UNITY to OfflineModelPack(
                id = PACK_VITS_UNITY,
                title = "云健离线包",
                sizeLabel = "114MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-fanchen-unity.tar.bz2")
            ),
            PACK_VITS_ZHIHUI to OfflineModelPack(
                id = PACK_VITS_ZHIHUI,
                title = "云扬离线包",
                sizeLabel = "114MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-fanchen-ZhiHuiLaoZhe.tar.bz2")
            ),
            PACK_VITS_KEQING to OfflineModelPack(
                id = PACK_VITS_KEQING,
                title = "晓伊离线包",
                sizeLabel = "115MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-keqing.tar.bz2")
            ),
            PACK_VITS_EULA to OfflineModelPack(
                id = PACK_VITS_EULA,
                title = "晓北离线包",
                sizeLabel = "115MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-eula.tar.bz2")
            ),
            PACK_VITS_ECHO to OfflineModelPack(
                id = PACK_VITS_ECHO,
                title = "晓妮离线包",
                sizeLabel = "115MB",
                kind = PackKind.VITS,
                downloadUrls = modelUrls("vits-zh-hf-echo.tar.bz2")
            )
        )

        private val digitsZh = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

        /**
         * Normalizes Chinese novel text (numbers, percentages, decimals, quotes, brackets, dashes)
         * into clean tokens present in VITS/Matcha lexicons so words are never skipped or dropped.
         */
        fun normalizeTextForOfflineTts(raw: String): String {
            if (raw.isBlank()) return ""
            val half = buildString(raw.length) {
                for (ch in raw) {
                    val c = ch.code
                    when {
                        c in 0xFF01..0xFF5E -> append((c - 0xFEE0).toChar())
                        c == 0x3000 || c == 0x00A0 -> append(' ')
                        c in 0x0000..0x001F -> if (ch == '\n') append('。') else append(' ')
                        else -> append(ch)
                    }
                }
            }

            var text = half
                // Percentages: 12.5% -> 百分之十二点五
                .replace(Regex("""(\d+(?:\.\d+)?)\s*%""")) { m ->
                    "百分之" + decimalToChinese(m.groupValues[1])
                }
                // Decimals: 3.14 -> 三点一四
                .replace(Regex("""\d+\.\d+""")) { m ->
                    decimalToChinese(m.value)
                }
                // Standalone integers
                .replace(Regex("""\d+""")) { m ->
                    val numStr = m.value
                    if (numStr.length == 4 && numStr.startsWith("19") || numStr.startsWith("20") && numStr.length == 4) {
                        digitsOnlyToChinese(numStr)
                    } else if (numStr.length > 8) {
                        digitsOnlyToChinese(numStr)
                    } else {
                        integerToChinese(numStr)
                    }
                }

            val sb = StringBuilder(text.length)
            var lastWasPause = false
            for (ch in text) {
                when (ch) {
                    '，', '。', '！', '？' -> {
                        if (!lastWasPause && sb.isNotEmpty()) {
                            sb.append(ch)
                            lastWasPause = true
                        }
                    }
                    ',', ';', '；', ':', '：', '、', '—', '–', '-', '~', '～', '…', '·' -> {
                        if (!lastWasPause && sb.isNotEmpty()) {
                            sb.append('，')
                            lastWasPause = true
                        }
                    }
                    '.' -> {
                        if (!lastWasPause && sb.isNotEmpty()) {
                            sb.append('。')
                            lastWasPause = true
                        }
                    }
                    '!' -> {
                        if (!lastWasPause && sb.isNotEmpty()) {
                            sb.append('！')
                            lastWasPause = true
                        }
                    }
                    '?' -> {
                        if (!lastWasPause && sb.isNotEmpty()) {
                            sb.append('？')
                            lastWasPause = true
                        }
                    }
                    // Strip brackets/quotes cleanly so they never merge into adjacent Chinese words as OOV
                    '“', '”', '‘', '’', '"', '\'', '「', '」', '『', '』',
                    '《', '》', '〈', '〉', '【', '】', '（', '）', '(', ')', '[', ']', '{', '}', ' ' -> {
                        // Skip silently without inserting invalid lexicon tokens
                    }
                    else -> {
                        if (ch.code >= 0x4E00 && ch.code <= 0x9FFF || ch.code in 0x3400..0x4DBF) {
                            sb.append(ch)
                            lastWasPause = false
                        } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                            sb.append(ch)
                            lastWasPause = false
                        }
                    }
                }
            }
            return sb.toString().trim('，', '。', '！', '？')
        }

        private fun digitsOnlyToChinese(s: String): String =
            s.map { c -> digitsZh[(c - '0').coerceIn(0, 9)] }.joinToString("")

        private fun decimalToChinese(s: String): String {
            val parts = s.split('.', limit = 2)
            val intPart = integerToChinese(parts[0])
            if (parts.size < 2 || parts[1].isEmpty()) return intPart
            return "${intPart}点${digitsOnlyToChinese(parts[1])}"
        }

        private fun integerToChinese(raw: String): String {
            val clean = raw.trimStart('0')
            if (clean.isEmpty()) return "零"
            val value = clean.toLongOrNull() ?: return digitsOnlyToChinese(raw)
            if (value == 0L) return "零"
            if (value >= 100_000_000L) {
                val yi = value / 100_000_000L
                val rem = value % 100_000_000L
                val head = "${integerToChinese(yi.toString())}亿"
                return if (rem == 0L) head else if (rem < 10_000_000L) "${head}零${integerToChinese(rem.toString())}" else "$head${integerToChinese(rem.toString())}"
            }
            if (value >= 10_000L) {
                val wan = value / 10_000L
                val rem = value % 10_000L
                val head = "${integerToChinese(wan.toString())}万"
                return if (rem == 0L) head else if (rem < 1_000L) "${head}零${integerToChinese(rem.toString())}" else "$head${integerToChinese(rem.toString())}"
            }
            val n = value.toInt()
            val qian = n / 1000
            val bai = (n % 1000) / 100
            val shi = (n % 100) / 10
            val ge = n % 10
            val sb = StringBuilder()
            var needZero = false
            if (qian > 0) {
                sb.append(digitsZh[qian]).append("千")
            }
            if (bai > 0) {
                sb.append(digitsZh[bai]).append("百")
            } else if (qian > 0 && (shi > 0 || ge > 0)) {
                needZero = true
            }
            if (shi > 0) {
                if (needZero) {
                    sb.append("零")
                    needZero = false
                }
                if (!(qian == 0 && bai == 0 && shi == 1)) {
                    sb.append(digitsZh[shi])
                }
                sb.append("十")
            } else if (bai > 0 && ge > 0) {
                needZero = true
            }
            if (ge > 0) {
                if (needZero) sb.append("零")
                sb.append(digitsZh[ge])
            }
            return sb.toString()
        }
    }

    private val modelsRoot = File(context.filesDir, "tts_models").apply { mkdirs() }
    private var loadedKey: String? = null
    private var loadedTts: OfflineTts? = null
    private var currentAudioTrack: AudioTrack? = null
    private var activeSampleRate: Int = 0

    private val builtinAvailableCached: Boolean by lazy {
        runCatching {
            context.assets.list(BUILTIN_YUNXI_ASSET_DIR)?.contains("vits-zh-hf-fanchen-wnj.onnx") == true
        }.getOrDefault(false)
    }

    fun isBuiltinAvailable(): Boolean = builtinAvailableCached

    fun getDownloadedPackIds(): Set<String> {
        return modelPacks.keys.filter { isPackDownloaded(it) }.toSet()
    }

    fun isPackDownloaded(packId: String): Boolean {
        val dir = File(modelsRoot, packId)
        return File(dir, ".ready").exists()
    }

    suspend fun downloadAndInstallPack(
        packId: String,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val pack = modelPacks[packId] ?: throw IllegalArgumentException("未知离线语音包")
        if (isPackDownloaded(packId)) {
            onProgress(1f)
            return@withContext
        }

        val archiveFile = File(context.cacheDir, "tts_pack_$packId.tar.bz2")
        val targetDir = File(modelsRoot, packId)
        try {
            CommercialFontsCatalog.downloadFontFromMirrors(pack.downloadUrls, archiveFile) { dlProg ->
                onProgress((dlProg * 0.85f).coerceIn(0f, 0.85f))
            }

            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            extractTarBz2(archiveFile, targetDir)
            onProgress(0.96f)

            val modelRoot = resolveModelSubDir(targetDir)
            val hasOnnx = modelRoot.listFiles()?.any { it.name.endsWith(".onnx") } == true
            if (!hasOnnx) {
                throw IllegalStateException("离线模型解压不完整")
            }

            File(targetDir, ".ready").createNewFile()
            onProgress(1f)
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        } finally {
            archiveFile.delete()
        }
    }

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        BufferedInputStream(FileInputStream(archiveFile), 65536).use { raw ->
            BZip2CompressorInputStream(raw).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val outFile = File(destDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(canonicalDest)) continue
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                while (true) {
                                    val r = tar.read(buf)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveModelSubDir(packDir: File): File {
        val directOnnx = packDir.listFiles()?.any { it.isFile && it.name.endsWith(".onnx") } == true
        if (directOnnx) return packDir
        return packDir.listFiles()?.firstOrNull { sub ->
            sub.isDirectory && sub.listFiles()?.any { it.name.endsWith(".onnx") } == true
        } ?: packDir
    }

    @Synchronized
    private fun getOrCreateBuiltinYunxi(): OfflineTts {
        if (loadedKey == BUILTIN_YUNXI_KEY && loadedTts != null) {
            return loadedTts!!
        }
        runCatching { loadedTts?.free() }
        loadedTts = null
        loadedKey = null

        val dir = BUILTIN_YUNXI_ASSET_DIR
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$dir/vits-zh-hf-fanchen-wnj.onnx",
                    lexicon = "$dir/lexicon.txt",
                    tokens = "$dir/tokens.txt"
                ),
                numThreads = 2,
                provider = "cpu"
            ),
            ruleFsts = "$dir/phone.fst,$dir/date.fst,$dir/number.fst"
        )
        val created = OfflineTts(assetManager = context.assets, config = config)
        loadedKey = BUILTIN_YUNXI_KEY
        loadedTts = created
        return created
    }

    @Synchronized
    private fun getOrCreateDownloadedTts(packId: String): OfflineTts {
        if (loadedKey == packId && loadedTts != null) {
            return loadedTts!!
        }
        runCatching { loadedTts?.free() }
        loadedTts = null
        loadedKey = null

        val pack = modelPacks[packId] ?: throw IllegalArgumentException("未知模型")
        val dir = resolveModelSubDir(File(modelsRoot, packId))
        val files = dir.listFiles().orEmpty()
        fun findFile(predicate: (File) -> Boolean): String =
            files.firstOrNull(predicate)?.absolutePath.orEmpty()

        val tokensPath = findFile { it.name == "tokens.txt" }
        val fstPaths = files
            .filter { it.name in setOf("phone.fst", "date.fst", "number.fst") }
            .sortedBy { it.name }
            .joinToString(",") { it.absolutePath }
        val farPaths = files
            .filter { it.name.endsWith(".far") }
            .sortedBy { it.name }
            .joinToString(",") { it.absolutePath }

        val config = when (pack.kind) {
            PackKind.MATCHA -> {
                val acoustic = findFile { it.name.contains("model") && it.name.endsWith(".onnx") }
                val vocoder = findFile { it.name.contains("vocos") && it.name.endsWith(".onnx") }
                val lex = findFile { it.name == "lexicon.txt" }
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        matcha = OfflineTtsMatchaModelConfig(
                            acousticModel = acoustic,
                            vocoder = vocoder,
                            lexicon = lex,
                            tokens = tokensPath
                        ),
                        numThreads = 2,
                        provider = "cpu"
                    ),
                    ruleFsts = fstPaths,
                    ruleFars = farPaths
                )
            }

            PackKind.VITS -> {
                val modelPath = findFile { it.name.endsWith(".onnx") }
                val lex = findFile { it.name == "lexicon.txt" }
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = modelPath,
                            lexicon = lex,
                            tokens = tokensPath
                        ),
                        numThreads = 2,
                        provider = "cpu"
                    ),
                    ruleFsts = fstPaths,
                    ruleFars = farPaths
                )
            }
        }

        val created = OfflineTts(assetManager = null, config = config)
        loadedKey = packId
        loadedTts = created
        return created
    }

    suspend fun synthesizeBuiltinToPcm(
        sid: Int,
        rawText: String,
        speed: Float
    ): SynthesizedAudio = withContext(Dispatchers.IO) {
        val clean = normalizeTextForOfflineTts(rawText)
        if (clean.isEmpty()) return@withContext SynthesizedAudio(ShortArray(0), 16000)
        val tts = getOrCreateBuiltinYunxi()
        currentCoroutineContext().ensureActive()
        val audio = tts.generate(text = clean, sid = sid, speed = speed.coerceIn(0.5f, 2.5f))
        currentCoroutineContext().ensureActive()
        SynthesizedAudio(toSanitizedPcm16(audio.samples, audio.sampleRate), audio.sampleRate)
    }

    suspend fun synthesizeDownloadedToPcm(
        packId: String,
        sid: Int,
        rawText: String,
        speed: Float
    ): SynthesizedAudio = withContext(Dispatchers.IO) {
        val clean = normalizeTextForOfflineTts(rawText)
        if (clean.isEmpty()) return@withContext SynthesizedAudio(ShortArray(0), 16000)
        val tts = getOrCreateDownloadedTts(packId)
        currentCoroutineContext().ensureActive()
        val audio = tts.generate(text = clean, sid = sid, speed = speed.coerceIn(0.5f, 2.5f))
        currentCoroutineContext().ensureActive()
        SynthesizedAudio(toSanitizedPcm16(audio.samples, audio.sampleRate), audio.sampleRate)
    }

    private fun toSanitizedPcm16(samples: FloatArray, sampleRate: Int): ShortArray {
        if (samples.isEmpty() || sampleRate <= 0) return ShortArray(0)
        val pcm = ShortArray(samples.size)
        val fadeSamples = minOf(samples.size / 4, (sampleRate * 0.004f).toInt().coerceAtLeast(1))
        for (i in samples.indices) {
            var s = samples[i]
            if (!s.isFinite()) s = 0f
            s = s.coerceIn(-0.98f, 0.98f)
            if (i < fadeSamples) {
                s *= (i.toFloat() / fadeSamples.toFloat())
            } else if (i >= samples.size - fadeSamples) {
                s *= ((samples.size - 1 - i).toFloat() / fadeSamples.toFloat())
            }
            pcm[i] = (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    /**
     * Streams synthesized PCM into a persistent AudioTrack kept alive across consecutive sentences
     * so chapter playback runs seamlessly without between-sentence gaps or track teardown pops.
     */
    suspend fun playStreamingPcm(audio: SynthesizedAudio) = withContext(Dispatchers.IO) {
        if (audio.pcm.isEmpty() || audio.sampleRate <= 0) return@withContext
        val track = ensureAudioTrack(audio.sampleRate)
        var offset = 0
        val chunkSize = 2048
        while (offset < audio.pcm.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(chunkSize, audio.pcm.size - offset)
            val written = track.write(audio.pcm, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
    }

    @Synchronized
    private fun ensureAudioTrack(sampleRate: Int): AudioTrack {
        val existing = currentAudioTrack
        if (existing != null && activeSampleRate == sampleRate && existing.state == AudioTrack.STATE_INITIALIZED) {
            if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
                existing.play()
            }
            return existing
        }
        stopAudioTrack()

        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufBytes * 3)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        currentAudioTrack = track
        activeSampleRate = sampleRate
        return track
    }

    fun stopPlayback() {
        synchronized(this) {
            stopAudioTrack()
        }
    }

    private fun stopAudioTrack() {
        val t = currentAudioTrack ?: return
        runCatching {
            t.pause()
            t.flush()
            t.stop()
        }
        runCatching { t.release() }
        currentAudioTrack = null
        activeSampleRate = 0
    }

    fun release() {
        stopPlayback()
        runCatching { loadedTts?.free() }
        loadedTts = null
        loadedKey = null
    }
}
