package com.yuedu.reader.model

import androidx.compose.ui.graphics.Color

data class BookItem(
    val id: String,
    val title: String,
    val originalFileName: String,
    val filePath: String,
    val fileSize: Long,
    val detectedEncoding: String,
    val totalBytes: Long,
    val chapterCount: Int,
    val currentChapterIndex: Int = 0,
    val currentCharOffset: Int = 0,
    val progress: Float = 0f,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    val coverPaletteIndex: Int = 0
)

data class ChapterInfo(
    val index: Int,
    val title: String,
    val startByte: Long,
    val endByte: Long
)

data class CustomFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false
)

enum class ReaderTheme(
    val id: String,
    val label: String,
    val backgroundColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val isDark: Boolean
) {
    PAPER(
        id = "paper",
        label = "纯净白",
        backgroundColor = Color(0xFFFBFBFD),
        textColor = Color(0xFF1D1D1F),
        secondaryTextColor = Color(0xFF6E6E73),
        accentColor = Color(0xFF007AFF),
        isDark = false
    ),
    SEPIA(
        id = "sepia",
        label = "暖羊皮",
        backgroundColor = Color(0xFFF6EFE2),
        textColor = Color(0xFF3A2F23),
        secondaryTextColor = Color(0xFF7E6E5B),
        accentColor = Color(0xFFC96B2C),
        isDark = false
    ),
    SAGE(
        id = "sage",
        label = "护眼青",
        backgroundColor = Color(0xFFE7F0E8),
        textColor = Color(0xFF1E3224),
        secondaryTextColor = Color(0xFF56705D),
        accentColor = Color(0xFF248A5A),
        isDark = false
    ),
    MIDNIGHT(
        id = "midnight",
        label = "深空黑",
        backgroundColor = Color(0xFF0E1015),
        textColor = Color(0xFFDDE2EB),
        secondaryTextColor = Color(0xFF8A92A0),
        accentColor = Color(0xFF4DA3FF),
        isDark = true
    );

    companion object {
        fun fromId(id: String): ReaderTheme = entries.find { it.id == id } ?: PAPER
    }
}

enum class ReadingMode(val id: String, val label: String) {
    PAGED_HORIZONTAL("paged", "左右翻页"),
    SCROLL_VERTICAL("scroll", "上下滚动");

    companion object {
        fun fromId(id: String): ReadingMode = entries.find { it.id == id } ?: PAGED_HORIZONTAL
    }
}

enum class AppAppearance(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色玻璃"),
    DARK("dark", "深邃暗夜");

    companion object {
        fun fromId(id: String): AppAppearance = entries.find { it.id == id } ?: SYSTEM
    }
}

data class ReaderSettings(
    val fontSizeSp: Float = 19f,
    val lineHeightMultiplier: Float = 1.65f,
    val horizontalPaddingDp: Float = 22f,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val readingMode: ReadingMode = ReadingMode.PAGED_HORIZONTAL,
    val selectedFontId: String = BUILTIN_SYSTEM_SANS,
    val appAppearance: AppAppearance = AppAppearance.SYSTEM
) {
    companion object {
        const val BUILTIN_SYSTEM_SANS = "builtin_sans"
        const val BUILTIN_SYSTEM_SERIF = "builtin_serif"
    }
}

data class ReaderPage(
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

data class WifiTransferLog(
    val id: String,
    val fileName: String,
    val kind: TransferKind,
    val sizeBytes: Long,
    val timestamp: Long,
    val targetId: String
)

enum class TransferKind {
    BOOK,
    FONT
}
