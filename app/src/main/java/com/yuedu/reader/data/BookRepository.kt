package com.yuedu.reader.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.yuedu.reader.engine.TxtBookEngine
import com.yuedu.reader.model.AppAppearance
import com.yuedu.reader.model.BookItem
import com.yuedu.reader.model.ChapterInfo
import com.yuedu.reader.model.CustomFont
import com.yuedu.reader.model.ReaderSettings
import com.yuedu.reader.model.ReaderTheme
import com.yuedu.reader.model.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class BookRepository(private val context: Context) {

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val chaptersDir = File(context.filesDir, "chapters").apply { mkdirs() }
    private val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
    private val booksMetaFile = File(context.filesDir, "books.json")
    private val fontsMetaFile = File(context.filesDir, "fonts.json")
    private val settingsMetaFile = File(context.filesDir, "settings.json")
    private val seedFlagFile = File(context.filesDir, ".seeded_v1")

    private val ioMutex = Mutex()
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private val _books = MutableStateFlow<List<BookItem>>(emptyList())
    val books: StateFlow<List<BookItem>> = _books.asStateFlow()

    private val _fonts = MutableStateFlow<List<CustomFont>>(builtInFonts())
    val fonts: StateFlow<List<CustomFont>> = _fonts.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            _settings.value = readSettingsLocked()
            _fonts.value = builtInFonts() + readCustomFontsLocked()
            val loadedBooks = readBooksLocked()
            _books.value = loadedBooks

            if (loadedBooks.isEmpty() && !seedFlagFile.exists()) {
                seedFlagFile.createNewFile()
                val welcomeText = buildWelcomeGuideText()
                val imported = importBookFromStreamLocked(
                    originalFileName = "悦读使用指南.txt",
                    fileSize = welcomeText.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                    streamFactory = { ByteArrayInputStream(welcomeText.toByteArray(StandardCharsets.UTF_8)) }
                )
                _books.value = listOf(imported)
                writeBooksLocked(_books.value)
            }
        }
    }

    suspend fun importBookFromUri(uri: Uri): BookItem = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "未命名书籍.txt"
        val size = queryFileSize(uri)
        ioMutex.withLock {
            val item = importBookFromStreamLocked(
                originalFileName = fileName,
                fileSize = size,
                streamFactory = {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("无法读取所选文件")
                }
            )
            val updated = listOf(item) + _books.value
            _books.value = updated
            writeBooksLocked(updated)
            item
        }
    }

    suspend fun importBookFromTempFile(tempFile: File, originalFileName: String): BookItem =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                val item = importBookFromStreamLocked(
                    originalFileName = originalFileName,
                    fileSize = tempFile.length(),
                    streamFactory = { FileInputStream(tempFile) }
                )
                val updated = listOf(item) + _books.value
                _books.value = updated
                writeBooksLocked(updated)
                item
            }
        }

    private fun importBookFromStreamLocked(
        originalFileName: String,
        fileSize: Long,
        streamFactory: () -> java.io.InputStream
    ): BookItem {
        val id = UUID.randomUUID().toString()
        val destBookFile = File(booksDir, "$id.txt")
        val result = TxtBookEngine.transcodeAndIndex(streamFactory, destBookFile)
        val chaptersFile = File(chaptersDir, "$id.json")
        writeChaptersLocked(chaptersFile, result.chapters)

        val cleanTitle = originalFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""\.(txt|TXT)$"""), "")
            .ifBlank { "未命名书籍" }

        return BookItem(
            id = id,
            title = cleanTitle,
            originalFileName = originalFileName,
            filePath = destBookFile.absolutePath,
            fileSize = if (fileSize > 0L) fileSize else result.totalBytes,
            detectedEncoding = result.detectedEncoding,
            totalBytes = result.totalBytes,
            chapterCount = result.chapters.size,
            coverPaletteIndex = (_books.value.size + cleanTitle.hashCode()).let { kotlin.math.abs(it) % 6 }
        )
    }

    suspend fun importFontFromUri(uri: Uri): CustomFont = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "custom_font.ttf"
        val temp = File(context.cacheDir, "font_import_${System.currentTimeMillis()}_$fileName")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法读取字体文件")
            importFontFromTempFile(temp, fileName)
        } finally {
            temp.delete()
        }
    }

    suspend fun importFontFromTempFile(tempFile: File, originalFileName: String): CustomFont =
        withContext(Dispatchers.IO) {
            val cleanName = originalFileName.substringAfterLast('/').substringAfterLast('\\')
            val ext = cleanName.substringAfterLast('.', "ttf").lowercase()
            require(ext in listOf("ttf", "otf", "ttc")) { "仅支持 .ttf / .otf / .ttc 字体文件" }

            val id = UUID.randomUUID().toString()
            val destFile = File(fontsDir, "$id.$ext")
            tempFile.copyTo(destFile, overwrite = true)

            // Validate font with Android Typeface
            val tf = try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                destFile.delete()
                throw IllegalArgumentException("字体文件无效或已损坏", e)
            }

            typefaceCache[id] = tf
            val displayName = cleanName.substringBeforeLast('.').ifBlank { "自定义字体" }
            val font = CustomFont(
                id = id,
                displayName = displayName,
                fileName = cleanName,
                filePath = destFile.absolutePath,
                isBuiltIn = false
            )

            ioMutex.withLock {
                val custom = readCustomFontsLocked() + font
                writeCustomFontsLocked(custom)
                _fonts.value = builtInFonts() + custom
            }
            font
        }

    fun getTypeface(fontId: String): Typeface {
        return when (fontId) {
            ReaderSettings.BUILTIN_SYSTEM_SANS -> Typeface.SANS_SERIF
            ReaderSettings.BUILTIN_SYSTEM_SERIF -> Typeface.SERIF
            else -> {
                typefaceCache[fontId]?.let { return it }
                val font = _fonts.value.find { it.id == fontId }
                if (font != null && font.filePath.isNotBlank()) {
                    val file = File(font.filePath)
                    if (file.exists()) {
                        return runCatching {
                            Typeface.createFromFile(file).also { typefaceCache[fontId] = it }
                        }.getOrDefault(Typeface.SANS_SERIF)
                    }
                }
                Typeface.SANS_SERIF
            }
        }
    }

    suspend fun loadChapters(bookId: String): List<ChapterInfo> = withContext(Dispatchers.IO) {
        val file = File(chaptersDir, "$bookId.json")
        if (!file.exists()) return@withContext emptyList()
        val arr = JSONArray(file.readText(StandardCharsets.UTF_8))
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ChapterInfo(
                        index = obj.getInt("index"),
                        title = obj.getString("title"),
                        startByte = obj.getLong("startByte"),
                        endByte = obj.getLong("endByte")
                    )
                )
            }
        }
    }

    suspend fun loadChapterText(book: BookItem, chapter: ChapterInfo): String =
        withContext(Dispatchers.IO) {
            TxtBookEngine.readChapterText(File(book.filePath), chapter)
        }

    suspend fun updateBookProgress(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        progress: Float
    ) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val now = System.currentTimeMillis()
            val updated = _books.value.map { item ->
                if (item.id == bookId) {
                    item.copy(
                        currentChapterIndex = chapterIndex,
                        currentCharOffset = charOffset,
                        progress = progress.coerceIn(0f, 1f),
                        lastReadAt = now
                    )
                } else {
                    item
                }
            }
            _books.value = updated
            writeBooksLocked(updated)
        }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            File(booksDir, "$bookId.txt").delete()
            File(chaptersDir, "$bookId.json").delete()
            val updated = _books.value.filterNot { it.id == bookId }
            _books.value = updated
            writeBooksLocked(updated)
        }
    }

    suspend fun deleteFont(fontId: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val custom = readCustomFontsLocked()
            val target = custom.find { it.id == fontId } ?: return@withLock
            File(target.filePath).delete()
            typefaceCache.remove(fontId)
            val remaining = custom.filterNot { it.id == fontId }
            writeCustomFontsLocked(remaining)
            _fonts.value = builtInFonts() + remaining

            if (_settings.value.selectedFontId == fontId) {
                val next = _settings.value.copy(selectedFontId = ReaderSettings.BUILTIN_SYSTEM_SANS)
                _settings.value = next
                writeSettingsLocked(next)
            }
        }
    }

    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                val updated = transform(_settings.value)
                _settings.value = updated
                writeSettingsLocked(updated)
            }
        }

    private fun builtInFonts(): List<CustomFont> = listOf(
        CustomFont(
            id = ReaderSettings.BUILTIN_SYSTEM_SANS,
            displayName = "系统无衬线 (默认)",
            fileName = "System Sans",
            filePath = "",
            isBuiltIn = true
        ),
        CustomFont(
            id = ReaderSettings.BUILTIN_SYSTEM_SERIF,
            displayName = "系统衬线体 (书卷)",
            fileName = "System Serif",
            filePath = "",
            isBuiltIn = true
        )
    )

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun queryFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        return 0L
    }

    private fun readBooksLocked(): List<BookItem> {
        if (!booksMetaFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(booksMetaFile.readText(StandardCharsets.UTF_8))
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        BookItem(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            originalFileName = o.optString("originalFileName", ""),
                            filePath = o.getString("filePath"),
                            fileSize = o.optLong("fileSize", 0L),
                            detectedEncoding = o.optString("detectedEncoding", "UTF-8"),
                            totalBytes = o.optLong("totalBytes", 0L),
                            chapterCount = o.optInt("chapterCount", 1),
                            currentChapterIndex = o.optInt("currentChapterIndex", 0),
                            currentCharOffset = o.optInt("currentCharOffset", 0),
                            progress = o.optDouble("progress", 0.0).toFloat(),
                            addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                            lastReadAt = o.optLong("lastReadAt", 0L),
                            coverPaletteIndex = o.optInt("coverPaletteIndex", 0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeBooksLocked(books: List<BookItem>) {
        val arr = JSONArray()
        for (b in books) {
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("originalFileName", b.originalFileName)
                    put("filePath", b.filePath)
                    put("fileSize", b.fileSize)
                    put("detectedEncoding", b.detectedEncoding)
                    put("totalBytes", b.totalBytes)
                    put("chapterCount", b.chapterCount)
                    put("currentChapterIndex", b.currentChapterIndex)
                    put("currentCharOffset", b.currentCharOffset)
                    put("progress", b.progress.toDouble())
                    put("addedAt", b.addedAt)
                    put("lastReadAt", b.lastReadAt)
                    put("coverPaletteIndex", b.coverPaletteIndex)
                }
            )
        }
        booksMetaFile.writeText(arr.toString(), StandardCharsets.UTF_8)
    }

    private fun writeChaptersLocked(file: File, chapters: List<ChapterInfo>) {
        val arr = JSONArray()
        for (c in chapters) {
            arr.put(
                JSONObject().apply {
                    put("index", c.index)
                    put("title", c.title)
                    put("startByte", c.startByte)
                    put("endByte", c.endByte)
                }
            )
        }
        file.writeText(arr.toString(), StandardCharsets.UTF_8)
    }

    private fun readCustomFontsLocked(): List<CustomFont> {
        if (!fontsMetaFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(fontsMetaFile.readText(StandardCharsets.UTF_8))
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val path = o.getString("filePath")
                    if (File(path).exists()) {
                        add(
                            CustomFont(
                                id = o.getString("id"),
                                displayName = o.getString("displayName"),
                                fileName = o.getString("fileName"),
                                filePath = path,
                                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                                isBuiltIn = false
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomFontsLocked(fonts: List<CustomFont>) {
        val arr = JSONArray()
        for (f in fonts) {
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("displayName", f.displayName)
                    put("fileName", f.fileName)
                    put("filePath", f.filePath)
                    put("addedAt", f.addedAt)
                }
            )
        }
        fontsMetaFile.writeText(arr.toString(), StandardCharsets.UTF_8)
    }

    private fun readSettingsLocked(): ReaderSettings {
        if (!settingsMetaFile.exists()) return ReaderSettings()
        return runCatching {
            val o = JSONObject(settingsMetaFile.readText(StandardCharsets.UTF_8))
            ReaderSettings(
                fontSizeSp = o.optDouble("fontSizeSp", 19.0).toFloat(),
                lineHeightMultiplier = o.optDouble("lineHeightMultiplier", 1.65).toFloat(),
                horizontalPaddingDp = o.optDouble("horizontalPaddingDp", 22.0).toFloat(),
                theme = ReaderTheme.fromId(o.optString("theme", "paper")),
                readingMode = ReadingMode.fromId(o.optString("readingMode", "paged")),
                selectedFontId = o.optString("selectedFontId", ReaderSettings.BUILTIN_SYSTEM_SANS),
                appAppearance = AppAppearance.fromId(o.optString("appAppearance", "system"))
            )
        }.getOrDefault(ReaderSettings())
    }

    private fun writeSettingsLocked(s: ReaderSettings) {
        val obj = JSONObject().apply {
            put("fontSizeSp", s.fontSizeSp.toDouble())
            put("lineHeightMultiplier", s.lineHeightMultiplier.toDouble())
            put("horizontalPaddingDp", s.horizontalPaddingDp.toDouble())
            put("theme", s.theme.id)
            put("readingMode", s.readingMode.id)
            put("selectedFontId", s.selectedFontId)
            put("appAppearance", s.appAppearance.id)
        }
        settingsMetaFile.writeText(obj.toString(), StandardCharsets.UTF_8)
    }

    private fun buildWelcomeGuideText(): String = """
        第一章 欢迎来到悦读

        欢迎体验「悦读」第一版！这是一款专为纯文本阅读打造的极简阅读器，采用通透灵动的液态玻璃（Liquid Glass）视觉语言设计。

        在这一版中，你可以完整体验以下核心功能：
        1. 智能 TXT 排版：自动识别 UTF-8、GBK/GB18030、UTF-16 编码，告别乱码；自动识别中英文章节目录，大文件也能秒开。
        2. 同 Wi-Fi 电脑传书：切换到底部「传书」标签，在电脑浏览器打开屏幕上的局域网地址，即可拖拽批量上传 TXT 小说与自定义字体。
        3. 自定义字体导入：支持从手机本地或电脑 Wi-Fi 导入任意 .ttf / .otf / .ttc 字体文件，随时在阅读器内一键切换。

        第二章 阅读手势与个性化

        在阅读界面中：
        • 点击屏幕左侧 1/3 区域向上翻页，点击右侧 1/3 区域向下翻页。
        • 点击屏幕中央区域，可以唤出或收起悬浮液态玻璃控制栏。
        • 支持「左右翻页」与「上下连续滚动」两种模式自由切换。
        • 内置纯净白、暖羊皮、护眼青、深空黑四款阅读纸张主题，并支持字号、行距、页边距无级调节。

        祝你阅读愉快！现在就试着通过底部「传书」把你喜欢的小说和字体传到手机上吧。
    """.trimIndent()
}
