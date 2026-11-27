package com.yuedu.reader.ui.screens

import android.graphics.Typeface
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.model.AppAppearance
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.ui.components.LiquidGlassButton
import com.yuedu.reader.ui.components.LiquidGlassSurface
import com.yuedu.reader.ui.theme.LocalLiquidGlass

@Composable
fun FontsSettingsScreen(
    fonts: List<CustomFont>,
    settings: ReaderSettings,
    resolveTypeface: (String) -> Typeface,
    onImportLocalFont: () -> Unit,
    onSelectFont: (String) -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onChangeAppearance: (AppAppearance) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val activeTypeface = remember(settings.selectedFontId, fonts) {
        resolveTypeface(settings.selectedFontId)
    }

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
                        text = "字体与外观",
                        color = glass.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    )
                    Text(
                        text = "自定义阅读字体与液态玻璃界面主题",
                        color = glass.textSecondary,
                        fontSize = 13.sp
                    )
                }

                LiquidGlassButton(
                    onClick = onImportLocalFont,
                    tint = glass.accent,
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "导入字体",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "导入字体",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Live Typography Preview Glass Card
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                strong = true,
                elevation = 14.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "实时排版预览",
                        color = glass.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "　　落霞与孤鹜齐飞，秋水共长天一色。阅读是一场跨越时空的静谧对话，好字体让文字呼吸自如。\n　　The quick brown fox jumps over the lazy dog. 0123456789",
                        color = glass.textPrimary,
                        fontSize = settings.fontSizeSp.sp,
                        lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                        fontFamily = FontFamily(activeTypeface)
                    )
                }
            }
        }

        // App Appearance Switcher
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                    .clip(CircleShape)
                                    .background(if (selected) glass.accent else glass.accentSoft)
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

        // Fonts List Header
        item {
            Text(
                text = "可用字体 (${fonts.size}) · 支持 .ttf / .otf / .ttc",
                color = glass.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(fonts, key = { it.id }) { font ->
            val selected = settings.selectedFontId == font.id
            val tf = remember(font.id) { resolveTypeface(font.id) }

            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelectFont(font.id) },
                cornerRadius = 22.dp,
                strong = selected,
                elevation = if (selected) 12.dp else 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            text = if (font.isBuiltIn) "内置系统字体" else font.fileName,
                            color = glass.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!font.isBuiltIn) {
                        LiquidGlassButton(
                            onClick = { onDeleteFont(font) },
                            modifier = Modifier.size(34.dp)
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
        }

        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                elevation = 4.dp
            ) {
                Text(
                    text = "悦读 YueDu v0.1.0 · 第一版专注纯净 TXT 离线排版、同 Wi-Fi 电脑传书与自定义字体。",
                    color = glass.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
