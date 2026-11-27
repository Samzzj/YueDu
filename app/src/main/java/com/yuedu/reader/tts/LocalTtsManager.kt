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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class LocalTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var localTts: TextToSpeech? = null
    private var localVoicesById: Map<String, Voice> = emptyMap()

    private var mediaPlayer: MediaPlayer? = null
    private var edgePlayJob: Job? = null
    private var previewJob: Job? = null
    private var sleepJob: Job? = null

    private var currentSentences: List<TtsSentence> = emptyList()
    private var currentIndex: Int = 0
    private var currentSettings: TtsSettings = TtsSettings()
    private var onChapterFinishedCallback: (() -> Unit)? = null
    private var suppressNextLocalDone: Boolean = false

    private val _isReady = MutableStateFlow(true)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initLocalEngine()
    }

    private fun initLocalEngine() {
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

    /**
     * Combines expressive neural voices with strictly deduplicated Simplified Chinese local voices.
     * Never shows redundant regional labels (简中/繁中) or duplicate voice entries.
     */
    private fun refreshVoices(engine: TextToSpeech) {
        val rawVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val simplifiedOnly = rawVoices.filter { v ->
            val lang = v.locale?.language?.lowercase().orEmpty()
            val country = v.locale?.country?.uppercase().orEmpty()
            (lang == "zh" || lang == "cmn") && (country.isEmpty() || country == "CN" || country == "CHS")
        }

        // Deduplicate by normalized name + offline status so OEM duplicate entries collapse cleanly
        val seenKeys = HashSet<String>()
        val dedupedLocal = mutableListOf<Pair<String, Voice>>()
        val localOptions = mutableListOf<TtsVoiceOption>()

        for (v in simplifiedOnly) {
            val rawKey = "${v.name.lowercase()}_${v.isNetworkConnectionRequired}"
            if (!seenKeys.add(rawKey)) continue
            val idx = localOptions.size + 1
            val uniqueId = "local_${idx}_${v.name.ifBlank { "default" }}"
            dedupedLocal.add(uniqueId to v)
            localOptions.add(
                TtsVoiceOption(
                    id = uniqueId,
                    engineVoiceName = v.name,
                    displayLabel = "本机离线音色 $idx",
                    styleTag = if (v.isNetworkConnectionRequired) "系统音色 · 需联网" else "系统本地 · 无网可用",
                    isNeural = false
                )
            )
            if (localOptions.size >= 4) break
        }

        localVoicesById = dedupedLocal.toMap()
        _availableVoices.value = EdgeNeuralTtsEngine.neuralVoices + localOptions
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
                    advanceToNextSentence()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "preview") return
                scope.launch {
                    if (!_isPlaying.value || _isPaused.value) return@launch
                    advanceToNextSentence()
                }
            }
        })
    }

    private fun advanceToNextSentence() {
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

    fun startReading(
        sentences: List<TtsSentence>,
        startIndex: Int,
        settings: TtsSettings,
        onChapterCompleted: () -> Unit
    ) {
        if (sentences.isEmpty()) return
        previewJob?.cancel()
        edgePlayJob?.cancel()
        releaseMediaPlayer()

        currentSentences = sentences
        currentIndex = startIndex.coerceIn(0, sentences.lastIndex)
        currentSettings = settings
        onChapterFinishedCallback = onChapterCompleted
        _isPlaying.value = true
        _isPaused.value = false
        _errorMessage.value = null

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
        edgePlayJob?.cancel()
        releaseMediaPlayer()
        suppressNextLocalDone = true
        localTts?.stop()
    }

    fun resume() {
        if (!_isPlaying.value || !_isPaused.value) return
        _isPaused.value = false
        speakFromIndex(currentIndex)
    }

    fun stop() {
        sleepJob?.cancel()
        sleepJob = null
        previewJob?.cancel()
        edgePlayJob?.cancel()
        releaseMediaPlayer()
        _sleepRemainingSeconds.value = 0
        _isPlaying.value = false
        _isPaused.value = false
        _activeSentence.value = null
        suppressNextLocalDone = true
        localTts?.stop()
    }

    fun updateLiveSettings(newSettings: TtsSettings) {
        val timerChanged = newSettings.sleepTimerMinutes != currentSettings.sleepTimerMinutes
        val audioChanged = newSettings.speechRate != currentSettings.speechRate ||
            newSettings.pitch != currentSettings.pitch ||
            newSettings.voiceId != currentSettings.voiceId
        currentSettings = newSettings

        if (timerChanged && _isPlaying.value) {
            startSleepTimerIfNeeded(newSettings.sleepTimerMinutes)
        }

        if (audioChanged && _isPlaying.value && !_isPaused.value && currentSentences.isNotEmpty()) {
            edgePlayJob?.cancel()
            releaseMediaPlayer()
            suppressNextLocalDone = true
            localTts?.stop()
            speakFromIndex(currentIndex)
        }
    }

    fun previewVoice(settings: TtsSettings) {
        val sampleText = "你好，欢迎来到悦读，现在为你朗读精彩小说。"
        previewJob?.cancel()
        releaseMediaPlayer()
        suppressNextLocalDone = true
        localTts?.stop()

        val selectedOption = _availableVoices.value.find { it.id == settings.voiceId }
            ?: EdgeNeuralTtsEngine.neuralVoices.first()

        if (selectedOption.isNeural) {
            previewJob = scope.launch {
                try {
                    val mp3 = withContext(Dispatchers.IO) {
                        EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                            text = sampleText,
                            voiceName = selectedOption.engineVoiceName,
                            speechRate = settings.speechRate,
                            pitch = settings.pitch
                        )
                    }
                    playMp3Blocking(mp3, "preview")
                } catch (_: CancellationException) {
                    // Ignore
                } catch (_: Exception) {
                    speakLocally(sampleText, settings, "preview")
                }
            }
        } else {
            speakLocally(sampleText, settings, "preview")
        }
    }

    private fun speakFromIndex(startIdx: Int) {
        val selectedOption = _availableVoices.value.find { it.id == currentSettings.voiceId }
            ?: EdgeNeuralTtsEngine.neuralVoices.first()

        if (selectedOption.isNeural) {
            edgePlayJob?.cancel()
            edgePlayJob = scope.launch {
                var idx = startIdx
                var prefetched: Deferred<ByteArray>? = null

                while (idx < currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    val sentence = currentSentences[idx]
                    currentIndex = idx
                    _activeSentence.value = sentence

                    val currentDeferred = prefetched ?: async(Dispatchers.IO) {
                        EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                            text = sentence.text,
                            voiceName = selectedOption.engineVoiceName,
                            speechRate = currentSettings.speechRate,
                            pitch = currentSettings.pitch
                        )
                    }

                    // Pre-fetch next sentence concurrently for gapless transition
                    prefetched = if (idx + 1 < currentSentences.size) {
                        val nextSentence = currentSentences[idx + 1]
                        async(Dispatchers.IO) {
                            EdgeNeuralTtsEngine.synthesizeToMp3Bytes(
                                text = nextSentence.text,
                                voiceName = selectedOption.engineVoiceName,
                                speechRate = currentSettings.speechRate,
                                pitch = currentSettings.pitch
                            )
                        }
                    } else null

                    try {
                        val mp3Bytes = currentDeferred.await()
                        if (!_isPlaying.value || _isPaused.value) break
                        playMp3Blocking(mp3Bytes, "tts_$idx")
                    } catch (ce: CancellationException) {
                        prefetched?.cancel()
                        throw ce
                    } catch (_: Exception) {
                        prefetched?.cancel()
                        prefetched = null
                        // Seamless fallback to local system TTS when offline or network fails
                        speakSentenceLocallyFallback(idx)
                        return@launch
                    }

                    if (!_isPlaying.value || _isPaused.value) break
                    idx++
                }

                if (idx >= currentSentences.size && _isPlaying.value && !_isPaused.value) {
                    if (currentSettings.sleepTimerMinutes == -1) {
                        stop()
                    } else {
                        onChapterFinishedCallback?.invoke()
                    }
                }
            }
        } else {
            speakSentenceLocallyFallback(startIdx)
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
        engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2.5f))
        engine.setPitch(settings.pitch.coerceIn(0.6f, 1.5f))
        localVoicesById[settings.voiceId]?.let { v ->
            runCatching { engine.voice = v }
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
    }

    private suspend fun playMp3Blocking(mp3Bytes: ByteArray, tag: String) {
        val tempFile = withContext(Dispatchers.IO) {
            File(appContext.cacheDir, "tts_audio_$tag.mp3").apply {
                writeBytes(mp3Bytes)
            }
        }
        try {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                releaseMediaPlayer()
                val mp = MediaPlayer()
                mediaPlayer = mp
                mp.setDataSource(tempFile.absolutePath)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener {
                    releaseMediaPlayer()
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
                mp.setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("音频播放失败")))
                    true
                }
                mp.prepareAsync()
                cont.invokeOnCancellation {
                    releaseMediaPlayer()
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
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
        edgePlayJob?.cancel()
        releaseMediaPlayer()
        runCatching {
            localTts?.stop()
            localTts?.shutdown()
        }
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
                    val isEnd = ch in sentenceBreakChars || (i - segStart >= 110 && (ch == '，' || ch == ','))
                    if (isEnd || i == text.lastIndex) {
                        var end = i + 1
                        while (end < text.length && text[end] in setOf('”', '’', '」', '』', ')', '）', '"', '\'')) {
                            end++
                        }
                        val sub = text.substring(segStart, end).trim()
                        if (sub.isNotEmpty()) {
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
