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
 * Uses clean FP32 VITS / Matcha models (avoiding ARM64 Kokoro-int8 iSTFT noise)
 * and outputs sanitized 16-bit PCM to AudioTrack for click/static-free playback.
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
    }

    private val modelsRoot = File(context.filesDir, "tts_models").apply { mkdirs() }
    private var loadedKey: String? = null
    private var loadedTts: OfflineTts? = null
    private var currentAudioTrack: AudioTrack? = null

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

    suspend fun speakBuiltinBlocking(
        sid: Int,
        text: String,
        speed: Float
    ) = withContext(Dispatchers.IO) {
        val tts = getOrCreateBuiltinYunxi()
        currentCoroutineContext().ensureActive()
        val audio = tts.generate(text = text, sid = sid, speed = speed.coerceIn(0.5f, 2.5f))
        currentCoroutineContext().ensureActive()
        playPcm16Blocking(audio.samples, audio.sampleRate)
    }

    suspend fun speakBlocking(
        packId: String,
        sid: Int,
        text: String,
        speed: Float
    ) = withContext(Dispatchers.IO) {
        val tts = getOrCreateDownloadedTts(packId)
        currentCoroutineContext().ensureActive()
        val audio = tts.generate(text = text, sid = sid, speed = speed.coerceIn(0.5f, 2.5f))
        currentCoroutineContext().ensureActive()
        playPcm16Blocking(audio.samples, audio.sampleRate)
    }

    private suspend fun playPcm16Blocking(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty() || sampleRate <= 0) return

        // Convert to sanitized 16-bit PCM with 5ms edge fade to eliminate float HAL buzz and boundary clicks
        val pcm = ShortArray(samples.size)
        val fadeSamples = minOf(samples.size / 4, (sampleRate * 0.005f).toInt().coerceAtLeast(1))
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

        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

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
            .setBufferSizeInBytes(minBufBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        synchronized(this) {
            stopAudioTrack()
            currentAudioTrack = track
        }

        try {
            track.play()
            var offset = 0
            val chunkSize = 2048
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(chunkSize, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
        } finally {
            synchronized(this) {
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                }
                runCatching { track.release() }
                if (currentAudioTrack === track) {
                    currentAudioTrack = null
                }
            }
        }
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
    }

    fun release() {
        stopPlayback()
        runCatching { loadedTts?.free() }
        loadedTts = null
        loadedKey = null
    }
}
