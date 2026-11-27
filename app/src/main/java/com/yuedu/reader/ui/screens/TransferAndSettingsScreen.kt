package com.yuedu.reader.ui.screens

import android.app.Activity
import android.graphics.Typeface
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.yuedu.reader.model.TransferKind
import com.yuedu.reader.model.WifiTransferLog
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransferAndSettingsScreen(
    isWifiRunning: Boolean,
    serverUrl: String?,
    recentTransfers: List<WifiTransferLog>,
    fonts: List<CustomFont>,
    settings: ReaderSettings,
    resolveTypeface: (String) -> Typeface,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRefreshIp: () -> Unit,
    onOpenUploadedBook: (String) -> Unit,
    onImportLocalFont: () -> Unit,
    onSelectFont: (String) -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onChangeAppearance: (AppAppearance) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Solid opaque palette (no semi-transparency on settings screen)
    val pageBackground = if (glass.isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val cardBackground = if (glass.isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val subPillBg = if (glass.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    LaunchedEffect(Unit) {
        if (!isWifiRunning) {
            onStartServer()
        } else {
            onRefreshIp()
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val activeTypeface = rememberTypeface(settings.selectedFontId, fonts, resolveTypeface)

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
                    text = "传书与设置",
                    color = glass.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = "同 Wi-Fi 上传书籍或字体，管理阅读字体与外观",
                    color = glass.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Section 1: Wi-Fi Transfer Card (Books & Fonts)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = glass.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Wi-Fi 传书与传字体",
                                color = glass.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .alpha(if (isWifiRunning) pulseAlpha else 1f)
                                        .clip(CircleShape)
                                        .background(if (isWifiRunning) glass.success else glass.textSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isWifiRunning) "服务运行中 · 支持 .txt / .ttf / .otf" else "服务已暂停",
                                    color = if (isWifiRunning) glass.success else glass.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isWifiRunning) subPillBg else glass.accent)
                            .clickable { if (isWifiRunning) onStopServer() else onStartServer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "开关服务",
                            tint = if (isWifiRunning) glass.success else Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glass.accentSoft)
                        .padding(vertical = 12.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = serverUrl ?: "服务未启动",
                        color = glass.accent,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(glass.accent)
                            .clickable {
                                if (serverUrl != null) {
                                    clipboard.setText(AnnotatedString(serverUrl))
                                    Toast.makeText(context, "已复制传书地址", Toast.LENGTH_SHORT).show()
                                }
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制地址", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(subPillBg)
                            .clickable { onRefreshIp() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = glass.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刷新 IP", color = glass.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (recentTransfers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "本次接收 (${recentTransfers.size})",
                            color = glass.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        recentTransfers.take(5).forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(subPillBg)
                                    .clickable {
                                        if (log.kind == TransferKind.BOOK) {
                                            onOpenUploadedBook(log.targetId)
                                        } else {
                                            onSelectFont(log.targetId)
                                            Toast.makeText(context, "已切换字体", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.kind == TransferKind.BOOK) Icons.Rounded.AutoStories else Icons.Rounded.FontDownload,
                                    contentDescription = null,
                                    tint = glass.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = log.fileName,
                                    color = glass.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                                    color = glass.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Font Management Header + Import Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字体管理 (${fonts.size})",
                    color = glass.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(glass.accent)
                        .clickable { onImportLocalFont() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("本地导入字体", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Live Font Preview Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "字体效果预览",
                    color = glass.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "　　落霞与孤鹜齐飞，秋水共长天一色。支持电脑 Wi-Fi 拖入或本机选择 .ttf / .otf 字体。",
                    color = glass.textPrimary,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    fontFamily = FontFamily(activeTypeface)
                )
            }
        }

        items(fonts, key = { it.id }) { font ->
            val selected = settings.selectedFontId == font.id
            val tf = androidx.compose.runtime.remember(font.id) { resolveTypeface(font.id) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBackground)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) glass.accent else cardBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectFont(font.id) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) glass.accent else glass.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FontDownload,
                        contentDescription = null,
                        tint = if (selected) Color.White else glass.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.displayName,
                        color = glass.textPrimary,
                        fontSize = 15.sp,
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(subPillBg)
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
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // Section 3: App Appearance
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "应用外观",
                    color = glass.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
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

        item {
            Text(
                text = "悦读 YueDu v0.2.0",
                color = glass.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun rememberTypeface(
    fontId: String,
    fonts: List<CustomFont>,
    resolveTypeface: (String) -> Typeface
): Typeface {
    return androidx.compose.runtime.remember(fontId, fonts) { resolveTypeface(fontId) }
}
