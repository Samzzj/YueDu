package com.yuedu.reader.ui.screens

import android.app.Activity
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookItem,
    settings: ReaderSettings,
    fonts: List<CustomFont>,
    resolveTypeface: (String) -> Typeface,
    loadChapters: suspend (String) -> List<ChapterInfo>,
    loadChapterText: suspend (BookItem, ChapterInfo) -> String,
    onSaveProgress: (bookId: String, chapterIdx: Int, charOffset: Int, progress: Float) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var chapters by remember(book.id) { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var chapterIndex by remember(book.id) { mutableIntStateOf(book.currentChapterIndex.coerceAtLeast(0)) }
    var targetCharOffset by remember(book.id) { mutableIntStateOf(book.currentCharOffset.coerceAtLeast(0)) }
    var chapterText by remember(book.id) { mutableStateOf("") }
    var pages by remember(book.id) { mutableStateOf<List<ReaderPage>>(emptyList()) }
    var isLoading by remember(book.id) { mutableStateOf(true) }
    var jumpToLastPageOnChapterLoad by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var reverseChapters by remember { mutableStateOf(false) }

    BackHandler {
        if (showChapterSheet) {
            showChapterSheet = false
        } else if (showControls) {
            showControls = false
        } else {
            onExit()
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

    // Load chapter text when chapterIndex changes
    LaunchedEffect(chapters, chapterIndex) {
        if (chapters.isEmpty()) return@LaunchedEffect
        isLoading = true
        val safeIdx = chapterIndex.coerceIn(0, chapters.lastIndex)
        chapterText = loadChapterText(book, chapters[safeIdx])
        isLoading = false
    }

    val activeTypeface = remember(settings.selectedFontId, fonts) {
        resolveTypeface(settings.selectedFontId)
    }
    val activeFontFamily = remember(activeTypeface) { FontFamily(activeTypeface) }

    val glassFill = if (settings.theme.isDark) {
        Color(0xD6181A20)
    } else {
        Color(0xDCF8F9FC)
    }
    val glassBorder = if (settings.theme.isDark) {
        Color(0x38FFFFFF)
    } else {
        Color(0xCCFFFFFF)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(settings.theme.backgroundColor)
    ) {
        val horizontalPadPx = with(density) { settings.horizontalPaddingDp.dp.roundToPx() }
        val topHeaderReservePx = with(density) { 46.dp.roundToPx() }
        val bottomFooterReservePx = with(density) { 36.dp.roundToPx() }
        val statusBarReservePx = with(density) { 28.dp.roundToPx() }

        val contentWidthPx = (constraints.maxWidth - horizontalPadPx * 2).coerceAtLeast(100)
        val contentHeightPx = (constraints.maxHeight - topHeaderReservePx - bottomFooterReservePx - statusBarReservePx).coerceAtLeast(200)
        val textSizePx = with(density) { settings.fontSizeSp.sp.toPx() }

        // Repaginate whenever text or typography changes
        LaunchedEffect(
            chapterText,
            contentWidthPx,
            contentHeightPx,
            textSizePx,
            settings.lineHeightMultiplier,
            activeTypeface
        ) {
            if (chapterText.isEmpty()) return@LaunchedEffect
            val computed = withContext(Dispatchers.Default) {
                TxtBookEngine.paginateChapter(
                    text = chapterText,
                    widthPx = contentWidthPx,
                    heightPx = contentHeightPx,
                    textSizePx = textSizePx,
                    lineHeightMultiplier = settings.lineHeightMultiplier,
                    typeface = activeTypeface
                )
            }
            pages = computed
        }

        if (isLoading || pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = settings.theme.accentColor)
            }
        } else {
            val currentChapterTitle = chapters.getOrNull(chapterIndex)?.title ?: book.title

            if (settings.readingMode == ReadingMode.PAGED_HORIZONTAL) {
                val initialPage = remember(pages, chapterIndex) {
                    if (jumpToLastPageOnChapterLoad) {
                        jumpToLastPageOnChapterLoad = false
                        pages.lastIndex
                    } else {
                        val idx = pages.indexOfFirst {
                            targetCharOffset >= it.startOffset && targetCharOffset < it.endOffset
                        }
                        if (idx >= 0) idx else 0
                    }
                }

                val pagerState = rememberPagerState(
                    initialPage = initialPage.coerceIn(0, pages.lastIndex),
                    pageCount = { pages.size }
                )

                LaunchedEffect(pages, initialPage) {
                    val safe = initialPage.coerceIn(0, pages.lastIndex)
                    if (pagerState.currentPage != safe) {
                        pagerState.scrollToPage(safe)
                    }
                }

                // Persist progress on page change
                LaunchedEffect(pagerState.currentPage, chapterIndex, pages) {
                    val page = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
                    targetCharOffset = page.startOffset
                    val total = chapters.size.coerceAtLeast(1)
                    val chapterSpan = 1f / total
                    val inChapter = (pagerState.currentPage + 1f) / pages.size.coerceAtLeast(1)
                    val progress = (chapterIndex * chapterSpan) + (inChapter * chapterSpan)
                    onSaveProgress(book.id, chapterIndex, page.startOffset, progress)
                }

                fun goPrevPage() {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    } else if (chapterIndex > 0) {
                        jumpToLastPageOnChapterLoad = true
                        targetCharOffset = 0
                        chapterIndex--
                    }
                }

                fun goNextPage() {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else if (chapterIndex < chapters.lastIndex) {
                        jumpToLastPageOnChapterLoad = false
                        targetCharOffset = 0
                        chapterIndex++
                    }
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
                                    offset.x < w * 0.30f -> goPrevPage()
                                    offset.x > w * 0.70f -> goNextPage()
                                    else -> showControls = true
                                }
                            }
                        }
                ) { pageIdx ->
                    val page = pages[pageIdx]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = settings.horizontalPaddingDp.dp)
                    ) {
                        // Subtle Top Chapter Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = currentChapterTitle,
                                color = settings.theme.secondaryTextColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Exact-fitted Page Text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Text(
                                text = page.text,
                                color = settings.theme.textColor,
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                                fontFamily = activeFontFamily
                            )
                        }

                        // Subtle Bottom Page Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = book.title,
                                color = settings.theme.secondaryTextColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${pageIdx + 1} / ${pages.size}",
                                color = settings.theme.secondaryTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Vertical Continuous Scroll Mode
                val scrollState = rememberScrollState()
                LaunchedEffect(chapterIndex) {
                    scrollState.scrollTo(0)
                    val total = chapters.size.coerceAtLeast(1)
                    val progress = (chapterIndex + 1f) / total
                    onSaveProgress(book.id, chapterIndex, 0, progress)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .verticalScroll(scrollState)
                        .pointerInput(Unit) {
                            detectTapGestures { showControls = !showControls }
                        }
                        .padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = currentChapterTitle,
                        color = settings.theme.textColor,
                        fontSize = (settings.fontSizeSp + 4f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = activeFontFamily
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = chapterText,
                        color = settings.theme.textColor,
                        fontSize = settings.fontSizeSp.sp,
                        lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                        fontFamily = activeFontFamily
                    )
                    Spacer(modifier = Modifier.height(28.dp))
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
                                    .clickable { chapterIndex-- }
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
                                    .clickable { chapterIndex++ }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("下一章", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // Top Floating Liquid Glass Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(glassFill)
                        .border(0.9.dp, glassBorder, CircleShape)
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
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.accentColor.copy(alpha = 0.14f))
                            .clickable { showChapterSheet = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                contentDescription = "目录",
                                tint = settings.theme.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "目录",
                                color = settings.theme.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Liquid Glass Control Deck
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .shadow(20.dp, RoundedCornerShape(30.dp))
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.verticalGradient(listOf(glassFill, glassFill.copy(alpha = 0.96f)))
                    )
                    .border(0.9.dp, glassBorder, RoundedCornerShape(30.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Chapter Stepper & Slider
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
                                    targetCharOffset = 0
                                    chapterIndex--
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Slider(
                            value = chapterIndex.toFloat(),
                            onValueChange = { chapterIndex = it.toInt().coerceIn(0, chapters.lastIndex) },
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
                                    targetCharOffset = 0
                                    chapterIndex++
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Font Size + Line Height + Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Font Size Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.textColor.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A-",
                            color = settings.theme.textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onUpdateSettings {
                                        it.copy(fontSizeSp = (it.fontSizeSp - 1f).coerceAtLeast(13f))
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "${settings.fontSizeSp.toInt()}",
                            color = settings.theme.secondaryTextColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Text(
                            text = "A+",
                            color = settings.theme.textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onUpdateSettings {
                                        it.copy(fontSizeSp = (it.fontSizeSp + 1f).coerceAtMost(32f))
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Line Spacing Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.textColor.copy(alpha = 0.08f))
                            .clickable {
                                val next = when {
                                    settings.lineHeightMultiplier < 1.5f -> 1.65f
                                    settings.lineHeightMultiplier < 1.8f -> 1.9f
                                    settings.lineHeightMultiplier < 2.0f -> 2.1f
                                    else -> 1.4f
                                }
                                onUpdateSettings { it.copy(lineHeightMultiplier = next) }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "行距 ${String.format("%.1f", settings.lineHeightMultiplier)}",
                            color = settings.theme.textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Reading Mode Toggle
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(settings.theme.accentColor.copy(alpha = 0.14f))
                            .clickable {
                                val next = if (settings.readingMode == ReadingMode.PAGED_HORIZONTAL) {
                                    ReadingMode.SCROLL_VERTICAL
                                } else {
                                    ReadingMode.PAGED_HORIZONTAL
                                }
                                onUpdateSettings { it.copy(readingMode = next) }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = settings.readingMode.label,
                            color = settings.theme.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 4 Paper Themes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        val selected = settings.theme == theme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(theme.backgroundColor)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) theme.accentColor else theme.secondaryTextColor.copy(alpha = 0.25f),
                                    shape = CircleShape
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

                // Quick Font Selector Strip (Built-in + Custom Imported Fonts)
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
                                .clip(CircleShape)
                                .background(
                                    if (selected) settings.theme.accentColor
                                    else settings.theme.textColor.copy(alpha = 0.08f)
                                )
                                .clickable { onUpdateSettings { it.copy(selectedFontId = font.id) } }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = font.displayName,
                                color = if (selected) Color.White else settings.theme.textColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily(tf),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Chapter Directory Modal Sheet
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
            containerColor = glassFill
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
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
