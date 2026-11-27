package com.yuedu.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
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
 * Models are downloaded on-demand via Mainland China mirrors into app internal storage,
 * enabling zero-latency (<80ms) offline playback without network dependency.
 */
class SherpaOfflineTtsEngine(private val context: Context) {

    enum class PackKind { KOKORO, VITS, MATCHA }

    data class OfflineModelPack(
        val id: String,
        val title: String,
        val sizeLabel: String,
        val kind: PackKind,
        val downloadUrls: List<String>
    )

    companion object {
        const val PACK_AISHELL3 = "aishell3"
        const val PACK_MATCHA_BAKER = "matcha_baker"
        const val PACK_KOKORO_V1_0 = "kokoro_v1_0"

        val modelPacks: Map<String, OfflineModelPack> = mapOf(
            PACK_AISHELL3 to OfflineModelPack(
                id = PACK_AISHELL3,
                title = "AISHELL 轻量多音色包",
                sizeLabel = "30 MB",
                kind = PackKind.VITS,
                downloadUrls = listOf(
                    "https://gh-proxy.com/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2",
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2"
                )
            ),
            PACK_MATCHA_BAKER to OfflineModelPack(
                id = PACK_MATCHA_BAKER,
                title = "标贝自然女声离线包",
                sizeLabel = "72 MB",
                kind = PackKind.MATCHA,
                downloadUrls = listOf(
                    "https://gh-proxy.com/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2",
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2"
                )
            ),
            PACK_KOKORO_V1_0 to OfflineModelPack(
                id = PACK_KOKORO_V1_0,
                title = "Kokoro 拟真全系列离线包",
                sizeLabel = "126 MB",
                kind = PackKind.KOKORO,
                downloadUrls = listOf(
                    "https://gh-proxy.com/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2",
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
                )
            )
        )
    }

    private val modelsRoot = File(context.filesDir, "tts_models").apply { mkdirs() }
    private var loadedPackId: String? = null
    private var loadedTts: OfflineTts? = null
    private var currentAudioTrack: AudioTrack? = null

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

            // Verify model folder contains .onnx & tokens.txt
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
    private fun getOrCreateTts(packId: String): OfflineTts {
        if (loadedPackId == packId && loadedTts != null) {
            return loadedTts!!
        }
        runCatching { loadedTts?.free() }
        loadedTts = null
        loadedPackId = null

        val pack = modelPacks[packId] ?: throw IllegalArgumentException("未知模型")
        val dir = resolveModelSubDir(File(modelsRoot, packId))
        val files = dir.listFiles().orEmpty()
        fun findFile(predicate: (File) -> Boolean): String =
            files.firstOrNull(predicate)?.absolutePath.orEmpty()

        val tokensPath = findFile { it.name == "tokens.txt" }
        val fstPaths = files.filter { it.name.endsWith(".fst") }.sortedBy { it.name }.joinToString(",") { it.absolutePath }
        val farPaths = files.filter { it.name.endsWith(".far") }.sortedBy { it.name }.joinToString(",") { it.absolutePath }

        val config = when (pack.kind) {
            PackKind.KOKORO -> {
                val modelPath = findFile { it.name.endsWith(".onnx") }
                val voicesPath = findFile { it.name == "voices.bin" }
                val lexicons = files.filter { it.name.startsWith("lexicon") && it.name.endsWith(".txt") }
                    .joinToString(",") { it.absolutePath }
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = modelPath,
                            voices = voicesPath,
                            tokens = tokensPath,
                            lexicon = lexicons
                        ),
                        numThreads = 4,
                        provider = "cpu"
                    ),
                    ruleFsts = fstPaths,
                    ruleFars = farPaths
                )
            }
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
        loadedPackId = packId
        loadedTts = created
        return created
    }

    suspend fun speakBlocking(
        packId: String,
        sid: Int,
        text: String,
        speed: Float
    ) = withContext(Dispatchers.IO) {
        val tts = getOrCreateTts(packId)
        currentCoroutineContext().ensureActive()
        val audio = tts.generate(text = text, sid = sid, speed = speed.coerceIn(0.5f, 2.5f))
        currentCoroutineContext().ensureActive()
        playFloatPcmBlocking(audio.samples, audio.sampleRate)
    }

    private suspend fun playFloatPcmBlocking(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty() || sampleRate <= 0) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
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
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
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
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(chunkSize, samples.size - offset)
                val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
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
        loadedPackId = null
    }
}
