package com.yuedu.reader.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ContentCopy
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
import com.yuedu.reader.model.TransferKind
import com.yuedu.reader.model.WifiTransferLog
import com.yuedu.reader.ui.components.LiquidGlassButton
import com.yuedu.reader.ui.components.LiquidGlassSurface
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WifiTransferScreen(
    isRunning: Boolean,
    serverUrl: String?,
    recentTransfers: List<WifiTransferLog>,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRefreshIp: () -> Unit,
    onOpenUploadedBook: (String) -> Unit,
    onApplyUploadedFont: (String) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Auto-start server when entering Wi-Fi transfer tab
    LaunchedEffect(Unit) {
        if (!isRunning) {
            onStartServer()
        } else {
            onRefreshIp()
        }
    }

    // Keep screen awake while user is transferring books from PC
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wi-Fi 传书",
                        color = glass.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    )
                    Text(
                        text = "同局域网下电脑直接上传书籍与字体",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                }

                LiquidGlassButton(
                    onClick = { if (isRunning) onStopServer() else onStartServer() },
                    tint = if (isRunning) glass.glassSurfaceStrong else glass.accent,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (isRunning) "停止服务" else "启动服务",
                        tint = if (isRunning) glass.success else Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // Hero Liquid Glass Address Card
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                strong = true,
                elevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isRunning) glass.success.copy(alpha = 0.14f)
                                else glass.textSecondary.copy(alpha = 0.14f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(if (isRunning) pulseAlpha else 1f)
                                .clip(CircleShape)
                                .background(if (isRunning) glass.success else glass.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "服务运行中 · 保持此页面开启" else "服务已暂停",
                            color = if (isRunning) glass.success else glass.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "在电脑浏览器输入以下地址",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(glass.accentSoft)
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = serverUrl ?: "服务未启动",
                            color = glass.accent,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LiquidGlassButton(
                            onClick = {
                                if (serverUrl != null) {
                                    clipboard.setText(AnnotatedString(serverUrl))
                                    Toast.makeText(context, "已复制传书地址", Toast.LENGTH_SHORT).show()
                                }
                            },
                            tint = glass.accent,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "复制地址",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        LiquidGlassButton(
                            onClick = onRefreshIp,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = glass.textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "刷新 IP",
                                    color = glass.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Steps Card
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "使用说明",
                        color = glass.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. 确保电脑与手机连接在同一个 Wi-Fi（或手机开热点给电脑连）。",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "2. 电脑打开 Chrome / Edge / Safari 浏览器，访问上方 http:// 地址。",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "3. 拖入 .txt 书籍或 .ttf / .otf / .ttc 字体文件即可批量入库。",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Live Transfer Feed
        item {
            Text(
                text = "本次接收记录 (${recentTransfers.size})",
                color = glass.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (recentTransfers.isEmpty()) {
            item {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    elevation = 4.dp
                ) {
                    Text(
                        text = "等待电脑端上传文件...",
                        color = glass.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(recentTransfers, key = { it.id }) { log ->
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            when (log.kind) {
                                TransferKind.BOOK -> onOpenUploadedBook(log.targetId)
                                TransferKind.FONT -> onApplyUploadedFont(log.targetId)
                            }
                        },
                    cornerRadius = 20.dp,
                    elevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(glass.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (log.kind == TransferKind.BOOK) Icons.Rounded.AutoStories else Icons.Rounded.FontDownload,
                                contentDescription = null,
                                tint = glass.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.fileName,
                                color = glass.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = (if (log.kind == TransferKind.BOOK) "点击立即阅读" else "点击立即应用字体") +
                                    " · ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))}",
                                color = glass.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
