package com.yuedu.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.yuedu.reader.engine.TxtBookEngine
import com.yuedu.reader.model.TtsSentence
import com.yuedu.reader.model.TtsSettings
import com.yuedu.reader.model.TtsVoiceOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class LocalTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private var voicesMap: Map<String, Voice> = emptyMap()

    private var currentSentences: List<TtsSentence> = emptyList()
    private var currentIndex: Int = 0
    private var currentSettings: TtsSettings = TtsSettings()
    private var onChapterFinishedCallback: (() -> Unit)? = null
    private var sleepJob: Job? = null
    private var suppressNextDone: Boolean = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _activeSentence = MutableStateFlow<TtsSentence?>(null)
    val activeSentence: StateFlow<TtsSentence?> = _activeSentence.asStateFlow()

    private val _sleepRemainingSeconds = MutableStateFlow(0)
    val sleepRemainingSeconds: StateFlow<Int> = _sleepRemainingSeconds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initEngine()
    }

    private fun initEngine() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val langRes = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.CHINESE)
                }
                refreshVoices(engine)
                setupListener(engine)
                _isReady.value = true
                _errorMessage.value = null
            } else {
                _isReady.value = false
                _errorMessage.value = "系统本地 TTS 引擎初始化失败，请检查系统「文字转语音」设置"
            }
        }
    }

    private fun refreshVoices(engine: TextToSpeech) {
        val rawVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val filtered = rawVoices
            .filter { v ->
                val lang = v.locale?.language?.lowercase().orEmpty()
                lang == "zh" || lang == "cmn" || lang == "yue" || lang == "en"
            }
            .sortedWith(
                compareBy<Voice>(
                    { if (it.locale?.language?.lowercase() == "zh") 0 else 1 },
                    { if (!it.isNetworkConnectionRequired) 0 else 1 },
                    { it.name }
                )
            )

        voicesMap = filtered.associateBy { it.name }
        val options = filtered.mapIndexed { idx, v ->
            val langTag = v.locale?.displayName ?: v.locale?.toLanguageTag().orEmpty()
            val offlineTag = if (v.isNetworkConnectionRequired) "在线" else "本地离线"
            val shortName = friendlyVoiceLabel(v.name, idx + 1)
            TtsVoiceOption(
                name = v.name,
                displayLabel = shortName,
                localeLabel = "$langTag · $offlineTag",
                isOffline = !v.isNetworkConnectionRequired
            )
        }
        _availableVoices.value = options
    }

    private fun friendlyVoiceLabel(rawName: String, index: Int): String {
        val lower = rawName.lowercase()
        return when {
            lower.contains("xiaoxiao") -> "晓晓（自然女声）"
            lower.contains("yunxi") -> "云希（清朗男声）"
            lower.contains("yunjian") -> "云健（沉稳男声）"
            lower.contains("xiaoyi") -> "晓伊（温柔女声）"
            lower.contains("female") || lower.contains("-f-") || lower.endsWith("f") -> "本地女声 $index"
            lower.contains("male") || lower.contains("-m-") || lower.endsWith("m") -> "本地男声 $index"
            lower.contains("local") -> "系统本地音色 $index"
            else -> "系统音色 $index (${rawName.takeLast(8)})"
        }
    }

    private fun setupListener(engine: TextToSpeech) {
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
                    if (suppressNextDone) {
                        suppressNextDone = false
                        return@launch
                    }
                    if (!_isPlaying.value || _isPaused.value) return@launch
                    val next = currentIndex + 1
                    if (next < currentSentences.size) {
                        speakCurrent(next)
                    } else {
                        if (currentSettings.sleepTimerMinutes == -1) {
                            stop()
                        } else {
                            onChapterFinishedCallback?.invoke()
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    if (!_isPlaying.value || _isPaused.value) return@launch
                    val next = currentIndex + 1
                    if (next < currentSentences.size) {
                        speakCurrent(next)
                    } else {
                        stop()
                    }
                }
            }
        })
    }

    fun startReading(
        sentences: List<TtsSentence>,
        startIndex: Int,
        settings: TtsSettings,
        onChapterCompleted: () -> Unit
    ) {
        if (!_isReady.value) {
            _errorMessage.value = "本地语音引擎尚未就绪，请稍候重试"
            return
        }
        if (sentences.isEmpty()) return

        currentSentences = sentences
        currentIndex = startIndex.coerceIn(0, sentences.lastIndex)
        currentSettings = settings
        onChapterFinishedCallback = onChapterCompleted
        _isPlaying.value = true
        _isPaused.value = false

        startSleepTimerIfNeeded(settings.sleepTimerMinutes)
        applyTtsParameters(settings)
        speakCurrent(currentIndex)
    }

    fun continueNextChapter(sentences: List<TtsSentence>) {
        if (!_isPlaying.value || sentences.isEmpty()) return
        currentSentences = sentences
        currentIndex = 0
        _isPaused.value = false
        applyTtsParameters(currentSettings)
        speakCurrent(0)
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPaused.value = true
        suppressNextDone = true
        tts?.stop()
    }

    fun resume() {
        if (!_isPlaying.value || !_isPaused.value) return
        _isPaused.value = false
        applyTtsParameters(currentSettings)
        speakCurrent(currentIndex)
    }

    fun stop() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingSeconds.value = 0
        _isPlaying.value = false
        _isPaused.value = false
        _activeSentence.value = null
        suppressNextDone = true
        tts?.stop()
    }

    fun updateLiveSettings(newSettings: TtsSettings) {
        val timerChanged = newSettings.sleepTimerMinutes != currentSettings.sleepTimerMinutes
        val audioChanged = newSettings.speechRate != currentSettings.speechRate ||
            newSettings.pitch != currentSettings.pitch ||
            newSettings.voiceName != currentSettings.voiceName
        currentSettings = newSettings

        if (timerChanged && _isPlaying.value) {
            startSleepTimerIfNeeded(newSettings.sleepTimerMinutes)
        }

        if (audioChanged) {
            applyTtsParameters(newSettings)
            if (_isPlaying.value && !_isPaused.value && currentSentences.isNotEmpty()) {
                suppressNextDone = true
                speakCurrent(currentIndex)
            }
        }
    }

    fun previewVoice(settings: TtsSettings) {
        val engine = tts ?: return
        if (!_isReady.value) return
        applyTtsParameters(settings)
        val params = Bundle()
        engine.speak(
            "你好，欢迎使用悦读本地语音朗读，当前语速为 ${String.format(Locale.US, "%.1f", settings.speechRate)} 倍。",
            TextToSpeech.QUEUE_FLUSH,
            params,
            "preview"
        )
    }

    private fun applyTtsParameters(settings: TtsSettings) {
        val engine = tts ?: return
        engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2.5f))
        engine.setPitch(settings.pitch.coerceIn(0.6f, 1.5f))
        if (settings.voiceName.isNotBlank()) {
            voicesMap[settings.voiceName]?.let { v ->
                runCatching { engine.voice = v }
            }
        }
    }

    private fun speakCurrent(index: Int) {
        val engine = tts ?: return
        val sentence = currentSentences.getOrNull(index) ?: return
        currentIndex = index
        _activeSentence.value = sentence
        val params = Bundle()
        engine.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, params, index.toString())
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
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
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
