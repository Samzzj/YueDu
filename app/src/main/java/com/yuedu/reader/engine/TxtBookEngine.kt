package com.yuedu.reader.engine

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yuedu.reader.model.ChapterInfo
import com.yuedu.reader.model.ReaderPage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object TxtBookEngine {

    private val chapterPatterns = listOf(
        Regex("""^\s*第[零一二三四五六七八九十百千万两0-9]{1,9}[章节回卷集部篇].{0,40}$"""),
        Regex("""^\s*[【\[]?(序章|楔子|前言|引子|后记|尾声|番外|简介)[】\]]?.{0,30}$"""),
        Regex("""^\s*(Chapter|CHAPTER|Section|PART)\s+[0-9IVXLCDM]+.{0,50}$""")
    )

    data class ImportResult(
        val detectedEncoding: String,
        val totalBytes: Long,
        val chapters: List<ChapterInfo>
    )

    fun detectCharset(sampleBytes: ByteArray, length: Int): Charset {
        if (length >= 3 &&
            sampleBytes[0] == 0xEF.toByte() &&
            sampleBytes[1] == 0xBB.toByte() &&
            sampleBytes[2] == 0xBF.toByte()
        ) {
            return StandardCharsets.UTF_8
        }
        if (length >= 2) {
            if (sampleBytes[0] == 0xFE.toByte() && sampleBytes[1] == 0xFF.toByte()) {
                return StandardCharsets.UTF_16BE
            }
            if (sampleBytes[0] == 0xFF.toByte() && sampleBytes[1] == 0xFE.toByte()) {
                return StandardCharsets.UTF_16LE
            }
        }

        // Trim potential incomplete trailing UTF-8 multibyte sequence in sample
        var checkLen = length
        while (checkLen > 0 && checkLen > length - 4 && (sampleBytes[checkLen - 1].toInt() and 0xC0) == 0x80) {
            checkLen--
        }
        if (checkLen > 0 && (sampleBytes[checkLen - 1].toInt() and 0x80) != 0) {
            checkLen--
        }
        if (checkLen <= 0) checkLen = length

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(sampleBytes, 0, checkLen))
            StandardCharsets.UTF_8
        } catch (_: Exception) {
            Charset.forName("GB18030")
        }
    }

    /**
     * Normalizes an arbitrary TXT input stream into a clean UTF-8 file and builds its chapter index.
     */
    fun transcodeAndIndex(inputStreamFactory: () -> InputStream, targetUtf8File: File): ImportResult {
        val sample = ByteArray(65536)
        var sampleRead = 0
        inputStreamFactory().use { stream ->
            val bis = BufferedInputStream(stream)
            while (sampleRead < sample.size) {
                val r = bis.read(sample, sampleRead, sample.size - sampleRead)
                if (r <= 0) break
                sampleRead += r
            }
        }

        val charset = detectCharset(sample, sampleRead)
        val chapters = mutableListOf<ChapterInfo>()
        var currentByteOffset = 0L
        var activeChapterTitle = "开始阅读"
        var activeChapterStart = 0L
        var hasDetectedHeading = false

        targetUtf8File.parentFile?.mkdirs()

        BufferedOutputStream(FileOutputStream(targetUtf8File), 65536).use { out ->
            inputStreamFactory().use { rawIn ->
                BufferedReader(InputStreamReader(rawIn, charset), 65536).use { reader ->
                    var firstLine = true
                    var blankRun = 0
                    while (true) {
                        val rawLine = reader.readLine() ?: break
                        var line = rawLine
                        if (firstLine) {
                            firstLine = false
                            if (line.startsWith("\uFEFF")) {
                                line = line.substring(1)
                            }
                        }

                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) {
                            blankRun++
                            if (blankRun > 1) continue
                        } else {
                            blankRun = 0
                        }

                        val isHeading = trimmed.length in 1..48 && chapterPatterns.any { it.matches(trimmed) }
                        if (isHeading) {
                            if (currentByteOffset > activeChapterStart) {
                                chapters.add(
                                    ChapterInfo(
                                        index = chapters.size,
                                        title = activeChapterTitle,
                                        startByte = activeChapterStart,
                                        endByte = currentByteOffset
                                    )
                                )
                            }
                            activeChapterTitle = trimmed
                            activeChapterStart = currentByteOffset
                            hasDetectedHeading = true
                        }

                        val normalizedLine = if (trimmed.isEmpty()) "\n" else "$trimmed\n"
                        val lineBytes = normalizedLine.toByteArray(StandardCharsets.UTF_8)
                        out.write(lineBytes)
                        currentByteOffset += lineBytes.size
                    }
                }
            }
        }

        if (currentByteOffset > activeChapterStart || chapters.isEmpty()) {
            chapters.add(
                ChapterInfo(
                    index = chapters.size,
                    title = if (hasDetectedHeading) activeChapterTitle else "全文",
                    startByte = activeChapterStart,
                    endByte = currentByteOffset
                )
            )
        }

        val finalChapters = if (!hasDetectedHeading && currentByteOffset > 24_000L) {
            splitLargeChapter(targetUtf8File, 0L, currentByteOffset, maxChunkBytes = 18_000L)
        } else {
            // Split any extra-large chapters (> 150 KB) so pagination stays instant
            val balanced = mutableListOf<ChapterInfo>()
            for (ch in chapters) {
                if (ch.endByte - ch.startByte > 150_000L) {
                    val sub = splitLargeChapter(targetUtf8File, ch.startByte, ch.endByte, maxChunkBytes = 60_000L, baseTitle = ch.title)
                    balanced.addAll(sub)
                } else {
                    balanced.add(ch)
                }
            }
            balanced.mapIndexed { idx, c -> c.copy(index = idx) }
        }

        return ImportResult(
            detectedEncoding = charset.name(),
            totalBytes = currentByteOffset,
            chapters = finalChapters
        )
    }

    private fun splitLargeChapter(
        utf8File: File,
        startByte: Long,
        endByte: Long,
        maxChunkBytes: Long,
        baseTitle: String? = null
    ): List<ChapterInfo> {
        val result = mutableListOf<ChapterInfo>()
        RandomAccessFile(utf8File, "r").use { raf ->
            var cursor = startByte
            var part = 1
            while (cursor < endByte) {
                var nextCut = minOf(cursor + maxChunkBytes, endByte)
                if (nextCut < endByte) {
                    raf.seek(nextCut)
                    while (nextCut < endByte) {
                        val b = raf.read()
                        nextCut++
                        if (b == '\n'.code || b == -1) break
                    }
                }
                val title = if (baseTitle != null) "$baseTitle ($part)" else "第 $part 部分"
                result.add(ChapterInfo(index = result.size, title = title, startByte = cursor, endByte = nextCut))
                cursor = nextCut
                part++
            }
        }
        return result
    }

    fun readChapterText(utf8File: File, chapter: ChapterInfo): String {
        val len = (chapter.endByte - chapter.startByte).coerceAtLeast(0L).toInt()
        if (len == 0) return ""
        val buf = ByteArray(len)
        RandomAccessFile(utf8File, "r").use { raf ->
            raf.seek(chapter.startByte)
            raf.readFully(buf)
        }
        // Format paragraphs with subtle indentation & double-newline breathing space
        val raw = String(buf, StandardCharsets.UTF_8)
        return raw.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n\n") { line -> "\u3000\u3000${line.trim()}" }
    }

    fun paginateChapter(
        text: String,
        widthPx: Int,
        heightPx: Int,
        textSizePx: Float,
        lineHeightMultiplier: Float,
        typeface: Typeface
    ): List<ReaderPage> {
        if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return listOf(ReaderPage(0, 0, text.length, text))
        }

        val paint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx
            this.typeface = typeface
        }

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineHeightMultiplier)
            .setIncludePad(false)
            .build()

        val pages = mutableListOf<ReaderPage>()
        var pageStartLine = 0
        var pageStartY = layout.getLineTop(0)

        for (line in 0 until layout.lineCount) {
            val lineBottom = layout.getLineBottom(line)
            if (lineBottom - pageStartY > heightPx && line > pageStartLine) {
                val startOffset = layout.getLineStart(pageStartLine)
                val endOffset = layout.getLineStart(line)
                pages.add(
                    ReaderPage(
                        pageIndex = pages.size,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        text = text.substring(startOffset, endOffset)
                    )
                )
                pageStartLine = line
                pageStartY = layout.getLineTop(line)
            }
        }

        if (pageStartLine < layout.lineCount) {
            val startOffset = layout.getLineStart(pageStartLine)
            val endOffset = text.length
            pages.add(
                ReaderPage(
                    pageIndex = pages.size,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    text = text.substring(startOffset, endOffset)
                )
            )
        }

        return pages.ifEmpty { listOf(ReaderPage(0, 0, text.length, text)) }
    }
}
