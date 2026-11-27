package com.yuedu.reader.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.data.CommercialFontsCatalog
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.DownloadableFont
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerSheet(
    fonts: List<CustomFont>,
    settings: ReaderSettings,
    resolveTypeface: (String) -> Typeface,
    onSelectFont: (String) -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onImportLocalFont: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    onDownloadCatalogFont: suspend (DownloadableFont, (Float) -> Unit) -> Result<CustomFont>,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = if (glass.isDark) Color(0xFF1A1C22) else Color(0xFFFFFFFF)
    val cardBg = if (glass.isDark) Color(0xFF252830) else Color(0xFFF2F2F7)
    val downloadingProgress = remember { mutableStateMapOf<String, Float>() }
    val activeTypeface = remember(settings.selectedFontId, fonts) { resolveTypeface(settings.selectedFontId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "字体设置与下载",
                    color = glass.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "支持本机导入、Wi-Fi 传字体及 10 款免费可商用中文字体一键下载",
                    color = glass.textSecondary,
                    fontSize = 12.sp
                )
            }

            // Local & Wi-Fi Import Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(glass.accent)
                            .clickable { onImportLocalFont() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本机导入字体", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable {
                                onDismiss()
                                onOpenWifiTransfer()
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = glass.accent, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wi-Fi 传字体", color = glass.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Live Preview Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(14.dp)
                ) {
                    Text("当前字体预览", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "　　落霞与孤鹜齐飞，秋水共长天一色。字体清晰舒展，久读不累。",
                        color = glass.textPrimary,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        fontFamily = FontFamily(activeTypeface)
                    )
                }
            }

            // Installed Fonts
            item {
                Text(
                    text = "已安装字体 (${fonts.size})",
                    color = glass.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(fonts, key = { "inst_${it.id}" }) { font ->
                val selected = settings.selectedFontId == font.id
                val tf = remember(font.id) { resolveTypeface(font.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(
                            width = if (selected) 1.5.dp else 0.dp,
                            color = if (selected) glass.accent else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectFont(font.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FontDownload,
                        contentDescription = null,
                        tint = if (selected) glass.accent else glass.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
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
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!font.isBuiltIn) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { onDeleteFont(font) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = glass.textSecondary, modifier = Modifier.size(17.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (selected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "当前使用", tint = glass.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 10 Free Commercial-Use Fonts Catalog
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "免费可商用字体库 (10款 · SIL OFL 1.1 开源授权)",
                    color = glass.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(CommercialFontsCatalog.freeCommercialFonts, key = { it.id }) { item ->
                val installedFont = fonts.find { it.catalogId == item.id }
                val isSelected = installedFont != null && settings.selectedFontId == installedFont.id
                val progress = downloadingProgress[item.id]

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.displayName,
                                    color = glass.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.success.copy(alpha = 0.14f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "免费商用",
                                        color = glass.success,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${item.styleTag} · ${item.sizeLabel}",
                                color = glass.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        when {
                            progress != null -> {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = glass.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            installedFont != null -> {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) glass.accent.copy(alpha = 0.14f) else glass.accent)
                                        .clickable { onSelectFont(installedFont.id) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (isSelected) "使用中" else "使用",
                                        color = if (isSelected) glass.accent else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.accent)
                                        .clickable {
                                            downloadingProgress[item.id] = 0f
                                            scope.launch {
                                                val res = onDownloadCatalogFont(item) { p ->
                                                    downloadingProgress[item.id] = p
                                                }
                                                downloadingProgress.remove(item.id)
                                                res.onSuccess { font ->
                                                    onSelectFont(font.id)
                                                    Toast.makeText(context, "已下载并应用：${font.displayName}", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, err.message ?: "下载失败，请检查网络", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("下载", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                .height(4.dp)
                                .clip(CircleShape),
                            color = glass.accent,
                            trackColor = glass.textSecondary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}
