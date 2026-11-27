package com.yuedu.reader.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.yuedu.reader.engine.TxtBookEngine
import com.yuedu.reader.model.TtsSentence
import com.yuedu.reader.model.TtsSettings
import com.yuedu.reader.model.TtsVoiceOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LocalTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val sherpaEngine = SherpaOfflineTtsEngine(appContext)
    private val previewCacheDir = File(appContext.cacheDir, "tts_preview_cache").apply { mkdirs() }

    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            _errorMessage.value = "朗读引擎异常，已自动保护"
            _isPlaying.value = false
            _isPaused.value = false
            TtsPlaybackService.stop(appContext)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + crashHandler)

    private var localTts: TextToSpeech? = null
    private var localVoicesById: Map<String, Voice> = emptyMap()

    private var mediaPlayer: MediaPlayer? = null
    private var playJob: Job? = null
    private var previewJob: Job? = null
    private var sleepJob: Job? = null

    private var currentSentences: List<TtsSentence> = emptyList()
    private var currentIndex: Int = 0
    private var currentSettings: TtsSettings = TtsSettings()
    private var onChapterFinishedCallback: (() -> Unit)? = null
    private var suppressNextLocalDone: Boolean = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(EdgeNeuralTtsEngine.neuralVoices)
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _activeSentence = MutableStateFlow<TtsSentence?>(null)
    val activeSentence: StateFlow<TtsSentence?> = _activeSentence.asStateFlow()

    private val _sleepRemainingSeconds = MutableStateFlow(0)
    val sleepRemainingSeconds: StateFlow<Int> = _sleepRemainingSeconds.asStateFlow()

    private val _downloadedPacks = MutableStateFlow<Set<String>>(sherpaEngine.getDownloadedPackIds())
    val downloadedPacks: StateFlow<Set<String>> = _downloadedPacks.asStateFlow()

    private val _packDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val packDownloadProgress: StateFlow<Map<String, Float>> = _packDownloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initLocalEngine()
    }

    private fun initLocalEngine() {
        runCatching {
            localTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = localTts ?: return@TextToSpeech
                    val langRes = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.CHINESE)
                    }
                    refreshVoices(engine)
                    setupLocalListener(engine)
                }
            }
        }
    }

    private fun refreshVoices(@Suppress("UNUSED_PARAMETER") engine: TextToSpeech) {
        // Keep system voices internal only for ultimate emergency fallback; do not expose them in AI voice list.
        localVoicesById = emptyMap()
        _availableVoices.value = EdgeNeuralTtsEngine.neuralVoices
    }

    private fun setupLocalListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == "preview") return
                val idx = utteranceId?.toIntOrNull() ?: return
                scope.launch {
                    currentIndex = idx
                    _activeSentence.value = currentSentences.getOrNull(idx)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "preview") return
                scope.launch {
                    if (suppressNextLocalDone) {
                        suppressNextLocalDone = false
                        return@launch
                    }
                    if (!_isPlaying.value || _isPaused.value) return@launch
                    advanceToNextLocalSentence()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "preview") return
                scope.launch {
                    if (!_isPlaying.value || _isPaused.value) return@launch
                    advanceToNextLocalSentence()
                }
            }
        })
    }

    private fun advanceToNextLocalSentence() {
        val next = currentIndex + 1
        if (next < currentSentences.size) {
            speakFromIndex(next)
        } else {
            if (currentSettings.sleepTimerMinutes == -1) {
                stop()
            } else {
                onChapterFinishedCallback?.invoke()
            }
        }
    }

    suspend fun downloadOfflinePack(packId: String): Result<Unit> {
        if (_packDownloadProgress.value.containsKey(packId)) {
            return Result.success(Unit)
        }
        return runCatching {
            _packDownloadProgress.value = _packDownloadProgress.value + (packId to 0f)
            sherpaEngine.downloadAndInstallPack(packId) { p ->
                _packDownloadProgress.value = _packDownloadProgress.value + (packId to p)
            }
            _downloadedPacks.value = sherpaEngine.getDownloadedPackIds()
        }.also {
            _packDownloadProgress.value = _packDownloadProgress.value - packId
        }
    }

    fun startReading(
        sentences: List<TtsSentence>,
        startIndex: Int,
        settings: TtsSettings,
        onChapterCompleted: () -> Unit
    ) {
        if (sentences.isEmpty()) return
        previewJob?.cancel()
        playJob?.cancel()
        releaseAllAudio()

        currentSentences = sentences
        currentIndex = startIndex.coerceIn(0, sentences.lastIndex)
        currentSettings = settings
        onChapterFinishedCallback = onChapterCompleted
        _isPlaying.value = true
        _isPaused.value = false
        _errorMessage.value = null

        val activeVoice = resolveVoice(settings.voiceId)
        TtsPlaybackService.startOrUpdate(appContext, "正在使用「${activeVoice.displayLabel}」朗读")
        startSleepTimerIfNeeded(settings.sleepTimerMinutes)
        speakFromIndex(currentIndex)
    }

    fun continueNextChapter(sentences: List<TtsSentence>) {
        if (!_isPlaying.value || sentences.isEmpty()) return
        currentSentences = sentences
        currentIndex = 0
        _isPaused.value = false
        speakFromIndex(0)
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPaused.value = true
        playJob?.cancel()
        releaseAllAudio()
        TtsPlaybackService.startOrUpdate(appContext, "AI 朗读已暂停")
    }

    fun resume() {
        if (!_isPlaying.value || !_isPaused.value) return
        _isPaused.value = false
        val activeVoice = resolveVoice(currentSettings.voiceId)
        TtsPlaybackService.startOrUpdate(appContext, "正在使用「${activeVoice.displayLabel}」朗读")
        speakFromIndex(currentIndex)
    }

    fun stop() {
        sleepJob?.cancel()
        sleepJob = null
        previewJob?.cancel()
        playJob?.cancel()
        releaseAllAudio()
        TtsPlaybackService.stop(appContext)
        _sleepRemainingSeconds.value = 0
        _isPlaying.value = false
        _isPaused.value = false
        _activeSentence.value = null
    }

    fun updateLiveSettings(newSettings: TtsSettings) {
        val timerChanged = newSettings.sleepTimerMinutes != currentSettings.sleepTimerMinutes
        val audioChanged = newSettings.speechRate != currentSettings.speechRate ||
            newSettings.voiceId != currentSettings.voiceId
        currentSettings = newSettings

        if (timerChanged && _isPlaying.value) {
            startSleepTimerIfNeeded(newSettings.sleepTimerMinutes)
        }

        if (audioChanged && _isPlaying.value && !_isPaused.value && currentSentences.isNotEmpty()) {
            val activeVoice = resolveVoice(newSettings.voiceId)
            TtsPlaybackService.startOrUpdate(appContext, "正在使用「${activeVoice.displayLabel}」朗读")
            playJob?.cancel()
            releaseAllAudio()
            speakFromIndex(currentIndex)
        }
    }

    private fun resolveVoice(voiceId: String): TtsVoiceOption {
        return _availableVoices.value.find { it.id == voiceId }
            ?: EdgeNeuralTtsEngine.neuralVoices.first()
    }

    /**
     * Unified voice routing for BOTH previewVoice and speakFromIndex:
     * 1) If user downloaded this voice's offline pack -> use that downloaded offline pack.
     * 2) Else (including built-in 云希 when online) -> use expressive EdgeNeuralTtsEngine with gapless pre-prepared MediaPlayer,
     *    falling back seamlessly to built-in Kokoro FP32 云希 when offline.
     */
    fun previewVoice(settings: TtsSettings) {
        val sampleText = "清晨的微风拂过书页，杨凌合上手中的古籍，缓缓走向窗前，轻声说道，这一章的故事才刚刚开始。"
        previewJob?.cancel()
        releaseAllAudio()

        val selectedOption = resolveVoice(settings.voiceId)
        val packId = selectedOption.offlinePackId

        if (packId != null && sherpaEngine.isPackDownloaded(packId)) {
            previewJob = scope.launch {
                runCatching {
                    val audio = sherpaEngine.synthesizeDownloadedToPcm(
                        packId = packId,
                        sid = selectedOption.offlineSid,
                        rawText = sampleText,
                        speed = settings.speechRate
                    )
                    sherpaEngine.playStreamingPcm(audio)
                }.onFailure {
                    if (it !is CancellationException) {
                        previewBuiltinYunxiFallback(sampleText, settings)
                    }
                }
            }
            return
        }

        previewJob = scope.launch {
            try {
                val safeRateKey = "%.2f".format(Locale.US, settings.speechRate)
                val cacheKey = "${selectedOption.engineVoiceName}_$safeRateKey.mp3"
                val cachedFile = File(previewCacheDir, cacheKey)
                val mp3Bytes = withContext(Dispatchers.IO) {
                    if (cachedFile.exists() && cachedFile.length() > 1024L) {
                        cachedFile.readBytes()
                    } else {
                        EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                            text = sampleText,
                            voiceName = selectedOption.engineVoiceName,
                            speechRate = settings.speechRate
                        ).also { bytes ->
                            if (bytes.size > 1024) runCatching { cachedFile.writeBytes(bytes) }
                        }
                    }
                }
                playMp3Blocking(mp3Bytes)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                previewBuiltinYunxiFallback(sampleText, settings)
            }
        }
    }

    private suspend fun previewBuiltinYunxiFallback(sampleText: String, settings: TtsSettings) {
        if (sherpaEngine.isBuiltinAvailable()) {
            runCatching {
                val audio = sherpaEngine.synthesizeBuiltinToPcm(
                    sid = SherpaOfflineTtsEngine.YUNXI_BUILTIN_SID,
                    rawText = sampleText,
                    speed = settings.speechRate
                )
                sherpaEngine.playStreamingPcm(audio)
            }.onFailure {
                if (it !is CancellationException) {
                    speakLocally(sampleText, settings, "preview")
                }
            }
        } else {
            speakLocally(sampleText, settings, "preview")
        }
    }

    private data class PreparedClip(
        val file: File,
        val player: MediaPlayer
    )

    private fun speakFromIndex(startIdx: Int) {
        val selectedOption = resolveVoice(currentSettings.voiceId)
        val packId = selectedOption.offlinePackId

        if (packId != null && sherpaEngine.isPackDownloaded(packId)) {
            speakOfflineLoopWithLookahead(startIdx) { sentence ->
                sherpaEngine.synthesizeDownloadedToPcm(
                    packId = packId,
                    sid = selectedOption.offlineSid,
                    rawText = sentence.text,
                    speed = currentSettings.speechRate
                )
            }
            return
        }

        // Online Edge Neural TTS with 4-sentence lookahead prefetch + pre-prepared MediaPlayer handoff
        // and built-in Kokoro FP32 云希 offline fallback when offline.
        playJob?.cancel()
        playJob = scope.launch {
            var idx = startIdx
            val prefetched = HashMap<Int, Deferred<ByteArray>>()
            val pendingClips = java.util.Collections.synchronizedList(mutableListOf<PreparedClip>())
            var nextClipDeferred: Deferred<PreparedClip>? = null

            fun enqueuePrefetch(targetIdx: Int) {
                if (targetIdx >= currentSentences.size || prefetched.containsKey(targetIdx)) return
                val targetSentence = currentSentences[targetIdx]
                prefetched[targetIdx] = async(Dispatchers.IO) {
                    EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                        text = targetSentence.text,
                        voiceName = selectedOption.engineVoiceName,
                        speechRate = currentSettings.speechRate
                    )
                }
            }

            fun prepareClipAsync(mp3Deferred: Deferred<ByteArray>): Deferred<PreparedClip> =
                async(Dispatchers.IO) {
                    val bytes = mp3Deferred.await()
                    val tempFile = File(appContext.cacheDir, "tts_${UUID.randomUUID()}.mp3").apply {
                        writeBytes(bytes)
                    }
                    try {
                        val mp = MediaPlayer()
                        mp.setDataSource(tempFile.absolutePath)
                        mp.prepare()
                        PreparedClip(tempFile, mp).also { pendingClips.add(it) }
                    } catch (t: Throwable) {
                        runCatching { tempFile.delete() }
                        throw t
                    }
                }

            fun cleanupPending() {
                prefetched.values.forEach { it.cancel() }
                prefetched.clear()
                nextClipDeferred?.cancel()
                nextClipDeferred = null
                synchronized(pendingClips) {
                    pendingClips.forEach { clip ->
                        runCatching { clip.player.release() }
                        runCatching { clip.file.delete() }
                    }
                    pendingClips.clear()
                }
            }

            try {
                while (idx < currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    val sentence = currentSentences[idx]
                    currentIndex = idx
                    _activeSentence.value = sentence

                    if (nextClipDeferred == null) enqueuePrefetch(idx)
                    enqueuePrefetch(idx + 1)
                    enqueuePrefetch(idx + 2)
                    enqueuePrefetch(idx + 3)

                    val currentClipDeferred = nextClipDeferred ?: prepareClipAsync(prefetched.remove(idx)!!)
                    nextClipDeferred = if (idx + 1 < currentSentences.size) {
                        prepareClipAsync(prefetched.remove(idx + 1)!!)
                    } else {
                        null
                    }

                    try {
                        val clip = currentClipDeferred.await()
                        pendingClips.remove(clip)
                        if (!_isPlaying.value || _isPaused.value) {
                            runCatching { clip.player.release() }
                            runCatching { clip.file.delete() }
                            break
                        }
                        playPreparedClipBlocking(clip)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        // Online synthesis failed after retries -> fall back to built-in offline Yunxi smoothly
                        cleanupPending()
                        if (sherpaEngine.isBuiltinAvailable()) {
                            speakOfflineLoopWithLookahead(idx) { fallbackSentence ->
                                sherpaEngine.synthesizeBuiltinToPcm(
                                    sid = SherpaOfflineTtsEngine.YUNXI_BUILTIN_SID,
                                    rawText = fallbackSentence.text,
                                    speed = currentSettings.speechRate
                                )
                            }
                        } else {
                            speakSentenceLocallyFallback(idx)
                        }
                        return@launch
                    }

                    if (!_isPlaying.value || _isPaused.value) break
                    idx++
                }

                if (idx >= currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    if (currentSettings.sleepTimerMinutes == -1) stop() else onChapterFinishedCallback?.invoke()
                }
            } finally {
                cleanupPending()
            }
        }
    }

    private suspend fun playPreparedClipBlocking(clip: PreparedClip) {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val resumed = AtomicBoolean(false)
                releaseMediaPlayer()
                val mp = clip.player
                mediaPlayer = mp
                mp.setOnCompletionListener {
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.success(Unit))
                    }
                }
                mp.setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.failure(IllegalStateException("音频播放失败")))
                    }
                    true
                }
                runCatching { mp.start() }.onFailure { err ->
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.failure(err))
                    }
                }
                cont.invokeOnCancellation {
                    resumed.set(true)
                    releaseMediaPlayer()
                }
            }
        } finally {
            runCatching { clip.file.delete() }
        }
    }

    private fun speakOfflineLoopWithLookahead(
        startIdx: Int,
        synthesizer: suspend (TtsSentence) -> SherpaOfflineTtsEngine.SynthesizedAudio
    ) {
        playJob?.cancel()
        playJob = scope.launch {
            var idx = startIdx
            var nextDeferred: Deferred<SherpaOfflineTtsEngine.SynthesizedAudio>? = null

            try {
                while (idx < currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    val sentence = currentSentences[idx]
                    currentIndex = idx
                    _activeSentence.value = sentence

                    val currentDeferred = nextDeferred ?: async(Dispatchers.IO) {
                        synthesizer(sentence)
                    }

                    nextDeferred = if (idx + 1 < currentSentences.size) {
                        val nextSentence = currentSentences[idx + 1]
                        async(Dispatchers.IO) {
                            synthesizer(nextSentence)
                        }
                    } else {
                        null
                    }

                    try {
                        val audio = currentDeferred.await()
                        if (!_isPlaying.value || _isPaused.value) break
                        if (audio.pcm.isNotEmpty()) {
                            sherpaEngine.playStreamingPcm(audio)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        // Skip only failed chunk gracefully instead of mutating user's selected voice mid-book
                    }

                    if (!_isPlaying.value || _isPaused.value) break
                    idx++
                }

                if (idx >= currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    if (currentSettings.sleepTimerMinutes == -1) stop() else onChapterFinishedCallback?.invoke()
                }
            } finally {
                nextDeferred?.cancel()
            }
        }
    }

    private fun speakSentenceLocallyFallback(index: Int) {
        val sentence = currentSentences.getOrNull(index) ?: return
        currentIndex = index
        _activeSentence.value = sentence
        speakLocally(sentence.text, currentSettings, index.toString())
    }

    private fun speakLocally(text: String, settings: TtsSettings, utteranceId: String) {
        val engine = localTts ?: return
        runCatching {
            engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2.5f))
            localVoicesById[settings.voiceId]?.let { v ->
                runCatching { engine.voice = v }
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }
    }

    private suspend fun playMp3Blocking(mp3Bytes: ByteArray) {
        val tempFile = withContext(Dispatchers.IO) {
            File(appContext.cacheDir, "tts_${UUID.randomUUID()}.mp3").apply {
                writeBytes(mp3Bytes)
            }
        }
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val resumed = AtomicBoolean(false)
                releaseMediaPlayer()
                val mp = MediaPlayer()
                mediaPlayer = mp
                mp.setDataSource(tempFile.absolutePath)
                mp.setOnPreparedListener {
                    runCatching { it.start() }.onFailure { err ->
                        releaseMediaPlayer()
                        if (resumed.compareAndSet(false, true) && cont.isActive) {
                            cont.resumeWith(Result.failure(err))
                        }
                    }
                }
                mp.setOnCompletionListener {
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.success(Unit))
                    }
                }
                mp.setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.failure(IllegalStateException("音频播放失败")))
                    }
                    true
                }
                runCatching { mp.prepareAsync() }.onFailure { err ->
                    releaseMediaPlayer()
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resumeWith(Result.failure(err))
                    }
                }
                cont.invokeOnCancellation {
                    resumed.set(true)
                    releaseMediaPlayer()
                }
            }
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun releaseAllAudio() {
        sherpaEngine.stopPlayback()
        releaseMediaPlayer()
        suppressNextLocalDone = true
        runCatching { localTts?.stop() }
    }

    private fun releaseMediaPlayer() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            if (mp.isPlaying) mp.stop()
        }
        runCatching { mp.reset() }
        runCatching { mp.release() }
    }

    private fun startSleepTimerIfNeeded(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepRemainingSeconds.value = 0
            return
        }
        val totalSec = minutes * 60
        _sleepRemainingSeconds.value = totalSec
        sleepJob = scope.launch {
            var remaining = totalSec
            while (remaining > 0 && _isPlaying.value) {
                delay(1000L)
                if (!_isPaused.value) {
                    remaining--
                    _sleepRemainingSeconds.value = remaining
                }
            }
            if (remaining <= 0 && _isPlaying.value) {
                stop()
            }
        }
    }

    fun shutdown() {
        sleepJob?.cancel()
        previewJob?.cancel()
        playJob?.cancel()
        releaseAllAudio()
        TtsPlaybackService.stop(appContext)
        sherpaEngine.release()
        runCatching { localTts?.shutdown() }
        localTts = null
    }

    companion object {
        private val sentenceBreakChars = setOf('。', '！', '？', '；', '…', '\n', '.', '!', '?')

        fun splitChapterIntoSentences(
            paragraphs: List<TxtBookEngine.ChapterParagraph>,
            chapterIndex: Int
        ): List<TtsSentence> {
            val result = mutableListOf<TtsSentence>()
            for (para in paragraphs) {
                val text = para.text
                var segStart = 0
                var i = 0
                while (i < text.length) {
                    val ch = text[i]
                    val isEnd = (ch in sentenceBreakChars && (i - segStart >= 32 || ch == '\n')) ||
                        (i - segStart >= 100 && (ch == '，' || ch == ','))
                    if (isEnd || i == text.lastIndex) {
                        var end = i + 1
                        while (end < text.length && text[end] in setOf('”', '’', '」', '』', ')', '）', '"', '\'')) {
                            end++
                        }
                        val sub = text.substring(segStart, end).trim()
                        if (sub.any { it.isLetterOrDigit() }) {
                            result.add(
                                TtsSentence(
                                    index = result.size,
                                    chapterIndex = chapterIndex,
                                    startOffset = para.charStartOffset + segStart,
                                    endOffset = para.charStartOffset + end,
                                    text = sub
                                )
                            )
                        }
                        segStart = end
                        i = end
                        continue
                    }
                    i++
                }
            }
            return result
        }
    }
}
