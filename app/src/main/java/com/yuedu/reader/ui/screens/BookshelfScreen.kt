package com.yuedu.reader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.ui.components.LiquidGlassButton
import com.yuedu.reader.ui.components.LiquidGlassSurface
import com.yuedu.reader.ui.theme.BookCoverPalettes
import com.yuedu.reader.ui.theme.LocalLiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookshelfScreen(
    books: List<BookItem>,
    onOpenBook: (BookItem) -> Unit,
    onImportLocalBook: () -> Unit,
    onGoWifiTransfer: () -> Unit,
    onRenameBook: (BookItem, String) -> Unit,
    onDeleteBook: (BookItem) -> Unit
) {
    val glass = LocalLiquidGlass.current
    var query by remember { mutableStateOf("") }
    var isGridMode by remember { mutableStateOf(true) }
    var actionBook by remember { mutableStateOf<BookItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = remember(books, query) {
        val q = query.trim()
        val list = if (q.isEmpty()) books else books.filter { it.title.contains(q, ignoreCase = true) }
        list.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "悦读",
                    color = glass.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = "${books.size} 本书籍",
                    color = glass.textSecondary,
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidGlassButton(
                    onClick = { isGridMode = !isGridMode },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isGridMode) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                        contentDescription = "切换视图",
                        tint = glass.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                LiquidGlassButton(
                    onClick = onGoWifiTransfer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = "Wi-Fi 传书",
                        tint = glass.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                LiquidGlassButton(
                    onClick = onImportLocalBook,
                    tint = glass.accent,
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "导入",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "导入",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            cornerRadius = 23.dp,
            elevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = glass.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索书架中的书籍...",
                            color = glass.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = glass.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(glass.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = glass.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { query = "" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = glass.accent,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (books.isEmpty()) "书架还是空的" else "未找到匹配书籍",
                            color = glass.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点击右上角「导入」选择本机 TXT，或使用 Wi-Fi 从电脑批量传书",
                            color = glass.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isGridMode) 2 else 1),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { book ->
                    if (isGridMode) {
                        GridBookCard(
                            book = book,
                            onClick = { onOpenBook(book) },
                            onLongClick = {
                                renameText = book.title
                                actionBook = book
                            }
                        )
                    } else {
                        ListBookCard(
                            book = book,
                            onClick = { onOpenBook(book) },
                            onDeleteClick = {
                                renameText = book.title
                                actionBook = book
                            }
                        )
                    }
                }
            }
        }
    }

    actionBook?.let { book ->
        AlertDialog(
            onDismissRequest = { actionBook = null },
            title = { Text("书籍管理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("可修改书名或将其移出书架：", fontSize = 13.sp)
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("书名") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameBook(book, renameText)
                        actionBook = null
                    }
                ) {
                    Text("保存书名", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDeleteBook(book)
                            actionBook = null
                        }
                    ) {
                        Text("删除书籍", color = glass.danger, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { actionBook = null }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookCard(
    book: BookItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val (c1, c2) = BookCoverPalettes[book.coverPaletteIndex % BookCoverPalettes.size]

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 24.dp,
        elevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(c1, c2)))
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "TXT",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = book.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.title,
                color = glass.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${book.chapterCount} 章 · ${formatBytes(book.fileSize)}",
                    color = glass.textSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = if (book.lastReadAt == 0L) "未读" else "${(book.progress * 100).toInt()}%",
                    color = glass.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { book.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = glass.accent,
                trackColor = glass.textSecondary.copy(alpha = 0.16f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBookCard(
    book: BookItem,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val (c1, c2) = BookCoverPalettes[book.coverPaletteIndex % BookCoverPalettes.size]

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDeleteClick),
        cornerRadius = 22.dp,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(c1, c2)))
                    .padding(6.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = "TXT",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = glass.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${book.chapterCount} 章 · ${formatBytes(book.fileSize)} · ${formatReadTime(book.lastReadAt)}",
                    color = glass.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { book.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = glass.accent,
                        trackColor = glass.textSecondary.copy(alpha = 0.16f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (book.lastReadAt == 0L) "未读" else "${(book.progress * 100).toInt()}%",
                        color = glass.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            LiquidGlassButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "管理",
                    tint = glass.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

private fun formatReadTime(timestamp: Long): String {
    if (timestamp <= 0L) return "尚未阅读"
    return "上次 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
