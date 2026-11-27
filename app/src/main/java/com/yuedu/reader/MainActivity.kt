package com.yuedu.reader

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuedu.reader.data.BookRepository
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.model.TransferKind
import com.yuedu.reader.server.WifiTransferServer
import com.yuedu.reader.tts.LocalTtsManager
import com.yuedu.reader.ui.components.AmbientGlassBackground
import com.yuedu.reader.ui.components.GlassTabItem
import com.yuedu.reader.ui.components.LiquidGlassFloatingTabBar
import com.yuedu.reader.ui.screens.AiReadingSettingsScreen
import com.yuedu.reader.ui.screens.BookshelfScreen
import com.yuedu.reader.ui.screens.ReaderScreen
import com.yuedu.reader.ui.theme.YueDuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: BookRepository
    private lateinit var wifiServer: WifiTransferServer
    private lateinit var ttsManager: LocalTtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = BookRepository(applicationContext)
        wifiServer = WifiTransferServer(applicationContext, repository)
        ttsManager = LocalTtsManager(applicationContext)

        setContent {
            YueDuApp(
                repository = repository,
                wifiServer = wifiServer,
                ttsManager = ttsManager
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wifiServer.isInitialized) {
            wifiServer.stop()
        }
        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }
    }
}

@Composable
fun YueDuApp(
    repository: BookRepository,
    wifiServer: WifiTransferServer,
    ttsManager: LocalTtsManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val books by repository.books.collectAsState()
    val fonts by repository.fonts.collectAsState()
    val settings by repository.settings.collectAsState()
    val ttsSettings by repository.ttsSettings.collectAsState()

    val isServerRunning by wifiServer.isRunning.collectAsState()
    val serverUrl by wifiServer.serverUrl.collectAsState()
    val recentTransfers by wifiServer.recentTransfers.collectAsState()

    val isTtsPlaying by ttsManager.isPlaying.collectAsState()
    val isTtsPaused by ttsManager.isPaused.collectAsState()
    val availableVoices by ttsManager.availableVoices.collectAsState()
    val activeTtsSentence by ttsManager.activeSentence.collectAsState()
    val sleepRemainingSeconds by ttsManager.sleepRemainingSeconds.collectAsState()
    val downloadedPacks by ttsManager.downloadedPacks.collectAsState()
    val packDownloadProgress by ttsManager.packDownloadProgress.collectAsState()
    val ttsError by ttsManager.errorMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeBookId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.initialize()
        wifiServer.events.collect { event ->
            val msg = if (event.kind == TransferKind.BOOK) {
                "已收到书籍：${event.fileName}"
            } else {
                "已导入字体：${event.fileName}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { wifiServer.stop() }
    }

    val bookPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var okCount = 0
            for (uri in uris) {
                runCatching { repository.importBookFromUri(uri) }
                    .onSuccess { okCount++ }
                    .onFailure {
                        Toast.makeText(context, it.message ?: "导入书籍失败", Toast.LENGTH_SHORT).show()
                    }
            }
            if (okCount > 0) {
                Toast.makeText(context, "已导入 $okCount 本 TXT 书籍", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { repository.importFontFromUri(uri) }
                .onSuccess { font ->
                    repository.updateSettings { it.copy(selectedFontId = font.id) }
                    Toast.makeText(context, "已导入并应用字体：${font.displayName}", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "导入字体失败", Toast.LENGTH_SHORT).show()
                }
        }
    }

    YueDuTheme(appearance = settings.appAppearance) {
        val activeBook: BookItem? = remember(activeBookId, books) {
            activeBookId?.let { id -> books.find { it.id == id } }
        }

        if (activeBook != null) {
            ReaderScreen(
                book = activeBook,
                settings = settings,
                ttsSettings = ttsSettings,
                fonts = fonts,
                isTtsPlaying = isTtsPlaying,
                isTtsPaused = isTtsPaused,
                activeTtsSentence = activeTtsSentence,
                sleepRemainingSeconds = sleepRemainingSeconds,
                resolveTypeface = { repository.getTypeface(it) },
                loadChapters = { repository.loadChapters(it) },
                loadChapterParagraphs = { b, ch -> repository.loadChapterParagraphs(b, ch) },
                loadBookmarks = { repository.loadBookmarks(it) },
                onAddBookmark = { repository.addBookmark(it) },
                onRemoveBookmark = { bookId, bmId -> repository.removeBookmark(bookId, bmId) },
                onSearchBook = { b, chs, q -> repository.searchBook(b, chs, q) },
                onSaveProgress = { bookId, chIdx, offset, prog ->
                    scope.launch {
                        repository.updateBookProgress(bookId, chIdx, offset, prog)
                    }
                },
                onUpdateSettings = { transform ->
                    scope.launch { repository.updateSettings(transform) }
                },
                onStartTts = { sentences, startIdx, onChapterDone ->
                    ttsManager.startReading(sentences, startIdx, ttsSettings, onChapterDone)
                },
                onContinueTtsNextChapter = { sentences ->
                    ttsManager.continueNextChapter(sentences)
                },
                onPauseResumeTts = {
                    if (isTtsPaused) ttsManager.resume() else ttsManager.pause()
                },
                onStopTts = { ttsManager.stop() },
                onOpenAiSettingsTab = {
                    activeBookId = null
                    selectedTab = 1
                },
                onImportLocalFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
                onDeleteFont = { font ->
                    scope.launch { repository.deleteFont(font.id) }
                },
                onDownloadCatalogFont = { catalogFont, onProgress ->
                    runCatching { repository.downloadCatalogFont(catalogFont, onProgress) }
                },
                onGoWifiTransfer = {
                    activeBookId = null
                    selectedTab = 0
                    wifiServer.start()
                },
                onExit = {
                    ttsManager.stop()
                    activeBookId = null
                }
            )
        } else {
            val tabs = remember {
                listOf(
                    GlassTabItem("书架", Icons.Rounded.AutoStories),
                    GlassTabItem("AI阅读", Icons.Rounded.GraphicEq)
                )
            }

            AmbientGlassBackground {
                Crossfade(
                    targetState = selectedTab,
                    label = "mainTab",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> BookshelfScreen(
                            books = books,
                            fonts = fonts,
                            settings = settings,
                            isWifiRunning = isServerRunning,
                            wifiServerUrl = serverUrl,
                            recentTransfers = recentTransfers,
                            resolveTypeface = { repository.getTypeface(it) },
                            onOpenBook = { activeBookId = it.id },
                            onImportLocalBook = { bookPickerLauncher.launch(arrayOf("text/plain", "*/*")) },
                            onImportLocalFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
                            onStartWifiServer = {
                                wifiServer.start().onFailure {
                                    Toast.makeText(context, it.message ?: "启动失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStopWifiServer = { wifiServer.stop() },
                            onRefreshWifiIp = { wifiServer.refreshUrl() },
                            onSelectFont = { fontId ->
                                scope.launch { repository.updateSettings { it.copy(selectedFontId = fontId) } }
                            },
                            onDeleteFont = { font ->
                                scope.launch { repository.deleteFont(font.id) }
                            },
                            onDownloadCatalogFont = { catalogFont, onProgress ->
                                runCatching { repository.downloadCatalogFont(catalogFont, onProgress) }
                            },
                            onRenameBook = { book, newTitle ->
                                scope.launch { repository.renameBook(book.id, newTitle) }
                            },
                            onDeleteBook = { book ->
                                scope.launch { repository.deleteBook(book.id) }
                            },
                            onChangeAppearance = { mode ->
                                scope.launch { repository.updateSettings { it.copy(appAppearance = mode) } }
                            }
                        )
                        else -> AiReadingSettingsScreen(
                            ttsSettings = ttsSettings,
                            isTtsPlaying = isTtsPlaying,
                            isTtsPaused = isTtsPaused,
                            sleepRemainingSeconds = sleepRemainingSeconds,
                            availableVoices = availableVoices,
                            downloadedPacks = downloadedPacks,
                            packDownloadProgress = packDownloadProgress,
                            ttsError = ttsError,
                            onUpdateTtsSettings = { transform ->
                                scope.launch {
                                    val next = repository.updateTtsSettings(transform)
                                    ttsManager.updateLiveSettings(next)
                                }
                            },
                            onSelectAndPreviewVoice = { voice ->
                                scope.launch {
                                    val next = repository.updateTtsSettings { it.copy(voiceId = voice.id) }
                                    ttsManager.updateLiveSettings(next)
                                    if (!isTtsPlaying) {
                                        ttsManager.previewVoice(next)
                                    }
                                }
                            },
                            onDownloadVoicePack = { packId ->
                                scope.launch {
                                    Toast.makeText(context, "开始下载离线语音包...", Toast.LENGTH_SHORT).show()
                                    ttsManager.downloadOfflinePack(packId)
                                        .onSuccess {
                                            Toast.makeText(context, "语音包下载完成", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "语音包下载失败：${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                            },
                            onPauseResumeTts = {
                                if (isTtsPaused) ttsManager.resume() else ttsManager.pause()
                            },
                            onStopTts = { ttsManager.stop() }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 36.dp, vertical = 14.dp)
                ) {
                    LiquidGlassFloatingTabBar(
                        tabs = tabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        }
    }
}
