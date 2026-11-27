package com.yuedu.reader.engine

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yuedu.reader.model.ChapterInfo
import com.yuedu.reader.model.PageSlice
import com.yuedu.reader.model.ReaderPage
import com.yuedu.reader.model.SearchHit
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
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

    data class ChapterParagraph(
        val text: String,
        val isTitle: Boolean,
        val charStartOffset: Int
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

    fun readChapterParagraphs(utf8File: File, chapter: ChapterInfo): List<ChapterParagraph> {
        val len = (chapter.endByte - chapter.startByte).coerceAtLeast(0L).toInt()
        if (len == 0) return emptyList()
        val buf = ByteArray(len)
        RandomAccessFile(utf8File, "r").use { raf ->
            raf.seek(chapter.startByte)
            raf.readFully(buf)
        }
        return parseParagraphsFromRaw(String(buf, StandardCharsets.UTF_8), chapter.title)
    }

    private fun parseParagraphsFromRaw(raw: String, chapterTitle: String): List<ChapterParagraph> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val result = ArrayList<ChapterParagraph>(lines.size)
        var offset = 0
        lines.forEachIndexed { idx, line ->
            val isHeading = idx == 0 && (line == chapterTitle || chapterPatterns.any { it.matches(line) })
            val formatted = if (isHeading) line else "\u3000\u3000$line"
            result.add(ChapterParagraph(text = formatted, isTitle = isHeading, charStartOffset = offset))
            offset += formatted.length + 1
        }
        return result
    }

    fun searchInBook(
        utf8File: File,
        chapters: List<ChapterInfo>,
        query: String,
        maxResults: Int = 80
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty() || !utf8File.exists()) return emptyList()
        val hits = mutableListOf<SearchHit>()

        RandomAccessFile(utf8File, "r").use { raf ->
            for (ch in chapters) {
                if (hits.size >= maxResults) break
                val len = (ch.endByte - ch.startByte).coerceAtLeast(0L).toInt()
                if (len == 0) continue
                val buf = ByteArray(len)
                raf.seek(ch.startByte)
                raf.readFully(buf)
                val raw = String(buf, StandardCharsets.UTF_8)
                if (!raw.contains(q, ignoreCase = true)) continue

                val paragraphs = parseParagraphsFromRaw(raw, ch.title)
                for (p in paragraphs) {
                    var idx = p.text.indexOf(q, ignoreCase = true)
                    while (idx >= 0) {
                        val snippetStart = (idx - 14).coerceAtLeast(0)
                        val snippetEnd = (idx + q.length + 28).coerceAtMost(p.text.length)
                        val prefix = if (snippetStart > 0) "…" else ""
                        val suffix = if (snippetEnd < p.text.length) "…" else ""
                        val snippet = prefix + p.text.substring(snippetStart, snippetEnd).trim() + suffix
                        hits.add(
                            SearchHit(
                                chapterIndex = ch.index,
                                chapterTitle = ch.title,
                                charOffset = p.charStartOffset + idx,
                                snippet = snippet
                            )
                        )
                        if (hits.size >= maxResults) break
                        idx = p.text.indexOf(q, startIndex = idx + q.length, ignoreCase = true)
                    }
                    if (hits.size >= maxResults) break
                }
            }
        }
        return hits
    }

    fun paginateParagraphs(
        paragraphs: List<ChapterParagraph>,
        widthPx: Int,
        heightPx: Int,
        textSizePx: Float,
        lineHeightMultiplier: Float,
        paragraphSpacingPx: Float,
        typeface: Typeface
    ): List<ReaderPage> {
        if (paragraphs.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return listOf(ReaderPage(0, 0, 0, emptyList()))
        }

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx
            this.typeface = typeface
        }
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx * 1.16f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }

        val pages = mutableListOf<ReaderPage>()
        var currentSlices = mutableListOf<PageSlice>()
        var pageStartOffset = paragraphs.first().charStartOffset
        var currentY = 0f

        fun flushPage(endOffset: Int) {
            if (currentSlices.isNotEmpty()) {
                pages.add(
                    ReaderPage(
                        pageIndex = pages.size,
                        startOffset = pageStartOffset,
                        endOffset = endOffset,
                        slices = currentSlices.toList()
                    )
                )
                currentSlices = mutableListOf()
                currentY = 0f
            }
        }

        for (para in paragraphs) {
            val paint = if (para.isTitle) titlePaint else bodyPaint
            val layout = StaticLayout.Builder
                .obtain(para.text, 0, para.text.length, paint, widthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineHeightMultiplier)
                .setIncludePad(false)
                .build()

            var sliceStartLine = 0
            for (line in 0 until layout.lineCount) {
                val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                if (currentY > 0f && currentY + lineHeight > heightPx) {
                    if (line > sliceStartLine) {
                        val s = layout.getLineStart(sliceStartLine)
                        val e = layout.getLineStart(line)
                        val sliceText = para.text.substring(s, e).trimEnd('\n')
                        currentSlices.add(
                            PageSlice(
                                text = sliceText,
                                startOffset = para.charStartOffset + s,
                                endOffset = para.charStartOffset + s + sliceText.length,
                                isTitle = para.isTitle,
                                hasBottomGap = false
                            )
                        )
                        flushPage(para.charStartOffset + e)
                        pageStartOffset = para.charStartOffset + e
                        sliceStartLine = line
                    } else {
                        flushPage(para.charStartOffset + layout.getLineStart(line))
                        pageStartOffset = para.charStartOffset + layout.getLineStart(line)
                    }
                }
                currentY += lineHeight
            }

            if (sliceStartLine < layout.lineCount) {
                val s = layout.getLineStart(sliceStartLine)
                val e = para.text.length
                val sliceText = para.text.substring(s, e).trimEnd('\n')
                val extraGap = if (para.isTitle) maxOf(paragraphSpacingPx, textSizePx * 0.55f) else paragraphSpacingPx
                val fitsGap = currentY + extraGap <= heightPx
                currentSlices.add(
                    PageSlice(
                        text = sliceText,
                        startOffset = para.charStartOffset + s,
                        endOffset = para.charStartOffset + s + sliceText.length,
                        isTitle = para.isTitle,
                        hasBottomGap = fitsGap
                    )
                )
                if (fitsGap) {
                    currentY += extraGap
                } else {
                    flushPage(para.charStartOffset + e)
                    pageStartOffset = para.charStartOffset + e
                }
            }
        }

        if (currentSlices.isNotEmpty()) {
            val lastPara = paragraphs.last()
            flushPage(lastPara.charStartOffset + lastPara.text.length)
        }

        return pages.ifEmpty { listOf(ReaderPage(0, 0, 0, emptyList())) }
    }
}
