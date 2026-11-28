package com.yuedu.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.model.TtsSettings
import com.yuedu.reader.model.TtsVoiceOption
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import java.util.Locale

@Composable
fun AiReadingSettingsScreen(
    ttsSettings: TtsSettings,
    isTtsPlaying: Boolean,
    isTtsPaused: Boolean,
    sleepRemainingSeconds: Int,
    availableVoices: List<TtsVoiceOption>,
    books: List<BookItem>,
    cachedChapterCounts: Map<String, Int>,
    bookDownloadProgress: Map<String, Float>,
    totalCacheBytes: Long,
    ttsError: String?,
    onUpdateTtsSettings: ((TtsSettings) -> TtsSettings) -> Unit,
    onSelectAndPreviewVoice: (TtsVoiceOption) -> Unit,
    onDownloadBookChapters: (BookItem) -> Unit,
    onCancelBookDownload: (String) -> Unit,
    onClearAudioCache: () -> Unit,
    onPauseResumeTts: () -> Unit,
    onStopTts: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val pageBg = if (glass.isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val cardBg = if (glass.isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val dividerColor = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val pillBg = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Header
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "AI 阅读",
                color = glass.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
        }

        // Live Playback Banner (only shown during playback or error)
        if (isTtsPlaying || ttsError != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    ttsError != null -> ttsError
                                    isTtsPaused -> "朗读已暂停"
                                    else -> "正在后台朗读"
                                },
                                color = if (ttsError != null) glass.danger else glass.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (sleepRemainingSeconds > 0) {
                                val m = sleepRemainingSeconds / 60
                                val s = sleepRemainingSeconds % 60
                                Text(
                                    text = "定时关闭 ${String.format(Locale.US, "%02d:%02d", m, s)}",
                                    color = glass.accent,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (isTtsPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .clickable { onPauseResumeTts() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTtsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "暂停/继续",
                                    tint = glass.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .clickable { onStopTts() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "停止",
                                    tint = glass.danger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Playback Controls (iOS Grouped Inset List)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(title = "朗读设置", color = glass.textSecondary)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                ) {
                    // Speed Control Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("播放倍速", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = String.format(Locale.US, "%.2fx", ttsSettings.speechRate),
                                color = glass.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Slider(
                            value = ttsSettings.speechRate,
                            onValueChange = { raw ->
                                val stepped = kotlin.math.round(raw * 20f) / 20f
                                onUpdateTtsSettings { it.copy(speechRate = stepped.coerceIn(0.5f, 2.5f)) }
                            },
                            valueRange = 0.5f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = glass.accent,
                                activeTrackColor = glass.accent,
                                inactiveTrackColor = pillBg
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                val selected = kotlin.math.abs(ttsSettings.speechRate - preset) < 0.03f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) glass.accent else pillBg)
                                        .clickable { onUpdateTtsSettings { it.copy(speechRate = preset) } }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${preset}x",
                                        color = if (selected) Color.White else glass.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = dividerColor
                    )

                    // Sleep Timer Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("定时关闭", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)

                        val timerOptions = listOf(
                            0 to "关闭",
                            15 to "15分钟",
                            30 to "30分钟",
                            45 to "45分钟",
                            60 to "60分钟",
                            -1 to "本章末"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            timerOptions.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (mins, label) ->
                                        val selected = ttsSettings.sleepTimerMinutes == mins
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (selected) glass.accent else pillBg)
                                                .clickable { onUpdateTtsSettings { it.copy(sleepTimerMinutes = mins) } }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (selected) Color.White else glass.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = dividerColor
                    )

                    // Auto Follow Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("播放自动跟随翻页", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("章末自动连续朗读下一章", color = glass.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = ttsSettings.autoFollow,
                            onCheckedChange = { checked ->
                                onUpdateTtsSettings { it.copy(autoFollow = checked) }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = dividerColor
                    )

                    // Sentence Highlight Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("朗读句子高亮", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("同步高亮当前正在朗读的句子", color = glass.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = ttsSettings.highlightSentence,
                            onCheckedChange = { checked ->
                                onUpdateTtsSettings { it.copy(highlightSentence = checked) }
                            }
                        )
                    }
                }
            }
        }

        // Section 2: Unified VPS Edge ReadAloud AI Voices (Preview & Reading & Offline Audio all sound identical)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 音色",
                        color = glass.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "微软原声 · 试听与下载同音",
                        color = glass.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                ) {
                    availableVoices.forEachIndexed { idx, voice ->
                        val selected = ttsSettings.voiceId == voice.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) glass.accentSoft.copy(alpha = 0.45f) else Color.Transparent)
                                .clickable { onSelectAndPreviewVoice(voice) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) glass.accent else pillBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = if (selected) Color.White else glass.textSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayLabel,
                                    color = glass.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = voice.styleTag,
                                    color = glass.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = if (selected) "使用中" else "试听",
                                color = if (selected) Color.White else glass.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) glass.accent else glass.accentSoft)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (idx < availableVoices.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 62.dp),
                                thickness = 0.5.dp,
                                color = dividerColor
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Offline Chapter Audio Download (Uses exact same VPS Edge ReadAloud MP3s as previews!)
        if (books.isNotEmpty()) {
            item {
                val activeVoiceLabel = availableVoices.find { it.id == ttsSettings.voiceId }?.displayLabel ?: "云希"
                val cacheMb = String.format(Locale.US, "%.1f MB", totalCacheBytes / (1024f * 1024f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "离线听书下载（$activeVoiceLabel · ${String.format(Locale.US, "%.2fx", ttsSettings.speechRate)}）",
                            color = glass.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (totalCacheBytes > 0L) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onClearAudioCache() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "清理缓存",
                                    tint = glass.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "清理 $cacheMb",
                                    color = glass.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                    ) {
                        books.take(8).forEachIndexed { idx, book ->
                            val cachedCount = cachedChapterCounts[book.id] ?: 0
                            val totalChapters = book.chapterCount.coerceAtLeast(1)
                            val progress = bookDownloadProgress[book.id]
                            val allDone = cachedCount >= totalChapters

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(pillBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = glass.accent,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = book.title,
                                            color = glass.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (cachedCount > 0) "已离线缓存 $cachedCount / $totalChapters 章" else "未下载离线音频 · 共 $totalChapters 章",
                                            color = glass.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    when {
                                        progress != null -> {
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(pillBg)
                                                    .clickable { onCancelBookDownload(book.id) }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    color = glass.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "取消",
                                                    tint = glass.textSecondary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }

                                        allDone -> {
                                            Text(
                                                text = "全本已缓存",
                                                color = Color(0xFF28A745),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF28A745).copy(alpha = 0.14f))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        else -> {
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(glass.accent)
                                                    .clickable { onDownloadBookChapters(book) }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = "下载章节音频",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (cachedCount > 0) "再下 5 章" else "下载 5 章",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (progress != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(CircleShape),
                                        color = glass.accent,
                                        trackColor = pillBg
                                    )
                                }
                            }

                            if (idx < minOf(books.size, 8) - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 62.dp),
                                    thickness = 0.5.dp,
                                    color = dividerColor
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
private fun SectionLabel(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
