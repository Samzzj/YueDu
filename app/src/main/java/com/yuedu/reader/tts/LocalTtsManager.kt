package com.yuedu.reader.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.yuedu.reader.engine.TxtBookEngine
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.model.ChapterInfo
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LocalTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val sherpaEngine = SherpaOfflineTtsEngine(appContext)
    val audioCache = TtsAudioCache(appContext)
    private val bookDownloadJobs = mutableMapOf<String, Job>()

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

    // Cross-chapter lookahead caches so chapter transitions play seamlessly with zero stall
    private var nextChapterSentences: List<TtsSentence> = emptyList()
    private val nextChapterPrefetched = HashMap<Int, Deferred<File>>()
    private var nextChapterOfflineDeferred: Deferred<SherpaOfflineTtsEngine.SynthesizedAudio>? = null

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

    private val _bookDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bookDownloadProgress: StateFlow<Map<String, Float>> = _bookDownloadProgress.asStateFlow()

    private val _cachedChapterCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cachedChapterCounts: StateFlow<Map<String, Int>> = _cachedChapterCounts.asStateFlow()

    private val _totalCacheBytes = MutableStateFlow(0L)
    val totalCacheBytes: StateFlow<Long> = _totalCacheBytes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initLocalEngine()
        scope.launch {
            _availableVoices.value = EdgeNeuralTtsEngine.fetchVpsVoices()
        }
        scope.launch(Dispatchers.IO) {
            _totalCacheBytes.value = audioCache.getTotalCacheBytes()
        }
    }

    fun refreshAudioCacheStats(settings: TtsSettings) {
        val voice = resolveVoice(settings.voiceId)
        scope.launch(Dispatchers.IO) {
            _cachedChapterCounts.value = audioCache.getAllCachedChapterCounts(voice.id, settings.speechRate)
            _totalCacheBytes.value = audioCache.getTotalCacheBytes()
        }
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

    fun cancelBookAudioDownload(bookId: String) {
        bookDownloadJobs.remove(bookId)?.cancel()
        _bookDownloadProgress.value = _bookDownloadProgress.value - bookId
    }

    fun clearAllCachedAudio(settings: TtsSettings) {
        bookDownloadJobs.values.forEach { it.cancel() }
        bookDownloadJobs.clear()
        _bookDownloadProgress.value = emptyMap()
        scope.launch(Dispatchers.IO) {
            audioCache.clearAll()
            _cachedChapterCounts.value = emptyMap()
            _totalCacheBytes.value = 0L
        }
    }

    suspend fun downloadBookChapters(
        book: BookItem,
        chapters: List<ChapterInfo>,
        startChapterIndex: Int,
        chapterCount: Int,
        settings: TtsSettings
    ): Result<Int> {
        if (chapters.isEmpty()) return Result.failure(IllegalStateException("书籍暂无章节"))
        if (bookDownloadJobs.containsKey(book.id)) return Result.success(0)

        val voice = resolveVoice(settings.voiceId)
        val alreadyCached = audioCache.getBookCachedChapters(book.id, voice.id, settings.speechRate)
        val safeStart = startChapterIndex.coerceIn(0, chapters.lastIndex)
        val candidates = (safeStart until chapters.size)
            .map { chapters[it] }
            .filterNot { it.index in alreadyCached }
            .take(chapterCount)
            .ifEmpty {
                chapters.filterNot { it.index in alreadyCached }.take(chapterCount)
            }

        if (candidates.isEmpty()) {
            refreshAudioCacheStats(settings)
            return Result.success(0)
        }

        return runCatching {
            val job = scope.launch(Dispatchers.IO) {
                val sem = Semaphore(3)
                var completedChapters = 0
                val totalChapters = candidates.size
                _bookDownloadProgress.value = _bookDownloadProgress.value + (book.id to 0.01f)

                for (ch in candidates) {
                    val paras = TxtBookEngine.readChapterParagraphs(File(book.filePath), ch)
                    val sentences = splitChapterIntoSentences(paras, ch.index)
                    val totalSents = sentences.size.coerceAtLeast(1)
                    var doneSents = 0

                    coroutineScope {
                        sentences.map { sent ->
                            async {
                                sem.withPermit {
                                    if (!audioCache.hasCachedClip(voice.engineVoiceName, settings.speechRate, sent.text)) {
                                        runCatching {
                                            val bytes = EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                                                text = sent.text,
                                                voiceName = voice.engineVoiceName,
                                                speechRate = settings.speechRate
                                            )
                                            audioCache.writeCachedClip(voice.engineVoiceName, settings.speechRate, sent.text, bytes)
                                        }.onFailure { err ->
                                            if (err is CancellationException) throw err
                                            if (err !is EdgeNeuralTtsEngine.EmptyAudioException) throw err
                                        }
                                    }
                                    synchronized(bookDownloadJobs) {
                                        doneSents++
                                        val prog = ((completedChapters + doneSents.toFloat() / totalSents) / totalChapters)
                                            .coerceIn(0.01f, 0.99f)
                                        _bookDownloadProgress.value = _bookDownloadProgress.value + (book.id to prog)
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    audioCache.markChapterCached(book.id, ch.index, voice.id, settings.speechRate)
                    completedChapters++
                    _cachedChapterCounts.value = audioCache.getAllCachedChapterCounts(voice.id, settings.speechRate)
                    _totalCacheBytes.value = audioCache.getTotalCacheBytes()
                }
            }
            bookDownloadJobs[book.id] = job
            job.join()
            candidates.size
        }.also {
            bookDownloadJobs.remove(book.id)
            _bookDownloadProgress.value = _bookDownloadProgress.value - book.id
            refreshAudioCacheStats(settings)
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
        clearNextChapterPreload()
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

    fun preloadNextChapter(sentences: List<TtsSentence>) {
        if (sentences == nextChapterSentences) return
        val firstCh = sentences.firstOrNull()?.chapterIndex
        if (firstCh != null && firstCh == nextChapterSentences.firstOrNull()?.chapterIndex && sentences.size == nextChapterSentences.size) {
            return
        }
        nextChapterPrefetched.values.forEach { it.cancel() }
        nextChapterPrefetched.clear()
        nextChapterOfflineDeferred?.cancel()
        nextChapterOfflineDeferred = null
        nextChapterSentences = sentences
    }

    private fun clearNextChapterPreload() {
        nextChapterPrefetched.values.forEach { it.cancel() }
        nextChapterPrefetched.clear()
        nextChapterOfflineDeferred?.cancel()
        nextChapterOfflineDeferred = null
        nextChapterSentences = emptyList()
    }

    fun continueNextChapter(sentences: List<TtsSentence>) {
        if (!_isPlaying.value || sentences.isEmpty()) return
        val matchesPreload = nextChapterSentences.isNotEmpty() &&
            sentences.firstOrNull()?.chapterIndex == nextChapterSentences.firstOrNull()?.chapterIndex
        val handedOffOnline = if (matchesPreload && nextChapterPrefetched.isNotEmpty()) {
            HashMap(nextChapterPrefetched).also { nextChapterPrefetched.clear() }
        } else {
            nextChapterPrefetched.values.forEach { it.cancel() }
            nextChapterPrefetched.clear()
            null
        }
        val handedOffOffline = if (matchesPreload) {
            nextChapterOfflineDeferred.also { nextChapterOfflineDeferred = null }
        } else {
            nextChapterOfflineDeferred?.cancel()
            nextChapterOfflineDeferred = null
            null
        }
        nextChapterSentences = emptyList()

        currentSentences = sentences
        currentIndex = 0
        _isPaused.value = false
        speakFromIndex(0, handedOffOnline, handedOffOffline)
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
        clearNextChapterPreload()
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
            clearNextChapterPreload()
            releaseAllAudio()
            speakFromIndex(currentIndex)
        }
    }

    private fun resolveVoice(voiceId: String): TtsVoiceOption {
        val mapped = if (voiceId == "matcha:baker") "edge:zh-CN-XiaoxiaoNeural" else voiceId
        return _availableVoices.value.find { it.id == mapped }
            ?: _availableVoices.value.firstOrNull()
            ?: EdgeNeuralTtsEngine.neuralVoices.first()
    }

    /**
     * Unified voice routing for preview, streaming playback, and downloaded offline chapter audio:
     * All use the exact same Edge ReadAloud MP3 clips from HK CN2 VPS (or local cache when downloaded/cached),
     * so previews and downloaded audio deliver 100% identical voice and emotion.
     */
    fun previewVoice(settings: TtsSettings) {
        val sampleText = "清晨的微风拂过书页，杨凌合上手中的古籍，缓缓走向窗前，轻声说道，这一章的故事才刚刚开始。"
        previewJob?.cancel()
        releaseAllAudio()

        val selectedOption = resolveVoice(settings.voiceId)

        previewJob = scope.launch {
            try {
                val mp3Bytes = withContext(Dispatchers.IO) {
                    audioCache.readCachedClip(selectedOption.engineVoiceName, settings.speechRate, sampleText)
                        ?: EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                            text = sampleText,
                            voiceName = selectedOption.engineVoiceName,
                            speechRate = settings.speechRate
                        ).also { bytes ->
                            audioCache.writeCachedClip(selectedOption.engineVoiceName, settings.speechRate, sampleText, bytes)
                            _totalCacheBytes.value = audioCache.getTotalCacheBytes()
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
        val player: MediaPlayer,
        val deleteFileOnFinish: Boolean = true
    )

    private suspend fun fetchOrLoadSentenceMp3(
        sentenceText: String,
        voiceName: String,
        speechRate: Float
    ): File {
        val existing = audioCache.clipFile(voiceName, speechRate, sentenceText)
        if (existing.exists() && existing.length() > 64L) {
            return existing
        }
        val bytes = EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
            text = sentenceText,
            voiceName = voiceName,
            speechRate = speechRate
        )
        return audioCache.writeCachedClip(voiceName, speechRate, sentenceText, bytes)
            ?: File(appContext.cacheDir, "tts_${UUID.randomUUID()}.mp3").apply { writeBytes(bytes) }
    }

    private fun speakFromIndex(
        startIdx: Int,
        initialOnlinePrefetched: Map<Int, Deferred<File>>? = null,
        initialOfflineFirst: Deferred<SherpaOfflineTtsEngine.SynthesizedAudio>? = null
    ) {
        val selectedOption = resolveVoice(currentSettings.voiceId)

        // Unified Edge ReadAloud pipeline:
        // 1) Local downloaded/cached Edge MP3 -> 2) HK CN2 VPS Edge gateway -> 3) Direct Bing Edge backup
        // Built-in Kokoro 云希 is only used silently if completely offline and chapter was not downloaded.
        playJob?.cancel()
        playJob = scope.launch {
            var idx = startIdx
            val prefetched = HashMap<Int, Deferred<File>>()
            if (initialOnlinePrefetched != null) {
                prefetched.putAll(initialOnlinePrefetched)
            }
            val pendingClips = java.util.Collections.synchronizedList(mutableListOf<PreparedClip>())
            var nextClipDeferred: Deferred<PreparedClip>? = null
            var consecutiveNetworkFailures = 0

            fun enqueuePrefetch(targetIdx: Int) {
                if (targetIdx >= currentSentences.size || prefetched.containsKey(targetIdx)) return
                val targetSentence = currentSentences[targetIdx]
                prefetched[targetIdx] = async(Dispatchers.IO) {
                    fetchOrLoadSentenceMp3(
                        sentenceText = targetSentence.text,
                        voiceName = selectedOption.engineVoiceName,
                        speechRate = currentSettings.speechRate
                    )
                }
            }

            fun enqueueNextChapterIfNeeded() {
                if (nextChapterSentences.isEmpty()) return
                val limit = minOf(3, nextChapterSentences.size)
                for (nIdx in 0 until limit) {
                    if (!nextChapterPrefetched.containsKey(nIdx)) {
                        val nextSentence = nextChapterSentences[nIdx]
                        nextChapterPrefetched[nIdx] = scope.async(Dispatchers.IO) {
                            fetchOrLoadSentenceMp3(
                                sentenceText = nextSentence.text,
                                voiceName = selectedOption.engineVoiceName,
                                speechRate = currentSettings.speechRate
                            )
                        }
                    }
                }
            }

            fun prepareClipAsync(fileDeferred: Deferred<File>): Deferred<PreparedClip> =
                async(Dispatchers.IO) {
                    val audioFile = fileDeferred.await()
                    val isTemp = audioFile.parentFile == appContext.cacheDir
                    try {
                        val mp = MediaPlayer()
                        mp.setDataSource(audioFile.absolutePath)
                        mp.prepare()
                        PreparedClip(audioFile, mp, deleteFileOnFinish = isTemp).also { pendingClips.add(it) }
                    } catch (t: Throwable) {
                        if (isTemp) runCatching { audioFile.delete() }
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
                        if (clip.deleteFileOnFinish) runCatching { clip.file.delete() }
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
                    for (ahead in 1..5) {
                        enqueuePrefetch(idx + ahead)
                    }
                    if (idx >= currentSentences.size - 4) {
                        enqueueNextChapterIfNeeded()
                    }

                    val currentClipDeferred = nextClipDeferred ?: prepareClipAsync(prefetched.remove(idx)!!)
                    nextClipDeferred = if (idx + 1 < currentSentences.size) {
                        prepareClipAsync(prefetched.remove(idx + 1)!!)
                    } else {
                        null
                    }

                    try {
                        val clip = currentClipDeferred.await()
                        pendingClips.remove(clip)
                        consecutiveNetworkFailures = 0
                        if (!_isPlaying.value || _isPaused.value) {
                            runCatching { clip.player.release() }
                            if (clip.deleteFileOnFinish) runCatching { clip.file.delete() }
                            break
                        }
                        playPreparedClipBlocking(clip)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: EdgeNeuralTtsEngine.EmptyAudioException) {
                        // Single unpronounceable chunk: skip gracefully without blowing away lookahead cache
                    } catch (_: Exception) {
                        consecutiveNetworkFailures++
                        if (consecutiveNetworkFailures < 2) {
                            if (sherpaEngine.isBuiltinAvailable()) {
                                runCatching {
                                    val audio = sherpaEngine.synthesizeBuiltinToPcm(
                                        sid = SherpaOfflineTtsEngine.YUNXI_BUILTIN_SID,
                                        rawText = sentence.text,
                                        speed = currentSettings.speechRate
                                    )
                                    if (audio.pcm.isNotEmpty() && _isPlaying.value && !_isPaused.value) {
                                        sherpaEngine.playStreamingPcm(audio)
                                    }
                                }
                            }
                        } else {
                            cleanupPending()
                            clearNextChapterPreload()
                            if (sherpaEngine.isBuiltinAvailable()) {
                                speakOfflineLoopWithLookahead(idx, initialOfflineFirst) { fallbackSentence ->
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
            if (clip.deleteFileOnFinish) runCatching { clip.file.delete() }
        }
    }

    private fun speakOfflineLoopWithLookahead(
        startIdx: Int,
        initialFirstDeferred: Deferred<SherpaOfflineTtsEngine.SynthesizedAudio>? = null,
        synthesizer: suspend (TtsSentence) -> SherpaOfflineTtsEngine.SynthesizedAudio
    ) {
        playJob?.cancel()
        playJob = scope.launch {
            var idx = startIdx
            var nextDeferred: Deferred<SherpaOfflineTtsEngine.SynthesizedAudio>? = initialFirstDeferred

            try {
                while (idx < currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    val sentence = currentSentences[idx]
                    currentIndex = idx
                    _activeSentence.value = sentence

                    val currentDeferred = nextDeferred ?: async(Dispatchers.IO) {
                        synthesizer(sentence)
                    }

                    if (idx + 1 < currentSentences.size) {
                        val nextSentence = currentSentences[idx + 1]
                        nextDeferred = async(Dispatchers.IO) {
                            synthesizer(nextSentence)
                        }
                    } else {
                        nextDeferred = null
                        if (nextChapterSentences.isNotEmpty() && nextChapterOfflineDeferred == null) {
                            val firstNext = nextChapterSentences.first()
                            nextChapterOfflineDeferred = scope.async(Dispatchers.IO) {
                                synthesizer(firstNext)
                            }
                        }
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
        private val closingPunctChars = setOf('”', '’', '」', '』', ')', '）', '"', '\'')

        private data class RawSegment(
            val startOffset: Int,
            val endOffset: Int,
            val text: String,
            val isTitle: Boolean
        )

        fun splitChapterIntoSentences(
            paragraphs: List<TxtBookEngine.ChapterParagraph>,
            chapterIndex: Int
        ): List<TtsSentence> {
            val rawSegments = mutableListOf<RawSegment>()
            for (para in paragraphs) {
                val text = para.text
                var segStart = 0
                var i = 0
                while (i < text.length) {
                    val ch = text[i]
                    val segLen = i - segStart
                    val isEnd = (ch in sentenceBreakChars && (segLen >= 20 || ch == '\n')) ||
                        (segLen >= 85 && (ch == '，' || ch == ',')) ||
                        (segLen >= 125)
                    if (isEnd || i == text.lastIndex) {
                        var end = i + 1
                        while (end < text.length && text[end] in closingPunctChars) {
                            end++
                        }
                        val sub = text.substring(segStart, end).trim()
                        if (sub.any { it.isLetterOrDigit() }) {
                            rawSegments.add(
                                RawSegment(
                                    startOffset = para.charStartOffset + segStart,
                                    endOffset = para.charStartOffset + end,
                                    text = sub,
                                    isTitle = para.isTitle
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

            if (rawSegments.isEmpty()) return emptyList()

            // Coalesce consecutive short non-title lines/paragraphs so dialogue runs don't spam per-line network requests
            val result = mutableListOf<TtsSentence>()
            var bufStart = -1
            var bufEnd = -1
            var bufCount = 0
            val bufText = StringBuilder()

            fun flushBuffer() {
                if (bufText.isNotEmpty()) {
                    result.add(
                        TtsSentence(
                            index = result.size,
                            chapterIndex = chapterIndex,
                            startOffset = bufStart,
                            endOffset = bufEnd,
                            text = bufText.toString()
                        )
                    )
                    bufText.clear()
                    bufStart = -1
                    bufEnd = -1
                    bufCount = 0
                }
            }

            for (seg in rawSegments) {
                if (seg.isTitle) {
                    flushBuffer()
                    result.add(
                        TtsSentence(
                            index = result.size,
                            chapterIndex = chapterIndex,
                            startOffset = seg.startOffset,
                            endOffset = seg.endOffset,
                            text = seg.text
                        )
                    )
                    continue
                }

                if (bufText.isEmpty()) {
                    bufStart = seg.startOffset
                    bufEnd = seg.endOffset
                    bufText.append(seg.text)
                    bufCount = 1
                } else if (bufCount < 3 && bufText.length < 48 && bufText.length + 1 + seg.text.length <= 84) {
                    bufText.append('\n').append(seg.text)
                    bufEnd = seg.endOffset
                    bufCount++
                } else {
                    flushBuffer()
                    bufStart = seg.startOffset
                    bufEnd = seg.endOffset
                    bufText.append(seg.text)
                    bufCount = 1
                }
            }
            flushBuffer()
            return result
        }
    }
}
