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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.yuedu.reader.ui.theme.LocalLiquidGlass
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
    val activeTtsBookId by ttsManager.activeBookId.collectAsState()
    val activeTtsBookTitle by ttsManager.activeBookTitle.collectAsState()
    val activeTtsChapterTitle by ttsManager.activeChapterTitle.collectAsState()
    val sleepRemainingSeconds by ttsManager.sleepRemainingSeconds.collectAsState()
    val cachedChapterCounts by ttsManager.cachedChapterCounts.collectAsState()
    val bookDownloadProgress by ttsManager.bookDownloadProgress.collectAsState()
    val totalCacheBytes by ttsManager.totalCacheBytes.collectAsState()
    val ttsError by ttsManager.errorMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeBookId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ttsSettings.voiceId, ttsSettings.speechRate) {
        ttsManager.refreshAudioCacheStats(ttsSettings)
    }

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
                activeTtsBookId = activeTtsBookId,
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
                onStartTts = { chapters, chIdx, sentences, startIdx ->
                    ttsManager.startReading(
                        book = activeBook,
                        chapters = chapters,
                        chapterIndex = chIdx,
                        sentences = sentences,
                        startIndex = startIdx,
                        settings = ttsSettings
                    ) { b, cIdx, off, prog ->
                        scope.launch {
                            repository.updateBookProgress(b.id, cIdx, off, prog)
                        }
                    }
                },
                onPauseResumeTts = {
                    if (isTtsPaused) ttsManager.resume() else ttsManager.pause()
                },
                onStopTts = { ttsManager.stop() },
                ttsBookDownloadProgress = activeBook?.let { bookDownloadProgress[it.id] },
                onDownloadTtsChapters = { chIdx ->
                    val b = activeBook ?: return@ReaderScreen
                    scope.launch {
                        val chapters = repository.loadChapters(b.id)
                        Toast.makeText(context, "正在从香港服务器缓存后续 5 章同声离线音频...", Toast.LENGTH_SHORT).show()
                        ttsManager.downloadBookChapters(b, chapters, chIdx, 5, ttsSettings)
                            .onSuccess { count ->
                                val msg = if (count > 0) "已离线缓存 $count 章同音色音频" else "后续章节已在缓存中"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "缓存失败：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
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
                            books = books,
                            cachedChapterCounts = cachedChapterCounts,
                            bookDownloadProgress = bookDownloadProgress,
                            totalCacheBytes = totalCacheBytes,
                            ttsError = ttsError,
                            onUpdateTtsSettings = { transform ->
                                scope.launch {
                                    val next = repository.updateTtsSettings(transform)
                                    ttsManager.updateLiveSettings(next)
                                    ttsManager.refreshAudioCacheStats(next)
                                }
                            },
                            onSelectAndPreviewVoice = { voice ->
                                scope.launch {
                                    val next = repository.updateTtsSettings { it.copy(voiceId = voice.id) }
                                    ttsManager.updateLiveSettings(next)
                                    ttsManager.refreshAudioCacheStats(next)
                                    if (!isTtsPlaying) {
                                        ttsManager.previewVoice(next)
                                    }
                                }
                            },
                            onDownloadBookChapters = { book ->
                                scope.launch {
                                    val chapters = repository.loadChapters(book.id)
                                    Toast.makeText(context, "正在缓存《${book.title}》后续 5 章同音色音频...", Toast.LENGTH_SHORT).show()
                                    ttsManager.downloadBookChapters(book, chapters, book.currentChapterIndex, 5, ttsSettings)
                                        .onSuccess { cnt ->
                                            val msg = if (cnt > 0) "已完成 $cnt 章离线音频缓存" else "章节已全部缓存"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "缓存失败：${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                            },
                            onCancelBookDownload = { bookId ->
                                ttsManager.cancelBookAudioDownload(bookId)
                            },
                            onClearAudioCache = {
                                ttsManager.clearAllCachedAudio(ttsSettings)
                                Toast.makeText(context, "已清理全部离线听书缓存", Toast.LENGTH_SHORT).show()
                            },
                            onPauseResumeTts = {
                                if (isTtsPaused) ttsManager.resume() else ttsManager.pause()
                            },
                            onStopTts = { ttsManager.stop() }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isTtsPlaying && activeTtsBookId != null) {
                        val glass = LocalLiquidGlass.current
                        val barBg = if (glass.isDark) Color(0xEE1A1D24) else Color(0xF2FFFFFF)
                        val barBorder = if (glass.isDark) Color(0x36FFFFFF) else Color(0xCCFFFFFF)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .shadow(14.dp, CircleShape)
                                .clip(CircleShape)
                                .background(barBg)
                                .border(0.8.dp, barBorder, CircleShape)
                                .clickable { activeBookId = activeTtsBookId }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(glass.accentSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = glass.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = activeTtsBookTitle ?: "正在朗读",
                                        color = glass.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(if (isTtsPaused) "已暂停 · " else "朗读中 · ")
                                            append(activeTtsChapterTitle ?: "点击回到阅读位置")
                                        },
                                        color = glass.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(glass.accentSoft)
                                        .clickable {
                                            if (isTtsPaused) ttsManager.resume() else ttsManager.pause()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isTtsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = "暂停/继续",
                                        tint = glass.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x1FFF3B30))
                                        .clickable { ttsManager.stop() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "停止",
                                        tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.padding(horizontal = 36.dp)) {
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
}
