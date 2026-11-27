package com.yuedu.reader.ui.screens

import android.app.Activity
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.yuedu.reader.model.ChapterInfo
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.ReaderPage
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.model.ReaderTheme
import com.yuedu.reader.model.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: BookItem,
    settings: ReaderSettings,
    fonts: List<CustomFont>,
    resolveTypeface: (String) -> Typeface,
    loadChapters: suspend (String) -> List<ChapterInfo>,
    loadChapterParagraphs: suspend (BookItem, ChapterInfo) -> List<TxtBookEngine.ChapterParagraph>,
    onSaveProgress: (bookId: String, chapterIdx: Int, charOffset: Int, progress: Float) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onGoImportFonts: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var chapters by remember(book.id) { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var chapterIndex by remember(book.id) { mutableIntStateOf(book.currentChapterIndex.coerceAtLeast(0)) }
    var targetCharOffset by remember(book.id) { mutableIntStateOf(book.currentCharOffset.coerceAtLeast(0)) }
    var chapterParagraphs by remember(book.id) { mutableStateOf<List<TxtBookEngine.ChapterParagraph>>(emptyList()) }
    var pages by remember(book.id) { mutableStateOf<List<ReaderPage>>(emptyList()) }
    var currentPageIndex by remember(book.id) { mutableIntStateOf(0) }
    var isLoading by remember(book.id) { mutableStateOf(true) }
    var jumpToLastPageOnChapterLoad by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var reverseChapters by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showSettingsSheet -> showSettingsSheet = false
            showChapterSheet -> showChapterSheet = false
            showControls -> showControls = false
            else -> onExit()
        }
    }

    // Immersive system bars control
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

    // Load chapter list once
    LaunchedEffect(book.id) {
        isLoading = true
        val loaded = loadChapters(book.id)
        chapters = loaded
        if (chapterIndex >= loaded.size && loaded.isNotEmpty()) {
            chapterIndex = loaded.lastIndex
        }
    }

    // Load chapter paragraphs when chapterIndex changes
    LaunchedEffect(chapters, chapterIndex) {
        if (chapters.isEmpty()) return@LaunchedEffect
        isLoading = true
        val safeIdx = chapterIndex.coerceIn(0, chapters.lastIndex)
        chapterParagraphs = loadChapterParagraphs(book, chapters[safeIdx])
        isLoading = false
    }

    val activeTypeface = remember(settings.selectedFontId, fonts) {
        resolveTypeface(settings.selectedFontId)
    }

    // Safe top/bottom insets ignoring status bar visibility so punch-hole/Dynamic Island NEVER clips text
    val topSafeInsets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val bottomSafeInsets = WindowInsets.navigationBarsIgnoringVisibility
    val topSafeDp: Dp = maxOf(topSafeInsets.asPaddingValues().calculateTopPadding(), 32.dp) + 6.dp
    val bottomSafeDp: Dp = maxOf(bottomSafeInsets.asPaddingValues().calculateBottomPadding(), 12.dp)

    // Solid opaque panel colors for Settings & Directory
    val solidSheetBg = if (settings.theme.isDark) Color(0xFF1A1C22) else Color(0xFFFFFFFF)
    val solidSheetCard = if (settings.theme.isDark) Color(0xFF252830) else Color(0xFFF2F2F7)
    val solidGlassBarBg = if (settings.theme.isDark) Color(0xE8181A20) else Color(0xEAF8F9FC)
    val solidGlassBorder = if (settings.theme.isDark) Color(0x36FFFFFF) else Color(0xCCFFFFFF)

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

        // Repaginate accurately when typography or chapter changes
        LaunchedEffect(
            chapterParagraphs,
            contentWidthPx,
            contentHeightPx,
            textSizePx,
            settings.lineHeightMultiplier,
            paragraphSpacingPx,
            activeTypeface
        ) {
            if (chapterParagraphs.isEmpty()) return@LaunchedEffect
            val computed = withContext(Dispatchers.Default) {
                TxtBookEngine.paginateParagraphs(
                    paragraphs = chapterParagraphs,
                    widthPx = contentWidthPx,
                    heightPx = contentHeightPx,
                    textSizePx = textSizePx,
                    lineHeightMultiplier = settings.lineHeightMultiplier,
                    paragraphSpacingPx = paragraphSpacingPx,
                    typeface = activeTypeface
                )
            }
            pages = computed
            currentPageIndex = if (jumpToLastPageOnChapterLoad) {
                jumpToLastPageOnChapterLoad = false
                computed.lastIndex
            } else {
                val idx = computed.indexOfFirst {
                    targetCharOffset >= it.startOffset && targetCharOffset < it.endOffset
                }
                if (idx >= 0) idx else 0
            }
        }

        // Persist progress whenever currentPageIndex or chapterIndex changes
        LaunchedEffect(currentPageIndex, chapterIndex, pages) {
            val page = pages.getOrNull(currentPageIndex) ?: return@LaunchedEffect
            targetCharOffset = page.startOffset
            val total = chapters.size.coerceAtLeast(1)
            val chapterSpan = 1f / total
            val inChapter = (currentPageIndex + 1f) / pages.size.coerceAtLeast(1)
            val progress = (chapterIndex * chapterSpan) + (inChapter * chapterSpan)
            onSaveProgress(book.id, chapterIndex, page.startOffset, progress)
        }

        if (isLoading || pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = settings.theme.accentColor)
            }
        } else {
            val currentChapterTitle = chapters.getOrNull(chapterIndex)?.title ?: book.title

            fun goPrevChapterLastPage() {
                if (chapterIndex > 0) {
                    jumpToLastPageOnChapterLoad = true
                    targetCharOffset = 0
                    chapterIndex--
                }
            }

            fun goNextChapterFirstPage() {
                if (chapterIndex < chapters.lastIndex) {
                    jumpToLastPageOnChapterLoad = false
                    targetCharOffset = 0
                    chapterIndex++
                }
            }

            when (settings.readingMode) {
                ReadingMode.SMOOTH -> {
                    val pagerState = rememberPagerState(
                        initialPage = currentPageIndex.coerceIn(0, pages.lastIndex),
                        pageCount = { pages.size }
                    )

                    LaunchedEffect(pages, currentPageIndex) {
                        val safe = currentPageIndex.coerceIn(0, pages.lastIndex)
                        if (pagerState.currentPage != safe) {
                            pagerState.scrollToPage(safe)
                        }
                    }

                    LaunchedEffect(pagerState.currentPage) {
                        currentPageIndex = pagerState.currentPage
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pages.size, chapterIndex, showControls) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    when {
                                        showControls -> showControls = false
                                        offset.x < w * 0.30f -> {
                                            if (pagerState.currentPage > 0) {
                                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                            } else {
                                                goPrevChapterLastPage()
                                            }
                                        }
                                        offset.x > w * 0.70f -> {
                                            if (pagerState.currentPage < pages.lastIndex) {
                                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            } else {
                                                goNextChapterFirstPage()
                                            }
                                        }
                                        else -> showControls = true
                                    }
                                }
                            }
                    ) { pageIdx ->
                        ReaderPageFrame(
                            page = pages[pageIdx],
                            pageNumber = pageIdx + 1,
                            totalPages = pages.size,
                            bookTitle = book.title,
                            chapterTitle = currentChapterTitle,
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
                    SimulationCurlPager(
                        pages = pages,
                        currentPageIndex = currentPageIndex.coerceIn(0, pages.lastIndex),
                        hasPrevChapter = chapterIndex > 0,
                        hasNextChapter = chapterIndex < chapters.lastIndex,
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
                    ) { pageIdx ->
                        ReaderPageFrame(
                            page = pages[pageIdx],
                            pageNumber = pageIdx + 1,
                            totalPages = pages.size,
                            bookTitle = book.title,
                            chapterTitle = currentChapterTitle,
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
                    val scrollState = rememberScrollState()
                    LaunchedEffect(chapterIndex) {
                        scrollState.scrollTo(0)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topSafeDp, bottom = bottomSafeDp)
                            .verticalScroll(scrollState)
                            .pointerInput(Unit) {
                                detectTapGestures { showControls = !showControls }
                            }
                            .padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 12.dp)
                    ) {
                        val activeFontFamily = remember(activeTypeface) { FontFamily(activeTypeface) }
                        chapterParagraphs.forEach { para ->
                            Text(
                                text = para.text,
                                color = settings.theme.textColor,
                                fontSize = if (para.isTitle) (settings.fontSizeSp * 1.16f).sp else settings.fontSizeSp.sp,
                                fontWeight = if (para.isTitle) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = ((if (para.isTitle) settings.fontSizeSp * 1.16f else settings.fontSizeSp) * settings.lineHeightMultiplier).sp,
                                fontFamily = activeFontFamily
                            )
                            Spacer(
                                modifier = Modifier.height(
                                    if (para.isTitle) maxOf(settings.paragraphSpacingDp, 14f).dp
                                    else settings.paragraphSpacingDp.dp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (chapterIndex > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(settings.theme.accentColor.copy(alpha = 0.14f))
                                        .clickable { goPrevChapterLastPage() }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("上一章", color = settings.theme.accentColor, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (chapterIndex < chapters.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(settings.theme.accentColor)
                                        .clickable { goNextChapterFirstPage() }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("下一章", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Top Floating Liquid Glass Bar (Quick navigation)
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
                    Text(
                        text = "${currentPageIndex + 1}/${pages.size.coerceAtLeast(1)}",
                        color = settings.theme.secondaryTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }

        // Clean Bottom Bar (Chapter Slider + 3 Uncluttered Actions: 目录 / 夜间 / 设置)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .shadow(20.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(solidGlassBarBg)
                    .border(0.9.dp, solidGlassBorder, RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (chapters.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "上一章",
                            color = if (chapterIndex > 0) settings.theme.accentColor else settings.theme.secondaryTextColor.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = chapterIndex > 0) {
                                    jumpToLastPageOnChapterLoad = false
                                    targetCharOffset = 0
                                    chapterIndex--
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Slider(
                            value = chapterIndex.toFloat(),
                            onValueChange = {
                                jumpToLastPageOnChapterLoad = false
                                targetCharOffset = 0
                                chapterIndex = it.toInt().coerceIn(0, chapters.lastIndex)
                            },
                            valueRange = 0f..chapters.lastIndex.coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = settings.theme.accentColor,
                                activeTrackColor = settings.theme.accentColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "下一章",
                            color = if (chapterIndex < chapters.lastIndex) settings.theme.accentColor else settings.theme.secondaryTextColor.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = chapterIndex < chapters.lastIndex) {
                                    jumpToLastPageOnChapterLoad = false
                                    targetCharOffset = 0
                                    chapterIndex++
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BottomDeckAction(
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        label = "目录",
                        tint = settings.theme.textColor,
                        onClick = { showChapterSheet = true }
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

                // Typography Sliders Group (Font Size, Line Height, Paragraph Spacing)
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

                // Page Turn Mode (Simulation, Smooth, Vertical)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "翻页方式",
                        color = settings.theme.textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                                    .background(
                                        if (selected) settings.theme.accentColor
                                        else solidSheetBg
                                    )
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

                // Paper Theme
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(solidSheetCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "阅读背景",
                        color = settings.theme.textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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

                // Font Selector + Import Shortcut
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
                        Text(
                            text = "阅读字体",
                            color = settings.theme.textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "导入字体 (本地/Wi-Fi)",
                            color = settings.theme.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    showSettingsSheet = false
                                    onGoImportFonts()
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

    // Solid Opaque Chapter Directory Sheet
    if (showChapterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val displayList = remember(chapters, reverseChapters) {
            if (reverseChapters) chapters.asReversed() else chapters
        }
        val listState = rememberLazyListState()

        LaunchedEffect(showChapterSheet, reverseChapters) {
            val targetPos = displayList.indexOfFirst { it.index == chapterIndex }
            if (targetPos >= 0) {
                listState.scrollToItem((targetPos - 3).coerceAtLeast(0))
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showChapterSheet = false },
            sheetState = sheetState,
            containerColor = solidSheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "章节目录 (${chapters.size})",
                        color = settings.theme.textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.accentColor.copy(alpha = 0.14f))
                            .clickable { reverseChapters = !reverseChapters }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = "倒序",
                            tint = settings.theme.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (reverseChapters) "倒序" else "正序",
                            color = settings.theme.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                                .background(
                                    if (active) settings.theme.accentColor.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    jumpToLastPageOnChapterLoad = false
                                    targetCharOffset = 0
                                    chapterIndex = ch.index
                                    showChapterSheet = false
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
                                Text(
                                    text = "当前",
                                    color = settings.theme.accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(3.dp))
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
    pageNumber: Int,
    totalPages: Int,
    bookTitle: String,
    chapterTitle: String,
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
                    layout to gap
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val nc = drawContext.canvas.nativeCanvas
                var y = 0f
                for ((layout, gap) in layouts) {
                    nc.save()
                    nc.translate(0f, y)
                    layout.draw(nc)
                    nc.restore()
                    y += layout.height + gap
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bookTitle,
                color = theme.secondaryTextColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$pageNumber / $totalPages",
                color = theme.secondaryTextColor,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Realistic simulation page-curl pager with interactive drag peel, reverse-page edge, and cast shadow.
 */
@Composable
private fun SimulationCurlPager(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onPageChanged: (Int) -> Unit,
    onRequestPrevChapter: () -> Unit,
    onRequestNextChapter: () -> Unit,
    onToggleMenu: () -> Unit,
    hideMenuIfShown: () -> Boolean,
    theme: ReaderTheme,
    pageContent: @Composable (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val curlAnim = remember { Animatable(0f) } // -1..0 (next page peeling left), 0..1 (prev page pulling right)
    var dragDirection by remember { mutableIntStateOf(0) } // -1 = turning to next, +1 = turning to prev
    var isAnimating by remember { mutableStateOf(false) }

    fun animateTurnNext() {
        if (isAnimating) return
        if (currentPageIndex < pages.lastIndex) {
            scope.launch {
                isAnimating = true
                dragDirection = -1
                curlAnim.snapTo(0f)
                curlAnim.animateTo(-1f, tween(320))
                onPageChanged(currentPageIndex + 1)
                curlAnim.snapTo(0f)
                dragDirection = 0
                isAnimating = false
            }
        } else if (hasNextChapter) {
            onRequestNextChapter()
        }
    }

    fun animateTurnPrev() {
        if (isAnimating) return
        if (currentPageIndex > 0) {
            scope.launch {
                isAnimating = true
                dragDirection = 1
                curlAnim.snapTo(1f)
                curlAnim.animateTo(0f, tween(320))
                onPageChanged(currentPageIndex - 1)
                curlAnim.snapTo(0f)
                dragDirection = 0
                isAnimating = false
            }
        } else if (hasPrevChapter) {
            onRequestPrevChapter()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentPageIndex, pages.size, hasPrevChapter, hasNextChapter) {
                detectTapGestures { offset ->
                    if (hideMenuIfShown()) return@detectTapGestures
                    val w = size.width.toFloat()
                    when {
                        offset.x < w * 0.30f -> animateTurnPrev()
                        offset.x > w * 0.70f -> animateTurnNext()
                        else -> onToggleMenu()
                    }
                }
            }
            .pointerInput(currentPageIndex, pages.size, hasPrevChapter, hasNextChapter) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (isAnimating) return@detectHorizontalDragGestures
                        totalDragX = 0f
                        dragDirection = 0
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isAnimating) return@detectHorizontalDragGestures
                        change.consume()
                        totalDragX += dragAmount
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        if (dragDirection == 0) {
                            if (totalDragX < -6f && (currentPageIndex < pages.lastIndex || hasNextChapter)) {
                                dragDirection = -1
                            } else if (totalDragX > 6f && currentPageIndex > 0) {
                                dragDirection = 1
                            }
                        }
                        if (dragDirection == -1 && currentPageIndex < pages.lastIndex) {
                            val progress = (totalDragX / w).coerceIn(-1f, 0f)
                            scope.launch { curlAnim.snapTo(progress) }
                        } else if (dragDirection == 1 && currentPageIndex > 0) {
                            val progress = (1f - totalDragX / w).coerceIn(0f, 1f)
                            scope.launch { curlAnim.snapTo(progress) }
                        }
                    },
                    onDragEnd = {
                        if (isAnimating) return@detectHorizontalDragGestures
                        val v = curlAnim.value
                        scope.launch {
                            isAnimating = true
                            if (dragDirection == -1) {
                                if (currentPageIndex < pages.lastIndex) {
                                    if (v < -0.18f) {
                                        curlAnim.animateTo(-1f, tween(220))
                                        onPageChanged(currentPageIndex + 1)
                                    } else {
                                        curlAnim.animateTo(0f, tween(180))
                                    }
                                } else if (totalDragX < -80f && hasNextChapter) {
                                    onRequestNextChapter()
                                }
                            } else if (dragDirection == 1) {
                                if (currentPageIndex > 0) {
                                    if (v < 0.82f) {
                                        curlAnim.animateTo(0f, tween(220))
                                        onPageChanged(currentPageIndex - 1)
                                    } else {
                                        curlAnim.animateTo(1f, tween(180))
                                    }
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
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val p = curlAnim.value // -1..0 when turning next, 0..1 when turning prev

        val underIdx = when {
            dragDirection == -1 && currentPageIndex < pages.lastIndex -> currentPageIndex + 1
            else -> currentPageIndex
        }
        val topIdx = when {
            dragDirection == 1 && currentPageIndex > 0 -> currentPageIndex - 1
            else -> currentPageIndex
        }

        // Underlying page
        Box(modifier = Modifier.fillMaxSize()) {
            pageContent(underIdx)
        }

        // Peeling top page + curl shadow & reverse fold strip
        if (dragDirection != 0 && (underIdx != topIdx)) {
            val foldRatio = if (dragDirection == -1) (1f + p).coerceIn(0f, 1f) else (1f - p).coerceIn(0f, 1f)
            val foldX = widthPx * foldRatio
            val curlWidthPx = (widthPx * 0.10f * (1f - abs(foldRatio - 0.5f) * 1.4f)).coerceAtLeast(18f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = foldX - widthPx
                    }
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = widthPx - foldX
                        }
                ) {
                    pageContent(topIdx)
                }
            }

            // Realistic curl edge & cast shadow onto underlying page
            Canvas(modifier = Modifier.fillMaxSize()) {
                val edgeX = foldX.coerceIn(0f, size.width)
                // Shadow cast onto underlying page to the right of the curl
                val shadowW = (size.width * 0.08f).coerceAtLeast(24f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (theme.isDark) 0.55f else 0.22f),
                            Color.Transparent
                        ),
                        startX = edgeX,
                        endX = (edgeX + shadowW).coerceAtMost(size.width)
                    ),
                    topLeft = Offset(edgeX, 0f),
                    size = Size(shadowW, size.height)
                )

                // Curled back-of-page highlight strip to the left of fold edge
                val stripStart = (edgeX - curlWidthPx).coerceAtLeast(0f)
                val paperBack = if (theme.isDark) Color(0xFF1F232B) else theme.backgroundColor.copy(alpha = 0.94f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.10f),
                            paperBack,
                            Color.White.copy(alpha = if (theme.isDark) 0.08f else 0.45f),
                            Color.Black.copy(alpha = 0.14f)
                        ),
                        startX = stripStart,
                        endX = edgeX
                    ),
                    topLeft = Offset(stripStart, 0f),
                    size = Size((edgeX - stripStart).coerceAtLeast(0f), size.height)
                )
            }
        }
    }
}
