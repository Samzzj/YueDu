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

data class BookmarkItem(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val excerpt: String,
    val progress: Float,
    val createdAt: Long = System.currentTimeMillis()
)

data class SearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val snippet: String
)

data class CustomFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false,
    val catalogId: String? = null
)

data class DownloadableFont(
    val id: String,
    val displayName: String,
    val styleTag: String,
    val sizeLabel: String,
    val fileName: String,
    val downloadUrls: List<String>
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
        secondaryTextColor = Color(0xFF7A7A80),
        accentColor = Color(0xFF007AFF),
        isDark = false
    ),
    SEPIA(
        id = "sepia",
        label = "暖羊皮",
        backgroundColor = Color(0xFFF5ECD9),
        textColor = Color(0xFF3A2F23),
        secondaryTextColor = Color(0xFF847461),
        accentColor = Color(0xFFC96B2C),
        isDark = false
    ),
    SAGE(
        id = "sage",
        label = "护眼青",
        backgroundColor = Color(0xFFE5EFE6),
        textColor = Color(0xFF1E3224),
        secondaryTextColor = Color(0xFF5D7664),
        accentColor = Color(0xFF248A5A),
        isDark = false
    ),
    MIDNIGHT(
        id = "midnight",
        label = "深空黑",
        backgroundColor = Color(0xFF0E1015),
        textColor = Color(0xFFD6DCE6),
        secondaryTextColor = Color(0xFF7D8594),
        accentColor = Color(0xFF4DA3FF),
        isDark = true
    );

    companion object {
        fun fromId(id: String): ReaderTheme = entries.find { it.id == id } ?: PAPER
    }
}

enum class ReadingMode(val id: String, val label: String) {
    SIMULATION("simulation", "仿真翻页"),
    SMOOTH("smooth", "平滑翻页"),
    VERTICAL("vertical", "上下翻页");

    companion object {
        fun fromId(id: String): ReadingMode {
            return when (id) {
                "simulation" -> SIMULATION
                "smooth", "paged" -> SMOOTH
                "vertical", "scroll" -> VERTICAL
                else -> SIMULATION
            }
        }
    }
}

enum class AppAppearance(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromId(id: String): AppAppearance = entries.find { it.id == id } ?: SYSTEM
    }
}

data class ReaderSettings(
    val fontSizeSp: Float = 28f,
    val lineHeightMultiplier: Float = 1.3f,
    val paragraphSpacingDp: Float = 10f,
    val horizontalPaddingDp: Float = 20f,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val readingMode: ReadingMode = ReadingMode.SIMULATION,
    val selectedFontId: String = BUILTIN_SYSTEM_SANS,
    val appAppearance: AppAppearance = AppAppearance.SYSTEM
) {
    companion object {
        const val BUILTIN_SYSTEM_SANS = "builtin_sans"
    }
}

data class TtsSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceId: String = "edge:zh-CN-XiaoxiaoNeural",
    val sleepTimerMinutes: Int = 0, // 0 = off, -1 = end of chapter, >0 = minutes
    val autoFollow: Boolean = true,
    val highlightSentence: Boolean = true
)

data class TtsVoiceOption(
    val id: String,
    val engineVoiceName: String,
    val displayLabel: String,
    val styleTag: String,
    val isNeural: Boolean
)

data class TtsSentence(
    val index: Int,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

data class PageSlice(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val isTitle: Boolean,
    val hasBottomGap: Boolean
)

data class ReaderPage(
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val slices: List<PageSlice>
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
