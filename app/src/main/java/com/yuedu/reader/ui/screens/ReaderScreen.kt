package com.yuedu.reader.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.os.BatteryManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuedu.reader.engine.TxtBookEngine
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.model.BookmarkItem
import com.yuedu.reader.model.ChapterInfo
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.DownloadableFont
import com.yuedu.reader.model.ReaderPage
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.model.ReaderTheme
import com.yuedu.reader.model.ReadingMode
import com.yuedu.reader.model.SearchHit
import com.yuedu.reader.model.TtsSentence
import com.yuedu.reader.model.TtsSettings
import com.yuedu.reader.tts.LocalTtsManager
import com.yuedu.reader.ui.components.FontManagerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private data class PaginatedChapterState(
    val chapterIndex: Int,
    val pages: List<ReaderPage>,
    val sentences: List<TtsSentence>
)

private data class VirtualSmoothPage(
    val chapterIdx: Int,
    val pageIdxInChapter: Int,
    val page: ReaderPage
)

private data class VerticalParaItem(
    val chapterIndex: Int,
    val paraIndex: Int,
    val text: String,
    val isTitle: Boolean,
    val charOffset: Int
) {
    val key: String = "ch_${chapterIndex}_p_${paraIndex}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: BookItem,
    settings: ReaderSettings,
    ttsSettings: TtsSettings,
    fonts: List<CustomFont>,
    isTtsPlaying: Boolean,
    isTtsPaused: Boolean,
    activeTtsSentence: TtsSentence?,
    sleepRemainingSeconds: Int,
    resolveTypeface: (String) -> Typeface,
    loadChapters: suspend (String) -> List<ChapterInfo>,
    loadChapterParagraphs: suspend (BookItem, ChapterInfo) -> List<TxtBookEngine.ChapterParagraph>,
    loadBookmarks: suspend (String) -> List<BookmarkItem>,
    onAddBookmark: suspend (BookmarkItem) -> List<BookmarkItem>,
    onRemoveBookmark: suspend (String, String) -> List<BookmarkItem>,
    onSearchBook: suspend (BookItem, List<ChapterInfo>, String) -> List<SearchHit>,
    onSaveProgress: (bookId: String, chapterIdx: Int, charOffset: Int, progress: Float) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onStartTts: (sentences: List<TtsSentence>, startIdx: Int, onChapterDone: () -> Unit) -> Unit,
    onPreloadTtsNextChapter: (nextSentences: List<TtsSentence>) -> Unit,
    onContinueTtsNextChapter: (sentences: List<TtsSentence>) -> Unit,
    onPauseResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    onOpenAiSettingsTab: () -> Unit,
    onImportLocalFont: () -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onDownloadCatalogFont: suspend (DownloadableFont, (Float) -> Unit) -> Result<CustomFont>,
    onGoWifiTransfer: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var chapters by remember(book.id) { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var chapterIndex by remember(book.id) { mutableIntStateOf(book.currentChapterIndex.coerceAtLeast(0)) }
    var targetCharOffset by remember(book.id) { mutableIntStateOf(book.currentCharOffset.coerceAtLeast(0)) }
    var jumpToLastPageOnChapterLoad by remember { mutableStateOf(false) }
    var jumpNonce by remember(book.id) { mutableIntStateOf(0) }

    // Atomic paginated state for current, prev, and next chapters (eliminates jump-to-wrong-page bug)
    var currentPaginated by remember(book.id) { mutableStateOf<PaginatedChapterState?>(null) }
    var prevPaginated by remember(book.id) { mutableStateOf<PaginatedChapterState?>(null) }
    var nextPaginated by remember(book.id) { mutableStateOf<PaginatedChapterState?>(null) }
    var currentPageIndex by remember(book.id) { mutableIntStateOf(0) }
    var currentChapterParagraphs by remember(book.id) { mutableStateOf<List<TxtBookEngine.ChapterParagraph>>(emptyList()) }

    var bookmarks by remember(book.id) { mutableStateOf<List<BookmarkItem>>(emptyList()) }
    var showControls by remember { mutableStateOf(false) }
    var showDirectorySheet by remember { mutableStateOf(false) }
    var directoryInitialTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showFontManagerSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var reverseChapters by remember { mutableStateOf(false) }

    var searchQuery by remember(book.id) { mutableStateOf("") }
    var searchResults by remember(book.id) { mutableStateOf<List<SearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val (clockText, batteryPercent) = rememberClockAndBattery()

    BackHandler {
        when {
            showFontManagerSheet -> showFontManagerSheet = false
            showSearchSheet -> showSearchSheet = false
            showSettingsSheet -> showSettingsSheet = false
            showDirectorySheet -> showDirectorySheet = false
            showControls -> showControls = false
            else -> onExit()
        }
    }

    DisposableEffect(showControls, settings.theme) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = !settings.theme.isDark
            if (showControls) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    LaunchedEffect(book.id) {
        val loaded = loadChapters(book.id)
        chapters = loaded
        bookmarks = loadBookmarks(book.id)
        if (chapterIndex >= loaded.size && loaded.isNotEmpty()) {
            chapterIndex = loaded.lastIndex
        }
    }

    val activeTypeface = remember(settings.selectedFontId, fonts) {
        resolveTypeface(settings.selectedFontId)
    }

    val topSafeInsets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val bottomSafeInsets = WindowInsets.navigationBarsIgnoringVisibility
    val topSafeDp: Dp = maxOf(topSafeInsets.asPaddingValues().calculateTopPadding(), 32.dp) + 6.dp
    val bottomSafeDp: Dp = maxOf(bottomSafeInsets.asPaddingValues().calculateBottomPadding(), 12.dp)

    val solidSheetBg = if (settings.theme.isDark) Color(0xFF1A1C22) else Color(0xFFFFFFFF)
    val solidSheetCard = if (settings.theme.isDark) Color(0xFF252830) else Color(0xFFF2F2F7)
    val solidGlassBarBg = if (settings.theme.isDark) Color(0xE8181A20) else Color(0xEAF8F9FC)
    val solidGlassBorder = if (settings.theme.isDark) Color(0x36FFFFFF) else Color(0xCCFFFFFF)

    fun computeProgress(chIdx: Int, pageIdx: Int, totalPagesInCh: Int): Float {
        val ch = chapters.getOrNull(chIdx) ?: return 0f
        val totalBytes = book.totalBytes.coerceAtLeast(1L).toFloat()
        val chBytes = (ch.endByte - ch.startByte).coerceAtLeast(1L).toFloat()
        val ratio = (pageIdx + 1f) / totalPagesInCh.coerceAtLeast(1)
        return ((ch.startByte + chBytes * ratio) / totalBytes).coerceIn(0f, 1f)
    }

    val activePages = currentPaginated?.takeIf { it.chapterIndex == chapterIndex }?.pages.orEmpty()
    val currentBookProgress = remember(chapters, chapterIndex, currentPageIndex, activePages.size) {
        computeProgress(chapterIndex, currentPageIndex, activePages.size)
    }

    val activeBookmark = remember(bookmarks, chapterIndex, activePages, currentPageIndex) {
        val page = activePages.getOrNull(currentPageIndex)
        if (page == null) null
        else bookmarks.find {
            it.chapterIndex == chapterIndex &&
                it.charOffset >= page.startOffset &&
                it.charOffset <= page.endOffset
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(settings.theme.backgroundColor)
    ) {
        val horizontalPadPx = with(density) { settings.horizontalPaddingDp.dp.roundToPx() }
        val topSafePx = with(density) { topSafeDp.roundToPx() }
        val bottomSafePx = with(density) { bottomSafeDp.roundToPx() }
        val topHeaderReservePx = with(density) { 28.dp.roundToPx() }
        val bottomFooterReservePx = with(density) { 28.dp.roundToPx() }

        val contentWidthPx = (constraints.maxWidth - horizontalPadPx * 2).coerceAtLeast(100)
        val contentHeightPx = (constraints.maxHeight - topSafePx - bottomSafePx - topHeaderReservePx - bottomFooterReservePx).coerceAtLeast(200)
        val textSizePx = with(density) { settings.fontSizeSp.sp.toPx() }
        val paragraphSpacingPx = with(density) { settings.paragraphSpacingDp.dp.toPx() }

        suspend fun paginateChapterByIndex(idx: Int): Pair<List<TxtBookEngine.ChapterParagraph>, PaginatedChapterState> {
            val ch = chapters[idx]
            val paras = loadChapterParagraphs(book, ch)
            val pg = withContext(Dispatchers.Default) {
                TxtBookEngine.paginateParagraphs(
                    paragraphs = paras,
                    widthPx = contentWidthPx,
                    heightPx = contentHeightPx,
                    textSizePx = textSizePx,
                    lineHeightMultiplier = settings.lineHeightMultiplier,
                    paragraphSpacingPx = paragraphSpacingPx,
                    typeface = activeTypeface
                )
            }
            val sentences = withContext(Dispatchers.Default) {
                LocalTtsManager.splitChapterIntoSentences(paras, idx)
            }
            return paras to PaginatedChapterState(idx, pg, sentences)
        }

        // Load & paginate current chapter + preload prev/next atomically
        LaunchedEffect(
            chapters,
            chapterIndex,
            jumpNonce,
            contentWidthPx,
            contentHeightPx,
            textSizePx,
            settings.lineHeightMultiplier,
            paragraphSpacingPx,
            activeTypeface
        ) {
            if (chapters.isEmpty()) return@LaunchedEffect
            val safeIdx = chapterIndex.coerceIn(0, chapters.lastIndex)

            val (paras, curState) = paginateChapterByIndex(safeIdx)

            currentChapterParagraphs = paras
            currentPaginated = curState

            currentPageIndex = if (jumpToLastPageOnChapterLoad) {
                jumpToLastPageOnChapterLoad = false
                curState.pages.lastIndex
            } else {
                val idx = curState.pages.indexOfFirst {
                    targetCharOffset >= it.startOffset && targetCharOffset < it.endOffset
                }
                if (idx >= 0) idx else 0
            }

            prevPaginated = if (safeIdx > 0) paginateChapterByIndex(safeIdx - 1).second else null
            nextPaginated = if (safeIdx < chapters.lastIndex) paginateChapterByIndex(safeIdx + 1).second else null
        }

        // Feed next chapter's sentences to TTS engine so chapter boundaries never stall
        LaunchedEffect(nextPaginated, isTtsPlaying, chapterIndex) {
            if (isTtsPlaying) {
                val nextSents = nextPaginated?.takeIf { it.chapterIndex == chapterIndex + 1 }?.sentences.orEmpty()
                onPreloadTtsNextChapter(nextSents)
            }
        }

        // Persist progress ONLY when currentPaginated matches chapterIndex
        LaunchedEffect(currentPageIndex, currentPaginated) {
            val state = currentPaginated ?: return@LaunchedEffect
            if (state.chapterIndex != chapterIndex) return@LaunchedEffect
            val page = state.pages.getOrNull(currentPageIndex) ?: return@LaunchedEffect
            targetCharOffset = page.startOffset
            val progress = computeProgress(chapterIndex, currentPageIndex, state.pages.size)
            onSaveProgress(book.id, chapterIndex, page.startOffset, progress)
        }

        fun goPrevChapterLastPage() {
            if (chapterIndex <= 0) return
            val targetIdx = chapterIndex - 1
            val cachedPrev = prevPaginated?.takeIf { it.chapterIndex == targetIdx }
            if (cachedPrev != null) {
                nextPaginated = currentPaginated
                currentPaginated = cachedPrev
                prevPaginated = null
                chapterIndex = targetIdx
                currentPageIndex = cachedPrev.pages.lastIndex
                targetCharOffset = cachedPrev.pages.lastOrNull()?.startOffset ?: 0
                jumpToLastPageOnChapterLoad = false
            } else {
                jumpToLastPageOnChapterLoad = true
                targetCharOffset = 0
                chapterIndex = targetIdx
            }
        }

        fun goNextChapterFirstPage() {
            if (chapterIndex >= chapters.lastIndex) return
            val targetIdx = chapterIndex + 1
            val cachedNext = nextPaginated?.takeIf { it.chapterIndex == targetIdx }
            if (cachedNext != null) {
                prevPaginated = currentPaginated
                currentPaginated = cachedNext
                nextPaginated = null
                chapterIndex = targetIdx
                currentPageIndex = 0
                targetCharOffset = 0
                jumpToLastPageOnChapterLoad = false
            } else {
                jumpToLastPageOnChapterLoad = false
                targetCharOffset = 0
                chapterIndex = targetIdx
            }
        }

        // Auto-follow TTS sentence page turn
        LaunchedEffect(activeTtsSentence, ttsSettings.autoFollow, isTtsPlaying) {
            val sentence = activeTtsSentence ?: return@LaunchedEffect
            if (!isTtsPlaying || !ttsSettings.autoFollow) return@LaunchedEffect
            if (sentence.chapterIndex == chapterIndex) {
                val pages = currentPaginated?.takeIf { it.chapterIndex == chapterIndex }?.pages ?: return@LaunchedEffect
                val targetPage = pages.indexOfFirst {
                    sentence.startOffset >= it.startOffset && sentence.startOffset < it.endOffset
                }.let { if (it >= 0) it else pages.indexOfLast { p -> p.startOffset <= sentence.startOffset } }
                if (targetPage >= 0 && targetPage != currentPageIndex) {
                    currentPageIndex = targetPage
                }
            }
        }

        fun startTtsFromCurrentPosition() {
            val state = currentPaginated ?: return
            val page = state.pages.getOrNull(currentPageIndex)
            val offset = page?.startOffset ?: targetCharOffset
            val startSentenceIdx = state.sentences.indexOfFirst { it.endOffset > offset }.coerceAtLeast(0)
            onStartTts(state.sentences, startSentenceIdx) {
                // Chapter finished callback -> seamlessly advance to next chapter and continue reading
                scope.launch {
                    if (chapterIndex < chapters.lastIndex) {
                        goNextChapterFirstPage()
                        val nextIdx = chapterIndex
                        val nextSentences = currentPaginated?.takeIf { it.chapterIndex == nextIdx }?.sentences
                            ?: paginateChapterByIndex(nextIdx).second.sentences
                        onContinueTtsNextChapter(nextSentences)
                    } else {
                        onStopTts()
                    }
                }
            }
        }

        val highlightRange: IntRange? = if (
            isTtsPlaying &&
            ttsSettings.highlightSentence &&
            activeTtsSentence != null &&
            activeTtsSentence.chapterIndex == chapterIndex
        ) {
            activeTtsSentence.startOffset until activeTtsSentence.endOffset
        } else {
            null
        }

        val stateToRender = currentPaginated
        if (stateToRender == null || stateToRender.pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = settings.theme.accentColor)
            }
        } else {
            val pages = stateToRender.pages
            val safePageIdx = currentPageIndex.coerceIn(0, pages.lastIndex)
            val currentChapterTitle = chapters.getOrNull(chapterIndex)?.title ?: book.title

            when (settings.readingMode) {
                ReadingMode.SMOOTH -> {
                    val prevLast = prevPaginated?.takeIf { it.chapterIndex == chapterIndex - 1 }?.pages?.lastOrNull()
                    val nextFirst = nextPaginated?.takeIf { it.chapterIndex == chapterIndex + 1 }?.pages?.firstOrNull()
                    val virtualPages = remember(pages, prevLast, nextFirst, chapterIndex) {
                        buildList {
                            prevLast?.let { add(VirtualSmoothPage(chapterIndex - 1, it.pageIndex, it)) }
                            pages.forEach { add(VirtualSmoothPage(chapterIndex, it.pageIndex, it)) }
                            nextFirst?.let { add(VirtualSmoothPage(chapterIndex + 1, 0, it)) }
                        }
                    }
                    val prevOffsetCount = if (prevLast != null) 1 else 0
                    val pagerState = rememberPagerState(
                        initialPage = (prevOffsetCount + safePageIdx).coerceIn(0, virtualPages.lastIndex),
                        pageCount = { virtualPages.size }
                    )

                    LaunchedEffect(virtualPages, safePageIdx) {
                        val target = (prevOffsetCount + safePageIdx).coerceIn(0, virtualPages.lastIndex)
                        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
                            pagerState.scrollToPage(target)
                        }
                    }

                    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
                        if (pagerState.isScrollInProgress) return@LaunchedEffect
                        val vp = virtualPages.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        if (vp.chapterIdx < chapterIndex) {
                            goPrevChapterLastPage()
                        } else if (vp.chapterIdx > chapterIndex) {
                            goNextChapterFirstPage()
                        } else {
                            currentPageIndex = vp.pageIdxInChapter
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapPositionalThreshold = 0.14f
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(virtualPages.size, pagerState.currentPage, showControls) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    when {
                                        showControls -> showControls = false
                                        offset.x < w * 0.30f -> {
                                            if (pagerState.currentPage > 0) {
                                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                            }
                                        }
                                        offset.x > w * 0.70f -> {
                                            if (pagerState.currentPage < virtualPages.lastIndex) {
                                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            }
                                        }
                                        else -> showControls = true
                                    }
                                }
                            }
                    ) { vIdx ->
                        val vp = virtualPages[vIdx]
                        val chTitle = chapters.getOrNull(vp.chapterIdx)?.title ?: book.title
                        val totalInCh = when (vp.chapterIdx) {
                            chapterIndex -> pages.size
                            chapterIndex - 1 -> prevPaginated?.pages?.size ?: (vp.page.pageIndex + 1)
                            else -> nextPaginated?.pages?.size ?: 1
                        }
                        val progress = computeProgress(vp.chapterIdx, vp.pageIdxInChapter, totalInCh)
                        ReaderPageFrame(
                            page = vp.page,
                            chapterTitle = chTitle,
                            progressPercent = progress,
                            clockText = clockText,
                            batteryPercent = batteryPercent,
                            highlightRange = if (vp.chapterIdx == chapterIndex) highlightRange else null,
                            topSafeDp = topSafeDp,
                            bottomSafeDp = bottomSafeDp,
                            horizontalPaddingDp = settings.horizontalPaddingDp,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = settings.lineHeightMultiplier,
                            paragraphSpacingPx = paragraphSpacingPx,
                            typeface = activeTypeface,
                            theme = settings.theme
                        )
                    }
                }

                ReadingMode.SIMULATION -> {
                    val prevLast = prevPaginated?.takeIf { it.chapterIndex == chapterIndex - 1 }?.pages?.lastOrNull()
                    val nextFirst = nextPaginated?.takeIf { it.chapterIndex == chapterIndex + 1 }?.pages?.firstOrNull()
                    SimulationCurlPager(
                        pages = pages,
                        currentPageIndex = safePageIdx,
                        prevChapterLastPage = prevLast,
                        nextChapterFirstPage = nextFirst,
                        onPageChanged = { currentPageIndex = it },
                        onRequestPrevChapter = { goPrevChapterLastPage() },
                        onRequestNextChapter = { goNextChapterFirstPage() },
                        onToggleMenu = { showControls = !showControls },
                        hideMenuIfShown = {
                            if (showControls) {
                                showControls = false
                                true
                            } else false
                        },
                        theme = settings.theme
                    ) { pageObj, chOffset ->
                        val targetChIdx = (chapterIndex + chOffset).coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
                        val chTitle = chapters.getOrNull(targetChIdx)?.title ?: book.title
                        val totalInCh = when (chOffset) {
                            0 -> pages.size
                            -1 -> prevPaginated?.pages?.size ?: (pageObj.pageIndex + 1)
                            else -> nextPaginated?.pages?.size ?: 1
                        }
                        val progress = computeProgress(targetChIdx, pageObj.pageIndex, totalInCh)
                        ReaderPageFrame(
                            page = pageObj,
                            chapterTitle = chTitle,
                            progressPercent = progress,
                            clockText = clockText,
                            batteryPercent = batteryPercent,
                            highlightRange = if (chOffset == 0) highlightRange else null,
                            topSafeDp = topSafeDp,
                            bottomSafeDp = bottomSafeDp,
                            horizontalPaddingDp = settings.horizontalPaddingDp,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = settings.lineHeightMultiplier,
                            paragraphSpacingPx = paragraphSpacingPx,
                            typeface = activeTypeface,
                            theme = settings.theme
                        )
                    }
                }

                ReadingMode.VERTICAL -> {
                    val verticalItems = remember { mutableStateListOf<VerticalParaItem>() }
                    var maxLoadedChapter by remember(book.id, chapterIndex) { mutableIntStateOf(chapterIndex) }
                    var isAppending by remember { mutableStateOf(false) }
                    var lastHandledJumpNonce by remember(book.id) { mutableIntStateOf(-1) }
                    val listState = rememberLazyListState()

                    LaunchedEffect(currentChapterParagraphs, chapterIndex, jumpNonce) {
                        if (chapters.isEmpty() || currentChapterParagraphs.isEmpty()) return@LaunchedEffect
                        val alreadyHasChapter = verticalItems.any { it.chapterIndex == chapterIndex }
                        val forceJump = jumpNonce != lastHandledJumpNonce
                        if (!alreadyHasChapter || forceJump) {
                            lastHandledJumpNonce = jumpNonce
                            verticalItems.clear()
                            maxLoadedChapter = chapterIndex
                            currentChapterParagraphs.forEachIndexed { idx, p ->
                                verticalItems.add(
                                    VerticalParaItem(chapterIndex, idx, p.text, p.isTitle, p.charStartOffset)
                                )
                            }
                            val targetParaIdx = currentChapterParagraphs.indexOfFirst { it.charStartOffset >= targetCharOffset }.coerceAtLeast(0)
                            listState.scrollToItem(targetParaIdx)
                        }
                    }

                    // Auto-scroll vertical list when TTS speaks next sentence
                    LaunchedEffect(activeTtsSentence, ttsSettings.autoFollow, isTtsPlaying) {
                        val s = activeTtsSentence ?: return@LaunchedEffect
                        if (!isTtsPlaying || !ttsSettings.autoFollow) return@LaunchedEffect
                        val idx = verticalItems.indexOfFirst {
                            it.chapterIndex == s.chapterIndex &&
                                s.startOffset >= it.charOffset &&
                                s.startOffset < it.charOffset + it.text.length
                        }
                        if (idx >= 0) {
                            listState.animateScrollToItem(idx)
                        }
                    }

                    val lastVisible by remember {
                        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    }
                    LaunchedEffect(lastVisible, verticalItems.size, maxLoadedChapter) {
                        if (!isAppending &&
                            verticalItems.isNotEmpty() &&
                            lastVisible >= verticalItems.size - 6 &&
                            maxLoadedChapter < chapters.lastIndex
                        ) {
                            isAppending = true
                            val nextIdx = maxLoadedChapter + 1
                            val nextParas = loadChapterParagraphs(book, chapters[nextIdx])
                            nextParas.forEachIndexed { idx, p ->
                                verticalItems.add(
                                    VerticalParaItem(nextIdx, idx, p.text, p.isTitle, p.charStartOffset)
                                )
                            }
                            maxLoadedChapter = nextIdx
                            isAppending = false
                        }
                    }

                    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                    LaunchedEffect(firstVisible, verticalItems.size) {
                        val item = verticalItems.getOrNull(firstVisible) ?: return@LaunchedEffect
                        if (item.chapterIndex != chapterIndex) {
                            chapterIndex = item.chapterIndex
                        }
                        targetCharOffset = item.charOffset
                        val ch = chapters.getOrNull(item.chapterIndex) ?: return@LaunchedEffect
                        val totalBytes = book.totalBytes.coerceAtLeast(1L).toFloat()
                        val chBytes = (ch.endByte - ch.startByte).coerceAtLeast(1L).toFloat()
                        val chItems = verticalItems.count { it.chapterIndex == item.chapterIndex }.coerceAtLeast(1)
                        val ratio = (item.paraIndex + 1f) / chItems
                        val prog = ((ch.startByte + chBytes * ratio) / totalBytes).coerceIn(0f, 1f)
                        onSaveProgress(book.id, item.chapterIndex, item.charOffset, prog)
                    }

                    val activeFontFamily = remember(activeTypeface) { FontFamily(activeTypeface) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = topSafeDp + 28.dp,
                                bottom = bottomSafeDp + 36.dp,
                                start = settings.horizontalPaddingDp.dp,
                                end = settings.horizontalPaddingDp.dp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { showControls = !showControls }
                                }
                        ) {
                            items(verticalItems, key = { it.key }) { item ->
                                val paraStart = item.charOffset
                                val paraEnd = item.charOffset + item.text.length
                                val hl = highlightRange
                                val annotated = remember(item.text, hl, item.chapterIndex, activeTtsSentence?.chapterIndex) {
                                    if (hl != null && activeTtsSentence?.chapterIndex == item.chapterIndex &&
                                        hl.first < paraEnd && hl.last + 1 > paraStart
                                    ) {
                                        val s = (hl.first - paraStart).coerceIn(0, item.text.length)
                                        val e = (hl.last + 1 - paraStart).coerceIn(0, item.text.length)
                                        buildAnnotatedString {
                                            append(item.text)
                                            if (e > s) {
                                                addStyle(
                                                    SpanStyle(background = settings.theme.accentColor.copy(alpha = 0.24f)),
                                                    s,
                                                    e
                                                )
                                            }
                                        }
                                    } else null
                                }

                                if (annotated != null) {
                                    Text(
                                        text = annotated,
                                        color = settings.theme.textColor,
                                        fontSize = if (item.isTitle) (settings.fontSizeSp * 1.16f).sp else settings.fontSizeSp.sp,
                                        fontWeight = if (item.isTitle) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = ((if (item.isTitle) settings.fontSizeSp * 1.16f else settings.fontSizeSp) * settings.lineHeightMultiplier).sp,
                                        fontFamily = activeFontFamily
                                    )
                                } else {
                                    Text(
                                        text = item.text,
                                        color = settings.theme.textColor,
                                        fontSize = if (item.isTitle) (settings.fontSizeSp * 1.16f).sp else settings.fontSizeSp.sp,
                                        fontWeight = if (item.isTitle) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = ((if (item.isTitle) settings.fontSizeSp * 1.16f else settings.fontSizeSp) * settings.lineHeightMultiplier).sp,
                                        fontFamily = activeFontFamily
                                    )
                                }
                                Spacer(
                                    modifier = Modifier.height(
                                        if (item.isTitle) maxOf(settings.paragraphSpacingDp, 14f).dp
                                        else settings.paragraphSpacingDp.dp
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(settings.theme.backgroundColor.copy(alpha = 0.94f))
                                .padding(top = topSafeDp, start = settings.horizontalPaddingDp.dp, end = settings.horizontalPaddingDp.dp)
                                .height(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentChapterTitle,
                                color = settings.theme.secondaryTextColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ReaderFooterRow(
                            progressPercent = currentBookProgress,
                            clockText = clockText,
                            batteryPercent = batteryPercent,
                            color = settings.theme.secondaryTextColor,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(settings.theme.backgroundColor.copy(alpha = 0.94f))
                                .padding(bottom = bottomSafeDp, start = settings.horizontalPaddingDp.dp, end = settings.horizontalPaddingDp.dp)
                        )
                    }
                }
            }
        }

        // Enlarged Floating AI TTS Player Capsule when speaking (easy to tap)
        AnimatedVisibility(
            visible = isTtsPlaying && !showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = 36.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, CircleShape)
                    .clip(CircleShape)
                    .background(solidGlassBarBg)
                    .border(0.9.dp, solidGlassBorder, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = settings.theme.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (isTtsPaused) "朗读已暂停" else "AI 朗读中 · ${String.format(Locale.US, "%.1fx", ttsSettings.speechRate)}",
                            color = settings.theme.textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (sleepRemainingSeconds > 0) {
                            Text(
                                text = "定时剩余 ${sleepRemainingSeconds / 60}:${String.format(Locale.US, "%02d", sleepRemainingSeconds % 60)}",
                                color = settings.theme.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(settings.theme.accentColor.copy(alpha = 0.16f))
                            .clickable { onPauseResumeTts() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTtsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "暂停/继续",
                            tint = settings.theme.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(settings.theme.textColor.copy(alpha = 0.09f))
                            .clickable { onOpenAiSettingsTab() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "AI朗读设置",
                            tint = settings.theme.textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FF3B30))
                            .clickable { onStopTts() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "停止",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Top Floating Liquid Glass Bar (Back, Title, AI Read, Search, Bookmark)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(solidGlassBarBg)
                        .border(0.9.dp, solidGlassBorder, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onExit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回书架",
                            tint = settings.theme.textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            color = settings.theme.textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chapters.getOrNull(chapterIndex)?.title ?: "",
                            color = settings.theme.secondaryTextColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // In-Book Search
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { showSearchSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "全书搜索",
                            tint = settings.theme.textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmark Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                val existing = activeBookmark
                                scope.launch {
                                    if (existing != null) {
                                        bookmarks = onRemoveBookmark(book.id, existing.id)
                                        Toast.makeText(context, "已取消书签", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val page = activePages.getOrNull(currentPageIndex)
                                        val excerpt = page?.slices?.joinToString("") { it.text }
                                            ?.trim()
                                            ?.take(48)
                                            ?.ifEmpty { chapters.getOrNull(chapterIndex)?.title ?: "" }
                                            ?: ""
                                        val bm = BookmarkItem(
                                            id = UUID.randomUUID().toString(),
                                            bookId = book.id,
                                            chapterIndex = chapterIndex,
                                            chapterTitle = chapters.getOrNull(chapterIndex)?.title ?: book.title,
                                            charOffset = page?.startOffset ?: targetCharOffset,
                                            excerpt = excerpt,
                                            progress = currentBookProgress
                                        )
                                        bookmarks = onAddBookmark(bm)
                                        Toast.makeText(context, "已添加书签", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeBookmark != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "书签",
                            tint = if (activeBookmark != null) settings.theme.accentColor else settings.theme.textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Clean Bottom Bar (目录 / 书签 / 听书 / 夜间 / 设置)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .shadow(20.dp, CircleShape)
                    .clip(CircleShape)
                    .background(solidGlassBarBg)
                    .border(0.9.dp, solidGlassBorder, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomDeckAction(
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    label = "目录",
                    tint = settings.theme.textColor,
                    onClick = {
                        directoryInitialTab = 0
                        showDirectorySheet = true
                    }
                )
                BottomDeckAction(
                    icon = Icons.Rounded.BookmarkBorder,
                    label = "书签",
                    tint = settings.theme.textColor,
                    onClick = {
                        directoryInitialTab = 1
                        showDirectorySheet = true
                    }
                )
                BottomDeckAction(
                    icon = if (isTtsPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Headphones,
                    label = if (isTtsPlaying) "停止" else "听书",
                    tint = if (isTtsPlaying) settings.theme.accentColor else settings.theme.textColor,
                    onClick = {
                        if (isTtsPlaying) onStopTts() else startTtsFromCurrentPosition()
                        showControls = false
                    }
                )
                BottomDeckAction(
                    icon = if (settings.theme.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    label = if (settings.theme.isDark) "日间" else "夜间",
                    tint = settings.theme.textColor,
                    onClick = {
                        onUpdateSettings {
                            it.copy(theme = if (it.theme.isDark) ReaderTheme.PAPER else ReaderTheme.MIDNIGHT)
                        }
                    }
                )
                BottomDeckAction(
                    icon = Icons.Rounded.Settings,
                    label = "设置",
                    tint = settings.theme.accentColor,
                    onClick = { showSettingsSheet = true }
                )
            }
        }
    }

    // Unified Solid Opaque Reader Settings Sheet
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = solidSheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "阅读设置",
                    color = settings.theme.textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingSliderRow(
                        title = "字号大小",
                        valueLabel = "${settings.fontSizeSp.toInt()}",
                        value = settings.fontSizeSp,
                        valueRange = 14f..40f,
                        stepDelta = 1f,
                        accentColor = settings.theme.accentColor,
                        textColor = settings.theme.textColor,
                        secondaryColor = settings.theme.secondaryTextColor,
                        onValueChange = { next ->
                            onUpdateSettings { it.copy(fontSizeSp = next.coerceIn(14f, 40f)) }
                        }
                    )

                    SettingSliderRow(
                        title = "行距倍率",
                        valueLabel = String.format(Locale.US, "%.1f", settings.lineHeightMultiplier),
                        value = settings.lineHeightMultiplier,
                        valueRange = 1.0f..2.2f,
                        stepDelta = 0.1f,
                        accentColor = settings.theme.accentColor,
                        textColor = settings.theme.textColor,
                        secondaryColor = settings.theme.secondaryTextColor,
                        onValueChange = { next ->
                            val rounded = kotlin.math.round(next * 10f) / 10f
                            onUpdateSettings { it.copy(lineHeightMultiplier = rounded.coerceIn(1.0f, 2.2f)) }
                        }
                    )

                    SettingSliderRow(
                        title = "段落间距",
                        valueLabel = "${settings.paragraphSpacingDp.toInt()}",
                        value = settings.paragraphSpacingDp,
                        valueRange = 0f..32f,
                        stepDelta = 2f,
                        accentColor = settings.theme.accentColor,
                        textColor = settings.theme.textColor,
                        secondaryColor = settings.theme.secondaryTextColor,
                        onValueChange = { next ->
                            onUpdateSettings { it.copy(paragraphSpacingDp = next.coerceIn(0f, 32f)) }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("翻页方式", color = settings.theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingMode.entries.forEach { mode ->
                            val selected = settings.readingMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) settings.theme.accentColor else solidSheetBg)
                                    .clickable { onUpdateSettings { it.copy(readingMode = mode) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (selected) Color.White else settings.theme.textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("阅读背景", color = settings.theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReaderTheme.entries.forEach { theme ->
                            val selected = settings.theme == theme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.backgroundColor)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) theme.accentColor else theme.secondaryTextColor.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onUpdateSettings { it.copy(theme = theme) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = theme.label,
                                    color = theme.textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("阅读字体", color = settings.theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "精选字体库 / 导入",
                            color = settings.theme.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    showSettingsSheet = false
                                    showFontManagerSheet = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fonts.forEach { font ->
                            val selected = settings.selectedFontId == font.id
                            val tf = remember(font.id) { resolveTypeface(font.id) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) settings.theme.accentColor else solidSheetBg)
                                    .clickable { onUpdateSettings { it.copy(selectedFontId = font.id) } }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = font.displayName,
                                    color = if (selected) Color.White else settings.theme.textColor,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily(tf),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFontManagerSheet) {
        FontManagerSheet(
            fonts = fonts,
            settings = settings,
            resolveTypeface = resolveTypeface,
            onSelectFont = { fontId ->
                onUpdateSettings { it.copy(selectedFontId = fontId) }
            },
            onDeleteFont = onDeleteFont,
            onImportLocalFont = onImportLocalFont,
            onOpenWifiTransfer = {
                showFontManagerSheet = false
                onGoWifiTransfer()
            },
            onDownloadCatalogFont = onDownloadCatalogFont,
            onDismiss = { showFontManagerSheet = false }
        )
    }

    // Directory & Bookmarks Sheet
    if (showDirectorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedSubTab by remember(directoryInitialTab) { mutableIntStateOf(directoryInitialTab) }
        val displayList = remember(chapters, reverseChapters) {
            if (reverseChapters) chapters.asReversed() else chapters
        }
        val listState = rememberLazyListState()

        LaunchedEffect(showDirectorySheet, selectedSubTab, reverseChapters) {
            if (selectedSubTab == 0) {
                val targetPos = displayList.indexOfFirst { it.index == chapterIndex }
                if (targetPos >= 0) {
                    listState.scrollToItem((targetPos - 3).coerceAtLeast(0))
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showDirectorySheet = false },
            sheetState = sheetState,
            containerColor = solidSheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(solidSheetCard)
                            .padding(4.dp)
                    ) {
                        listOf("目录 (${chapters.size})", "书签 (${bookmarks.size})").forEachIndexed { idx, label ->
                            val sel = selectedSubTab == idx
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (sel) settings.theme.accentColor else Color.Transparent)
                                    .clickable { selectedSubTab = idx }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (sel) Color.White else settings.theme.textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (selectedSubTab == 0) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(settings.theme.accentColor.copy(alpha = 0.14f))
                                .clickable { reverseChapters = !reverseChapters }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = "倒序", tint = settings.theme.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (reverseChapters) "倒序" else "正序", color = settings.theme.accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedSubTab == 0) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayList, key = { it.index }) { ch ->
                            val active = ch.index == chapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) settings.theme.accentColor.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable {
                                        jumpToLastPageOnChapterLoad = false
                                        targetCharOffset = 0
                                        chapterIndex = ch.index
                                        jumpNonce++
                                        showDirectorySheet = false
                                        showControls = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ch.title,
                                    color = if (active) settings.theme.accentColor else settings.theme.textColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (active) {
                                    Text("当前", color = settings.theme.accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无书签，点击阅读顶栏书签图标即可添加", color = settings.theme.secondaryTextColor, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(bookmarks, key = { it.id }) { bm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(solidSheetCard)
                                        .clickable {
                                            jumpToLastPageOnChapterLoad = false
                                            targetCharOffset = bm.charOffset
                                            chapterIndex = bm.chapterIndex
                                            jumpNonce++
                                            showDirectorySheet = false
                                            showControls = false
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = bm.chapterTitle,
                                                color = settings.theme.accentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f%%", bm.progress * 100f),
                                                color = settings.theme.secondaryTextColor,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(bm.excerpt, color = settings.theme.textColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(bm.createdAt)),
                                            color = settings.theme.secondaryTextColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { scope.launch { bookmarks = onRemoveBookmark(book.id, bm.id) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除书签", tint = settings.theme.secondaryTextColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // In-Book Search Sheet
    if (showSearchSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = sheetState,
            containerColor = solidSheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(horizontal = 20.dp)
            ) {
                Text("全书搜索", color = settings.theme.textColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(solidSheetCard)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = settings.theme.secondaryTextColor, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("输入人物、关键词全书检索...", color = settings.theme.secondaryTextColor, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = settings.theme.textColor, fontSize = 14.sp),
                            cursorBrush = SolidColor(settings.theme.accentColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清空",
                            tint = settings.theme.secondaryTextColor,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.accentColor)
                            .clickable {
                                val q = searchQuery.trim()
                                if (q.isNotEmpty()) {
                                    scope.launch {
                                        isSearching = true
                                        searchResults = onSearchBook(book, chapters, q)
                                        isSearching = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("搜索", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = settings.theme.accentColor)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "输入关键词并点击搜索" else "未找到相关内容",
                            color = settings.theme.secondaryTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text("找到 ${searchResults.size} 处匹配", color = settings.theme.secondaryTextColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults) { hit ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(solidSheetCard)
                                    .clickable {
                                        jumpToLastPageOnChapterLoad = false
                                        targetCharOffset = hit.charOffset
                                        chapterIndex = hit.chapterIndex
                                        jumpNonce++
                                        showSearchSheet = false
                                        showControls = false
                                    }
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = hit.chapterTitle,
                                    color = settings.theme.accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(hit.snippet, color = settings.theme.textColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberClockAndBattery(): Pair<String, Int> {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var battery by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(20_000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) {
                    battery = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                }
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) {
                battery = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return clock to battery
}

@Composable
private fun ReaderFooterRow(
    progressPercent: Float,
    clockText: String,
    batteryPercent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bottom-left: Book Reading Percentage
        Text(
            text = String.format(Locale.US, "%.1f%%", progressPercent * 100f),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        // Bottom-right: Current Time + Battery Level
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = clockText,
                color = color,
                fontSize = 11.sp
            )
            Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
                val bodyW = size.width - 2.5.dp.toPx()
                val bodyH = size.height
                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset.Zero,
                    size = Size(bodyW, bodyH),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(bodyW + 0.8.dp.toPx(), bodyH * 0.28f),
                    size = Size(1.6.dp.toPx(), bodyH * 0.44f),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
                val fillW = ((bodyW - 3.dp.toPx()) * (batteryPercent / 100f)).coerceAtLeast(1f)
                drawRoundRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                    size = Size(fillW, bodyH - 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
            }
            Text(
                text = "$batteryPercent%",
                color = color,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BottomDeckAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepDelta: Float,
    accentColor: Color,
    textColor: Color,
    secondaryColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = valueLabel, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.09f))
                    .clickable { onValueChange((value - stepDelta).coerceIn(valueRange.start, valueRange.endInclusive)) },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = secondaryColor.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.09f))
                    .clickable { onValueChange((value + stepDelta).coerceIn(valueRange.start, valueRange.endInclusive)) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReaderPageFrame(
    page: ReaderPage,
    chapterTitle: String,
    progressPercent: Float,
    clockText: String,
    batteryPercent: Int,
    highlightRange: IntRange?,
    topSafeDp: Dp,
    bottomSafeDp: Dp,
    horizontalPaddingDp: Float,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Float,
    typeface: Typeface,
    theme: ReaderTheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(top = topSafeDp, bottom = bottomSafeDp)
            .padding(horizontal = horizontalPaddingDp.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = chapterTitle,
                color = theme.secondaryTextColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val widthPx = constraints.maxWidth.coerceAtLeast(1)
            val textColorInt = theme.textColor.toArgb()
            val highlightColor = theme.accentColor.copy(alpha = 0.24f)

            val layouts = remember(
                page,
                widthPx,
                textSizePx,
                lineHeightMultiplier,
                typeface,
                textColorInt
            ) {
                val bodyPaint = TextPaint().apply {
                    isAntiAlias = true
                    color = textColorInt
                    this.textSize = textSizePx
                    this.typeface = typeface
                }
                val titlePaint = TextPaint().apply {
                    isAntiAlias = true
                    color = textColorInt
                    this.textSize = textSizePx * 1.16f
                    this.typeface = Typeface.create(typeface, Typeface.BOLD)
                }

                page.slices.map { slice ->
                    val paint = if (slice.isTitle) titlePaint else bodyPaint
                    val layout = StaticLayout.Builder
                        .obtain(slice.text, 0, slice.text.length, paint, widthPx)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, lineHeightMultiplier)
                        .setIncludePad(false)
                        .build()
                    val gap = if (slice.hasBottomGap) {
                        if (slice.isTitle) maxOf(paragraphSpacingPx, textSizePx * 0.55f) else paragraphSpacingPx
                    } else 0f
                    Triple(slice, layout, gap)
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val nc = drawContext.canvas.nativeCanvas
                var y = 0f
                for ((slice, layout, gap) in layouts) {
                    if (highlightRange != null &&
                        highlightRange.first < slice.endOffset &&
                        highlightRange.last + 1 > slice.startOffset
                    ) {
                        val localStart = (highlightRange.first - slice.startOffset).coerceIn(0, slice.text.length)
                        val localEnd = (highlightRange.last + 1 - slice.startOffset).coerceIn(0, slice.text.length)
                        if (localEnd > localStart) {
                            val selPath = AndroidPath()
                            layout.getSelectionPath(localStart, localEnd, selPath)
                            val composePath = selPath.asComposePath()
                            composePath.translate(Offset(0f, y))
                            drawPath(path = composePath, color = highlightColor)
                        }
                    }

                    nc.save()
                    nc.translate(0f, y)
                    layout.draw(nc)
                    nc.restore()
                    y += layout.height + gap
                }
            }
        }

        ReaderFooterRow(
            progressPercent = progressPercent,
            clockText = clockText,
            batteryPercent = batteryPercent,
            color = theme.secondaryTextColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Realistic 3D Curved Page Curl Pager:
 * - Curved Bezier peel spine responding dynamically to touch Y height
 * - Tilted curled back-of-page crescent flap with 3D cylindrical lighting & specular rim
 * - Soft curved drop shadow onto the underlying page
 * - Responsive ~8% drag or gentle flick velocity (> 380 px/s)
 */
@Composable
private fun SimulationCurlPager(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    prevChapterLastPage: ReaderPage?,
    nextChapterFirstPage: ReaderPage?,
    onPageChanged: (Int) -> Unit,
    onRequestPrevChapter: () -> Unit,
    onRequestNextChapter: () -> Unit,
    onToggleMenu: () -> Unit,
    hideMenuIfShown: () -> Boolean,
    theme: ReaderTheme,
    pageContent: @Composable (ReaderPage, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val curlAnim = remember { Animatable(0f) } // -1..0 turning next, 0..1 turning prev
    var dragDirection by remember { mutableIntStateOf(0) } // -1 next, +1 prev
    var touchYRatio by remember { mutableFloatStateOf(0.65f) }
    var isAnimating by remember { mutableStateOf(false) }

    val canGoNext = currentPageIndex < pages.lastIndex || nextChapterFirstPage != null
    val canGoPrev = currentPageIndex > 0 || prevChapterLastPage != null

    fun finishTurnNext() {
        if (currentPageIndex < pages.lastIndex) {
            onPageChanged(currentPageIndex + 1)
        } else if (nextChapterFirstPage != null) {
            onRequestNextChapter()
        }
    }

    fun finishTurnPrev() {
        if (currentPageIndex > 0) {
            onPageChanged(currentPageIndex - 1)
        } else if (prevChapterLastPage != null) {
            onRequestPrevChapter()
        }
    }

    fun animateTurnNext(yRatio: Float) {
        if (isAnimating || !canGoNext) return
        scope.launch {
            isAnimating = true
            touchYRatio = yRatio
            dragDirection = -1
            curlAnim.snapTo(0f)
            curlAnim.animateTo(-1f, tween(280))
            finishTurnNext()
            curlAnim.snapTo(0f)
            dragDirection = 0
            isAnimating = false
        }
    }

    fun animateTurnPrev(yRatio: Float) {
        if (isAnimating || !canGoPrev) return
        scope.launch {
            isAnimating = true
            touchYRatio = yRatio
            dragDirection = 1
            curlAnim.snapTo(1f)
            curlAnim.animateTo(0f, tween(280))
            finishTurnPrev()
            curlAnim.snapTo(0f)
            dragDirection = 0
            isAnimating = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentPageIndex, pages.size, prevChapterLastPage, nextChapterFirstPage) {
                detectTapGestures { offset ->
                    if (hideMenuIfShown()) return@detectTapGestures
                    val w = size.width.toFloat()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val yr = (offset.y / h).coerceIn(0.15f, 0.85f)
                    when {
                        offset.x < w * 0.30f -> animateTurnPrev(yr)
                        offset.x > w * 0.70f -> animateTurnNext(yr)
                        else -> onToggleMenu()
                    }
                }
            }
            .pointerInput(currentPageIndex, pages.size, prevChapterLastPage, nextChapterFirstPage) {
                var totalDragX = 0f
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { startPos ->
                        if (isAnimating) return@detectHorizontalDragGestures
                        totalDragX = 0f
                        dragDirection = 0
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        touchYRatio = (startPos.y / h).coerceIn(0.15f, 0.85f)
                        velocityTracker.resetTracking()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isAnimating) return@detectHorizontalDragGestures
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        touchYRatio = (change.position.y / h).coerceIn(0.15f, 0.85f)
                        totalDragX += dragAmount
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        if (dragDirection == 0) {
                            if (totalDragX < -5f && canGoNext) {
                                dragDirection = -1
                            } else if (totalDragX > 5f && canGoPrev) {
                                dragDirection = 1
                            }
                        }
                        if (dragDirection == -1 && canGoNext) {
                            val progress = (totalDragX / w).coerceIn(-1f, 0f)
                            scope.launch { curlAnim.snapTo(progress) }
                        } else if (dragDirection == 1 && canGoPrev) {
                            val progress = (1f - totalDragX / w).coerceIn(0f, 1f)
                            scope.launch { curlAnim.snapTo(progress) }
                        }
                    },
                    onDragEnd = {
                        if (isAnimating) return@detectHorizontalDragGestures
                        val vx = velocityTracker.calculateVelocity().x
                        val v = curlAnim.value
                        scope.launch {
                            isAnimating = true
                            if (dragDirection == -1 && canGoNext) {
                                if (v < -0.08f || vx < -380f) {
                                    curlAnim.animateTo(-1f, tween(210))
                                    finishTurnNext()
                                } else {
                                    curlAnim.animateTo(0f, tween(160))
                                }
                            } else if (dragDirection == 1 && canGoPrev) {
                                if (v < 0.92f || vx > 380f) {
                                    curlAnim.animateTo(0f, tween(210))
                                    finishTurnPrev()
                                } else {
                                    curlAnim.animateTo(1f, tween(160))
                                }
                            }
                            curlAnim.snapTo(0f)
                            dragDirection = 0
                            isAnimating = false
                        }
                    }
                )
            }
    ) {
        val curPage = pages[currentPageIndex]

        val (underPage, underChOffset) = when {
            dragDirection == -1 && currentPageIndex < pages.lastIndex -> pages[currentPageIndex + 1] to 0
            dragDirection == -1 && nextChapterFirstPage != null -> nextChapterFirstPage to 1
            else -> curPage to 0
        }

        val (topPage, topChOffset) = when {
            dragDirection == 1 && currentPageIndex > 0 -> pages[currentPageIndex - 1] to 0
            dragDirection == 1 && prevChapterLastPage != null -> prevChapterLastPage to -1
            else -> curPage to 0
        }

        // Underlying destination page
        Box(modifier = Modifier.fillMaxSize()) {
            pageContent(underPage, underChOffset)
        }

        val isTurning = dragDirection != 0 && !(underPage === topPage && underChOffset == topChOffset)
        if (isTurning) {
            val p = curlAnim.value
            val foldRatio = if (dragDirection == -1) (1f + p).coerceIn(0f, 1f) else (1f - p).coerceIn(0f, 1f)

            // Curved foreground page clip + realistic 3D curl lighting
            val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            // Tilt based on touchYRatio: touching lower pulls bottom corner ahead of top
            val tiltOffset = (touchYRatio - 0.5f) * wPx * 0.22f * (1f - abs(foldRatio - 0.5f) * 1.6f)
            val topX = (wPx * foldRatio + tiltOffset).coerceIn(0f, wPx)
            val bottomX = (wPx * foldRatio - tiltOffset).coerceIn(0f, wPx)
            val curveBulge = (wPx * 0.065f * (1f - abs(foldRatio - 0.5f) * 1.7f)).coerceAtLeast(0f)
            val midX = ((topX + bottomX) * 0.5f + curveBulge).coerceIn(0f, wPx)
            val midY = hPx * touchYRatio

            val flapWidth = (wPx * 0.16f * (1f - abs(foldRatio - 0.5f) * 1.5f)).coerceAtLeast(14f) *
                (if (foldRatio < 0.99f && foldRatio > 0.01f) 1f else 0f)

            Box(modifier = Modifier.fillMaxSize()) {
                // Clip top page cleanly along the curved curl boundary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val shadowPath = Path().apply {
                        moveTo(topX, 0f)
                        quadraticTo(midX, midY, bottomX, size.height)
                        lineTo((bottomX + flapWidth * 1.45f).coerceAtMost(size.width), size.height)
                        lineTo((topX + flapWidth * 1.45f).coerceAtMost(size.width), 0f)
                        close()
                    }
                    val shadowStart = minOf(topX, bottomX)
                    val shadowEnd = (maxOf(topX, bottomX) + flapWidth * 1.45f).coerceAtMost(size.width)
                    if (shadowEnd > shadowStart) {
                        drawPath(
                            path = shadowPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = if (theme.isDark) 0.58f else 0.28f),
                                    Color.Black.copy(alpha = if (theme.isDark) 0.20f else 0.08f),
                                    Color.Transparent
                                ),
                                startX = shadowStart,
                                endX = shadowEnd
                            )
                        )
                    }
                }

                // Foreground curling page rendered inside curved clip path
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(
                            androidx.compose.foundation.shape.GenericShape { size, _ ->
                                moveTo(0f, 0f)
                                lineTo(topX, 0f)
                                quadraticTo(midX, midY, bottomX, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                        )
                ) {
                    pageContent(topPage, topChOffset)
                }

                // Curled back-of-page 3D cylinder flap + specular highlight
                if (flapWidth > 2f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val flapTopL = (topX - flapWidth).coerceAtLeast(0f)
                        val flapMidL = (midX - flapWidth * 1.18f).coerceAtLeast(0f)
                        val flapBotL = (bottomX - flapWidth).coerceAtLeast(0f)

                        val flapPath = Path().apply {
                            moveTo(flapTopL, 0f)
                            lineTo(topX, 0f)
                            quadraticTo(midX, midY, bottomX, size.height)
                            lineTo(flapBotL, size.height)
                            quadraticTo(flapMidL, midY, flapTopL, 0f)
                            close()
                        }

                        val paperTone = if (theme.isDark) Color(0xFF1F232C) else theme.backgroundColor
                        val highlightTone = if (theme.isDark) Color(0xFF2D323E) else Color.White
                        val minX = minOf(flapTopL, flapMidL, flapBotL)
                        val maxX = maxOf(topX, midX, bottomX)

                        if (maxX > minX) {
                            drawPath(
                                path = flapPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.12f),
                                        paperTone.copy(alpha = 0.96f),
                                        highlightTone.copy(alpha = if (theme.isDark) 0.9f else 0.98f),
                                        paperTone.copy(alpha = 0.90f),
                                        Color.Black.copy(alpha = 0.22f)
                                    ),
                                    startX = minX,
                                    endX = maxX
                                )
                            )
                        }

                        // Subtle crease line along the inner fold
                        val creasePath = Path().apply {
                            moveTo(topX, 0f)
                            quadraticTo(midX, midY, bottomX, size.height)
                        }
                        drawPath(
                            path = creasePath,
                            color = Color.Black.copy(alpha = if (theme.isDark) 0.4f else 0.16f),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
