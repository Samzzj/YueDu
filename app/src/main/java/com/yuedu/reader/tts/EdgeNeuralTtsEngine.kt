package com.yuedu.reader.tts

import com.yuedu.reader.model.TtsVoiceOption
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Crash-safe Kotlin port of GitHub `rany2/edge-tts` (v7.3+ Sec-MS-GEC v2 protocol),
 * used as instant online preview / fallback before an offline voice pack is downloaded.
 */
object EdgeNeuralTtsEngine {

    private const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    private const val CHROMIUM_MAJOR_VERSION = "143"
    private const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
    private const val WSS_URL = "wss://$BASE_URL/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

    val neuralVoices: List<TtsVoiceOption> = listOf(
        // 云希作为安装包内置本地离线兜底默认音色，其同系列情感音色同步内置零延迟运行
        TtsVoiceOption(
            id = "edge:zh-CN-YunxiNeural",
            engineVoiceName = "zh-CN-YunxiNeural",
            displayLabel = "云希",
            styleTag = "阳光青年 · 热门小说男声",
            isNeural = true,
            offlineSid = SherpaOfflineTtsEngine.YUNXI_BUILTIN_SID,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-XiaoxiaoNeural",
            engineVoiceName = "zh-CN-XiaoxiaoNeural",
            displayLabel = "晓晓",
            styleTag = "温暖自然 · 小说女声",
            isNeural = true,
            offlineSid = 47,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-YunjianNeural",
            engineVoiceName = "zh-CN-YunjianNeural",
            displayLabel = "云健",
            styleTag = "热血沉稳 · 悬疑玄幻旁白",
            isNeural = true,
            offlineSid = 49,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-XiaoyiNeural",
            engineVoiceName = "zh-CN-XiaoyiNeural",
            displayLabel = "晓伊",
            styleTag = "甜美灵动 · 青春女声",
            isNeural = true,
            offlineSid = 48,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-YunxiaNeural",
            engineVoiceName = "zh-CN-YunxiaNeural",
            displayLabel = "云夏",
            styleTag = "清亮少年 · 轻小说音色",
            isNeural = true,
            offlineSid = 51,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-YunyangNeural",
            engineVoiceName = "zh-CN-YunyangNeural",
            displayLabel = "云扬",
            styleTag = "沉稳播音 · 纪实历史男声",
            isNeural = true,
            offlineSid = 52,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-liaoning-XiaobeiNeural",
            engineVoiceName = "zh-CN-liaoning-XiaobeiNeural",
            displayLabel = "晓北",
            styleTag = "爽朗亲切 · 活力女声",
            isNeural = true,
            offlineSid = 45,
            isBuiltinOffline = true
        ),
        TtsVoiceOption(
            id = "edge:zh-CN-shaanxi-XiaoniNeural",
            engineVoiceName = "zh-CN-shaanxi-XiaoniNeural",
            displayLabel = "晓妮",
            styleTag = "温婉叙事 · 故事女声",
            isNeural = true,
            offlineSid = 46,
            isBuiltinOffline = true
        ),
        // 独立可选离线扩展音色（一对一独立下载，不联动其他音色）
        TtsVoiceOption(
            id = "matcha:baker",
            engineVoiceName = "zh-CN-XiaohanNeural",
            displayLabel = "小贝",
            styleTag = "温润自然 · 广播级女声旁白",
            isNeural = true,
            offlinePackId = SherpaOfflineTtsEngine.PACK_MATCHA_BAKER,
            offlineSid = 0,
            offlineSizeLabel = "72MB",
            isBuiltinOffline = false
        )
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeToMp3Bytes(
        text: String,
        voiceName: String,
        speechRate: Float
    ): ByteArray {
        return try {
            executeSynthesis(text, voiceName, speechRate)
        } catch (e: SkewException) {
            EdgeDrm.adjustClockSkew(e.serverDateHeader)
            executeSynthesis(text, voiceName, speechRate)
        }
    }

    private suspend fun executeSynthesis(
        text: String,
        voiceName: String,
        speechRate: Float
    ): ByteArray = suspendCancellableCoroutine { cont ->
        val connId = UUID.randomUUID().toString().replace("-", "")
        val gec = EdgeDrm.generateSecMsGecV2()
        val url = "$WSS_URL&ConnectionId=$connId&Sec-MS-GEC=$gec&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0"
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cookie", "muid=${EdgeDrm.randomMuid()};")
            .build()

        val audioStream = ByteArrayOutputStream()
        val finished = AtomicBoolean(false)

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val ts = jsUtcDateString()
                val configMsg = "X-Timestamp:$ts\r\n" +
                    "Content-Type:application/json; charset=utf-8\r\n" +
                    "Path:speech.config\r\n\r\n" +
                    """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""" +
                    "\r\n"
                webSocket.send(configMsg)

                val ratePct = ((speechRate - 1.0f) * 100f).toInt()
                val rateStr = if (ratePct >= 0) "+$ratePct%" else "$ratePct%"
                val escaped = escapeXml(cleanText(text))
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
                    "<voice name='$voiceName'>" +
                    "<prosody pitch='+0Hz' rate='$rateStr' volume='+0%'>$escaped</prosody>" +
                    "</voice></speak>"
                val reqId = UUID.randomUUID().toString().replace("-", "")
                val ssmlMsg = "X-RequestId:$reqId\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "X-Timestamp:${ts}Z\r\n" +
                    "Path:ssml\r\n\r\n" +
                    ssml
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, textMsg: String) {
                if (textMsg.contains("Path:turn.end") && finished.compareAndSet(false, true)) {
                    runCatching { webSocket.close(1000, "done") }
                    val bytes = synchronized(audioStream) { audioStream.toByteArray() }
                    if (cont.isActive) {
                        if (bytes.isEmpty()) {
                            cont.resumeWithException(IllegalStateException("未收到音频数据"))
                        } else {
                            cont.resume(bytes)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val raw = bytes.toByteArray()
                if (raw.size < 2) return
                val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                val dataOffset = headerLen + 2
                if (dataOffset <= raw.size) {
                    val headerStr = String(raw, 2, headerLen, StandardCharsets.UTF_8)
                    if (headerStr.contains("Path:audio") && headerStr.contains("Content-Type:audio/mpeg")) {
                        synchronized(audioStream) {
                            audioStream.write(raw, dataOffset, raw.size - dataOffset)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    val dateHdr = response?.header("Date")
                    if (response?.code == 403 && dateHdr != null) {
                        cont.resumeWithException(SkewException(dateHdr, t))
                    } else {
                        cont.resumeWithException(t)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    val bytes = synchronized(audioStream) { audioStream.toByteArray() }
                    if (bytes.isNotEmpty()) {
                        cont.resume(bytes)
                    } else {
                        cont.resumeWithException(IllegalStateException("连接关闭: $reason"))
                    }
                }
            }
        })

        cont.invokeOnCancellation {
            finished.set(true)
            runCatching { ws.cancel() }
        }
    }

    private class SkewException(val serverDateHeader: String, cause: Throwable) : Exception(cause)

    private fun cleanText(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val code = c.code
            if (code in 0..8 || code in 11..12 || code in 14..31) sb.append(' ') else sb.append(c)
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun jsUtcDateString(): String {
        val fmt = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private object EdgeDrm {
        @Volatile
        private var clockSkewSeconds: Double = 0.0
        private const val WIN_EPOCH = 11644473600L

        private val SHA256_INITIAL = uintArrayOf(
            0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au,
            0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u
        )
        private val SHA256_STATE = uintArrayOf(
            0xFD9FBEA2u, 0x91243CE7u, 0xBE499094u, 0xCD2E1E0Au,
            0x6C006F49u, 0x04BD35ABu, 0xFEB0A7E4u, 0x03EFFBD2u
        )
        private val SHA256_SCHEDULE = uintArrayOf(
            0xEB24BF9Eu, 0x1558691Fu, 0xA0C09EE2u, 0x0CFA32BAu,
            0xA622EE8Cu, 0xD1DD9491u, 0xC0DD25A0u, 0xF9E70AB6u,
            0xF0C31573u, 0x7058CE4Eu, 0x7188E606u, 0x00E5AB33u,
            0xD258B060u, 0x72766564u
        )
        private val SHA256_ROUND_CONSTANTS = uintArrayOf(
            0x428A2F98u, 0x71374491u, 0xB5C0FBCFu, 0xE9B5DBA5u, 0x3956C25Bu, 0x59F111F1u, 0x923F82A4u, 0xAB1C5ED5u,
            0xD807AA98u, 0x12835B01u, 0x243185BEu, 0x550C7DC3u, 0x72BE5D74u, 0x80DEB1FEu, 0x9BDC06A7u, 0xC19BF174u,
            0xE49B69C1u, 0xEFBE4786u, 0x0FC19DC6u, 0x240CA1CCu, 0x2DE92C6Fu, 0x4A7484AAu, 0x5CB0A9DCu, 0x76F988DAu,
            0x983E5152u, 0xA831C66Du, 0xB00327C8u, 0xBF597FC7u, 0xC6E00BF3u, 0xD5A79147u, 0x06CA6351u, 0x14292967u,
            0x27B70A85u, 0x2E1B2138u, 0x4D2C6DFCu, 0x53380D13u, 0x650A7354u, 0x766A0ABBu, 0x81C2C92Eu, 0x92722C85u,
            0xA2BFE8A1u, 0xA81A664Bu, 0xC24B8B70u, 0xC76C51A3u, 0xD192E819u, 0xD6990624u, 0xF40E3585u, 0x106AA070u,
            0x19A4C116u, 0x1E376C08u, 0x2748774Cu, 0x34B0BCB5u, 0x391C0CB3u, 0x4ED8AA4Au, 0x5B9CCA4Fu, 0x682E6FF3u,
            0x748F82EEu, 0x78A5636Fu, 0x84C87814u, 0x8CC70208u, 0x90BEFFFAu, 0xA4506CEBu, 0xBEF9A3F7u, 0xC67178F2u
        )
        private const val SHA256_ADJUSTMENT = 0xC9DC06A7u
        private val SHA256_BLOCK_CONSTANTS = SHA256_ROUND_CONSTANTS.copyOf().apply {
            this[14] = SHA256_ADJUSTMENT
        }
        private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80u
        private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0u

        private val CS64_COEFFICIENTS = arrayOf(
            0xE82E91B1u to 0xB32700D0u, 0x44DE24C6u to 0x88DFF51Du,
            0x3BEB65BCu to 0x29514038u, 0xC89C5933u to 0xB328CBDFu,
            0x8D1EC211u to 0xD405B4D5u, 0x314EF5D5u to 0x26E55F3Cu,
            0xE92491B1u to 0x607801C6u, 0xF0A5B9D5u to 0x82B2C1FBu,
            0x06064CF3u to 0xFA51A3DAu, 0xDA79FA60u to 0x207CC464u,
            0xBED20560u to 0x3C3C74F3u, 0x89F9C9F8u to 0xD2D14B61u,
            0x35FA38C6u to 0xBB04A2C1u, 0xCB49E3C6u to 0xC5626167u
        )
        private const val CS64_FACTOR = 0x82893933u
        private const val CS64_ADJUSTMENT0 = 0xF2F90B1Au
        private const val CS64_ADJUSTMENT1 = 0x0B1A0000u

        fun randomMuid(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02X".format(it) }
        }

        fun adjustClockSkew(serverDateHeader: String) {
            runCatching {
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                val parsed = fmt.parse(serverDateHeader) ?: return
                val serverSec = parsed.time / 1000.0
                val clientSec = System.currentTimeMillis() / 1000.0 + clockSkewSeconds
                clockSkewSeconds += (serverSec - clientSec)
            }
        }

        fun generateSecMsGecV2(): String {
            val nowSec = (System.currentTimeMillis() / 1000.0 + clockSkewSeconds).toLong()
            val winSec = nowSec + WIN_EPOCH
            val rounded = winSec - (winSec % 300L)
            val ticks = rounded * 10_000_000L
            val data = "${ticks}$TRUSTED_CLIENT_TOKEN".toByteArray(StandardCharsets.US_ASCII)
            val digest = sha256Digest(data)
            val (w0, w1) = cs64Digest(data, digest)
            val cs64Hex = buildString(16) {
                appendLeUIntHex(w0)
                appendLeUIntHex(w1)
            }
            val shaHex = digest.joinToString("") { "%02X".format(it) }
            return "$cs64Hex-$shaHex"
        }

        private fun StringBuilder.appendLeUIntHex(v: UInt) {
            append("%02X".format((v and 0xFFu).toInt()))
            append("%02X".format(((v shr 8) and 0xFFu).toInt()))
            append("%02X".format(((v shr 16) and 0xFFu).toInt()))
            append("%02X".format(((v shr 24) and 0xFFu).toInt()))
        }

        private fun rotr(x: UInt, n: Int): UInt = (x shr n) or (x shl (32 - n))
        private fun rol16(v: UInt): UInt = (v shl 16) or (v shr 16)
        private fun sigma0(x: UInt): UInt = (x shr 7) xor (x shl 25) xor (x shr 18) xor (x shl 14) xor (x shr 3)
        private fun sigma1(x: UInt): UInt = (x shr 17) xor (x shl 15) xor (x shr 19) xor (x shl 13) xor (x shr 10)

        private fun sha256Rounds(state: UIntArray, words: UIntArray, firstRound: Int = 0): UIntArray {
            var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
            var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
            val constants = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS
            for (i in firstRound until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor ((e.inv()) and g)
                val t1 = h + s1 + ch + constants[i] + words[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            return uintArrayOf(a, b, c, d, e, f, g, h)
        }

        private fun expandSchedule(words: UIntArray, firstWord: Int) {
            for (i in firstWord until 64) {
                words[i] = sigma1(words[i - 2]) + words[i - 7] + sigma0(words[i - 15]) + words[i - 16]
            }
        }

        private fun sha256Init(dataPrefix7: ByteArray): UIntArray {
            val words = UIntArray(64)
            words[14] = (((dataPrefix7[0].toInt() and 0xFF) shl 16) or
                ((dataPrefix7[1].toInt() and 0xFF) shl 8) or
                (dataPrefix7[2].toInt() and 0xFF)).toUInt()
            words[15] = (((dataPrefix7[3].toInt() and 0xFF) shl 24) or
                ((dataPrefix7[4].toInt() and 0xFF) shl 16) or
                ((dataPrefix7[5].toInt() and 0xFF) shl 8) or
                (dataPrefix7[6].toInt() and 0xFF)).toUInt()

            for (idx in 16 until 30) {
                var value = SHA256_SCHEDULE[idx - 16]
                if (idx - 2 >= 14) value += if (idx == 16) (sigma1(words[idx - 2]) xor SHA256_SIGMA1_ADJUSTMENT) else sigma1(words[idx - 2])
                if (idx - 7 >= 14) value += words[idx - 7]
                if (idx - 15 >= 14) value += if (idx == 29) (sigma0(words[idx - 15]) xor SHA256_SIGMA0_ADJUSTMENT) else sigma0(words[idx - 15])
                words[idx] = value
            }
            words[30] = sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] +
                SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]
            expandSchedule(words, 31)
            val working = sha256Rounds(SHA256_STATE, words, 14)
            return UIntArray(8) { i -> SHA256_INITIAL[i] + working[i] }
        }

        private fun sha256Digest(data: ByteArray): ByteArray {
            val totalLen = 57L + data.size
            val remLen = data.size - 7
            val zeroPadLen = ((55 - (totalLen % 64)).toInt() + 64) % 64
            val padded = ByteArray(remLen + 1 + zeroPadLen + 8)
            System.arraycopy(data, 7, padded, 0, remLen)
            padded[remLen] = 0x80.toByte()
            val bitLen = totalLen * 8L
            val lenPos = padded.size - 8
            for (i in 0 until 8) {
                padded[lenPos + i] = ((bitLen ushr (56 - i * 8)) and 0xFFL).toByte()
            }

            var state = sha256Init(data)
            val words = UIntArray(64)
            var offset = 0
            while (offset < padded.size) {
                for (i in 0 until 16) {
                    val p = offset + i * 4
                    words[i] = (((padded[p].toInt() and 0xFF) shl 24) or
                        ((padded[p + 1].toInt() and 0xFF) shl 16) or
                        ((padded[p + 2].toInt() and 0xFF) shl 8) or
                        (padded[p + 3].toInt() and 0xFF)).toUInt()
                }
                expandSchedule(words, 16)
                val working = sha256Rounds(state, words, 0)
                for (i in 0 until 8) state[i] = state[i] + working[i]
                offset += 64
            }

            val out = ByteArray(32)
            for (i in 0 until 8) {
                val w = state[i]
                out[i * 4] = ((w shr 24) and 0xFFu).toByte()
                out[i * 4 + 1] = ((w shr 16) and 0xFFu).toByte()
                out[i * 4 + 2] = ((w shr 8) and 0xFFu).toByte()
                out[i * 4 + 3] = (w and 0xFFu).toByte()
            }
            return out
        }

        private fun wordsLe4Even(data: ByteArray): List<UInt> {
            val padCount = (-data.size).mod(4)
            val padded = ByteArray(data.size + padCount)
            System.arraycopy(data, 0, padded, 0, data.size)
            val list = ArrayList<UInt>(padded.size / 4 + 1)
            var i = 0
            while (i < padded.size) {
                list.add(
                    ((padded[i].toInt() and 0xFF) or
                        ((padded[i + 1].toInt() and 0xFF) shl 8) or
                        ((padded[i + 2].toInt() and 0xFF) shl 16) or
                        ((padded[i + 3].toInt() and 0xFF) shl 24)).toUInt()
                )
                i += 4
            }
            if (list.size % 2 != 0) list.add(0u)
            return list
        }

        private fun cs64Digest(data: ByteArray, digest: ByteArray): Pair<UInt, UInt> {
            val keyA = (((digest[0].toInt() and 0xFF) or ((digest[1].toInt() and 0xFF) shl 8) or
                ((digest[2].toInt() and 0xFF) shl 16) or ((digest[3].toInt() and 0xFF) shl 24)).toUInt()) or 1u
            val keyB = (((digest[4].toInt() and 0xFF) or ((digest[5].toInt() and 0xFF) shl 8) or
                ((digest[6].toInt() and 0xFF) shl 16) or ((digest[7].toInt() and 0xFF) shl 24)).toUInt()) or 1u

            val factor0 = keyA * CS64_FACTOR
            val factor1 = keyB * CS64_FACTOR
            val terms = ArrayList<UInt>(32)
            val offsets = ArrayList<UInt>(32)
            CS64_COEFFICIENTS.forEachIndexed { idx, (coeff, off) ->
                terms.add(coeff * (if (idx % 2 == 0) factor0 else factor1))
                offsets.add(off)
            }

            val prefixed = ByteArray(data.size + 1)
            System.arraycopy(data, 0, prefixed, 1, data.size)
            wordsLe4Even(prefixed).forEachIndexed { idx, word ->
                var term = word * (if (idx % 2 == 0) keyA else keyB)
                var off = rol16(word) * (if (idx % 2 == 0) 0xEF0569FBu else 0xC31713DBu)
                if (idx == 0) {
                    term += CS64_ADJUSTMENT0 * factor0
                    off += CS64_ADJUSTMENT1
                }
                terms.add(term)
                offsets.add(off)
            }

            var swapAcc = 0u; var swapLast = 0u; var state = 0u; var acc = 0u
            var idx = 0
            while (idx < terms.size) {
                val x = terms[idx]; val y = terms[idx + 1]
                var t = rol16(offsets[idx] + x)
                var q = t * 0x76F50F34u
                swapAcc += rol16(q) * 0xC3EFEA97u + q
                t = rol16(offsets[idx + 1] + y)
                q = t * 0x1881CE3Cu
                swapLast = rol16(q) * 0x35BD1EC9u + q
                swapAcc += swapLast

                state = state * keyA + x
                state = rol16(state) * 0xCF98B111u
                state = rol16(state) * 0x87085B9Fu
                state = rol16(state) * 0x12CEB96Du
                state = rol16(state) * 0x257E1D83u
                acc += state
                t = state * keyB + y
                t = rol16(t) * 0xA27416F5u
                t = rol16(t) * 0xD38396FFu
                state = rol16(rol16(t) * 0x7C932B89u) * 0xBFA49F69u
                acc += state
                idx += 2
            }
            return (swapLast xor state) to (swapAcc xor acc)
        }
    }
}
