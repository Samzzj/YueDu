package com.yuedu.reader.ui.screens

import android.graphics.Typeface
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.model.AppAppearance
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.ui.theme.LocalLiquidGlass

@Composable
fun FontsSettingsScreen(
    fonts: List<CustomFont>,
    settings: ReaderSettings,
    isWifiRunning: Boolean,
    wifiServerUrl: String?,
    resolveTypeface: (String) -> Typeface,
    onImportLocalFont: () -> Unit,
    onStartWifiForFont: () -> Unit,
    onSelectFont: (String) -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onChangeAppearance: (AppAppearance) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showWifiFontDialog by remember { mutableStateOf(false) }

    // Solid opaque palette for Settings (no semi-transparency)
    val pageBackground = if (glass.isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val cardBackground = if (glass.isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val activeTypeface = remember(settings.selectedFontId, fonts) {
        resolveTypeface(settings.selectedFontId)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "字体与设置",
                    color = glass.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = "默认系统无衬线字体，支持本地与 Wi-Fi 导入自定义字体",
                    color = glass.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Import Actions Row: Local + Wi-Fi
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(glass.accent)
                        .clickable { onImportLocalFont() }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "本地导入字体",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBackground)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            onStartWifiForFont()
                            showWifiFontDialog = true
                        }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wi-Fi 导入字体",
                        color = glass.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Solid Preview Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "字体效果预览",
                    color = glass.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "　　落霞与孤鹜齐飞，秋水共长天一色。阅读是一场跨越时空的静谧对话，好字体让文字呼吸自如。\n　　The quick brown fox jumps over the lazy dog. 0123456789",
                    color = glass.textPrimary,
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                    fontFamily = FontFamily(activeTypeface)
                )
            }
        }

        // Solid App Appearance Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "应用外观",
                    color = glass.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppAppearance.entries.forEach { mode ->
                        val selected = settings.appAppearance == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) glass.accent
                                    else if (glass.isDark) Color(0xFF2C2C2E)
                                    else Color(0xFFF2F2F7)
                                )
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

        item {
            Text(
                text = "字体列表 (${fonts.size}) · 支持 .ttf / .otf / .ttc",
                color = glass.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        items(fonts, key = { it.id }) { font ->
            val selected = settings.selectedFontId == font.id
            val tf = remember(font.id) { resolveTypeface(font.id) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBackground)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) glass.accent else cardBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelectFont(font.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) glass.accent else glass.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FontDownload,
                        contentDescription = null,
                        tint = if (selected) Color.White else glass.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.displayName,
                        color = glass.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily(tf),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (font.isBuiltIn) "系统无衬线字体 (默认)" else font.fileName,
                        color = glass.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!font.isBuiltIn) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                            .clickable { onDeleteFont(font) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除字体",
                            tint = glass.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已选中",
                        tint = glass.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "悦读 YueDu v0.2.0 · 专注纯净 TXT 阅读、Wi-Fi 传书与自定义字体。",
                    color = glass.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showWifiFontDialog) {
        AlertDialog(
            onDismissRequest = { showWifiFontDialog = false },
            containerColor = cardBackground,
            title = { Text("Wi-Fi 导入字体", color = glass.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请保持手机与电脑连接同一 Wi-Fi，在电脑浏览器打开下方地址，点击「上传自定义字体」或直接拖入 .ttf / .otf / .ttc 文件：",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(glass.accentSoft)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isWifiRunning && wifiServerUrl != null) wifiServerUrl else "正在启动服务...",
                            color = glass.accent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (wifiServerUrl != null) {
                            clipboard.setText(AnnotatedString(wifiServerUrl))
                            Toast.makeText(context, "已复制 Wi-Fi 导入地址", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制地址")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWifiFontDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}
