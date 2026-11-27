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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
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
import com.yuedu.reader.model.AppAppearance
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.model.TtsSettings
import com.yuedu.reader.model.TtsVoiceOption
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import java.util.Locale

@Composable
fun AiReadingSettingsScreen(
    ttsSettings: TtsSettings,
    readerSettings: ReaderSettings,
    isTtsPlaying: Boolean,
    isTtsPaused: Boolean,
    sleepRemainingSeconds: Int,
    availableVoices: List<TtsVoiceOption>,
    downloadedPacks: Set<String>,
    packDownloadProgress: Map<String, Float>,
    ttsError: String?,
    onUpdateTtsSettings: ((TtsSettings) -> TtsSettings) -> Unit,
    onSelectAndPreviewVoice: (TtsVoiceOption) -> Unit,
    onDownloadVoicePack: (String) -> Unit,
    onPauseResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    onChangeAppearance: (AppAppearance) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val pageBackground = if (glass.isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val cardBackground = if (glass.isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val subPillBg = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "AI 阅读",
                    color = glass.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = "支持音色离线下载极速零延迟发声，点击音色即刻试听",
                    color = glass.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Active playback status bar (shown only when currently reading aloud or error occurs)
        if (isTtsPlaying || ttsError != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBackground)
                        .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(glass.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = glass.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    ttsError != null -> ttsError
                                    isTtsPaused -> "朗读已暂停"
                                    else -> "正在朗读中"
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(subPillBg)
                                    .clickable { onPauseResumeTts() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTtsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "暂停/继续",
                                    tint = glass.textPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(subPillBg)
                                    .clickable { onStopTts() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "停止", tint = glass.danger)
                            }
                        }
                    }
                }
            }
        }

        // Speech Rate Slider (Pitch removed per user request)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("播放倍速", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = String.format(Locale.US, "%.2fx", ttsSettings.speechRate),
                        color = glass.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
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
                        inactiveTrackColor = subPillBg
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
                                .background(if (selected) glass.accent else subPillBg)
                                .clickable { onUpdateTtsSettings { it.copy(speechRate = preset) } }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset}x",
                                color = if (selected) Color.White else glass.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Sleep Timer Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = glass.accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("定时关闭", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                val timerOptions = listOf(
                    0 to "不开启",
                    15 to "15 分钟",
                    30 to "30 分钟",
                    45 to "45 分钟",
                    60 to "60 分钟",
                    -1 to "读完本章"
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
                                        .background(if (selected) glass.accent else subPillBg)
                                        .clickable { onUpdateTtsSettings { it.copy(sleepTimerMinutes = mins) } }
                                        .padding(vertical = 10.dp),
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
        }

        // Auto Follow & Sentence Highlight Switches
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("播放自动跟随翻页", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("朗读到页尾或章末时自动切换下一页/下一章", color = glass.textSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = ttsSettings.autoFollow,
                        onCheckedChange = { checked ->
                            onUpdateTtsSettings { it.copy(autoFollow = checked) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("朗读句子高亮", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("实时高亮当前正在朗读的句子", color = glass.textSecondary, fontSize = 12.sp)
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

        // Voices Selector (Tap voice to select & immediately preview; Download button for local offline synthesis)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "音色选择",
                        color = glass.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "下载到本地后零延迟",
                        color = glass.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                availableVoices.forEach { voice ->
                    val selected = ttsSettings.voiceId == voice.id
                    val packId = voice.offlinePackId
                    val progress = packId?.let { packDownloadProgress[it] }
                    val isDownloaded = packId != null && packId in downloadedPacks

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) glass.accentSoft else subPillBg)
                            .border(
                                width = if (selected) 1.2.dp else 0.dp,
                                color = if (selected) glass.accent else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectAndPreviewVoice(voice) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = if (selected) glass.accent else glass.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayLabel,
                                    color = glass.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = voice.styleTag,
                                    color = glass.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (packId != null) {
                                when {
                                    progress != null -> {
                                        Text(
                                            text = "下载中 ${(progress * 100).toInt()}%",
                                            color = glass.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(cardBackground)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }

                                    isDownloaded -> {
                                        Text(
                                            text = "已离线",
                                            color = Color(0xFF28A745),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Color(0xFF28A745).copy(alpha = 0.14f))
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }

                                    else -> {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(glass.accent)
                                                .clickable { onDownloadVoicePack(packId) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = "下载到本地",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (voice.offlineSizeLabel.isNotBlank()) {
                                                    "下载到本地 ${voice.offlineSizeLabel}"
                                                } else {
                                                    "下载到本地"
                                                },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "系统内置",
                                    color = glass.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(cardBackground)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (progress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = glass.accent,
                                trackColor = cardBackground
                            )
                        }
                    }
                }
            }
        }

        // Global App Appearance
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text("应用外观", color = glass.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppAppearance.entries.forEach { mode ->
                        val selected = readerSettings.appAppearance == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) glass.accent else subPillBg)
                                .clickable { onChangeAppearance(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) Color.White else glass.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
