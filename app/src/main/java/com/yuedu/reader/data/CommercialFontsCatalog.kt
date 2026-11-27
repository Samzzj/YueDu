package com.yuedu.reader.data

import com.yuedu.reader.model.DownloadableFont
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object CommercialFontsCatalog {

    val freeCommercialFonts: List<DownloadableFont> = listOf(
        DownloadableFont(
            id = "ofl_lxgw_wenkai",
            displayName = "霞鹜文楷",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "温润楷书 · 小说首选",
            sizeLabel = "18.2 MB",
            fileName = "LXGWWenKai-Regular.ttf",
            downloadUrl = "https://github.com/lxgw/LxgwWenKai/releases/download/v1.501/LXGWWenKai-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_lxgw_wenkai_mono",
            displayName = "霞鹜文楷等宽",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "等宽手写 · 清晰耐看",
            sizeLabel = "18.3 MB",
            fileName = "LXGWWenKaiMono-Regular.ttf",
            downloadUrl = "https://github.com/lxgw/LxgwWenKai/releases/download/v1.501/LXGWWenKaiMono-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_noto_sans_sc",
            displayName = "思源黑体 (Noto Sans SC)",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "现代黑体 · 极简清爽",
            sizeLabel = "17.0 MB",
            fileName = "NotoSansSC-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf"
        ),
        DownloadableFont(
            id = "ofl_noto_serif_sc",
            displayName = "思源宋体 (Noto Serif SC)",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "经典宋体 · 出版质感",
            sizeLabel = "24.0 MB",
            fileName = "NotoSerifSC-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf"
        ),
        DownloadableFont(
            id = "ofl_zcool_xiaowei",
            displayName = "站酷小薇 LOGO 体",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "优雅明体 · 标题正文皆宜",
            sizeLabel = "6.0 MB",
            fileName = "ZCOOLXiaoWei-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/zcoolxiaowei/ZCOOLXiaoWei-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_zcool_kuaile",
            displayName = "站酷快乐体",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "圆润活泼 · 轻松阅读",
            sizeLabel = "1.4 MB",
            fileName = "ZCOOLKuaiLe-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/zcoolkuaile/ZCOOLKuaiLe-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_zcool_huangyou",
            displayName = "站酷庆科黄油体",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "圆角几何 · 现代感强",
            sizeLabel = "7.9 MB",
            fileName = "ZCOOLQingKeHuangYou-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/zcoolqingkehuangyou/ZCOOLQingKeHuangYou-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_ma_shan_zheng",
            displayName = "马善政毛笔楷书",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "手写毛笔 · 古风韵味",
            sizeLabel = "5.6 MB",
            fileName = "MaShanZheng-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/mashanzheng/MaShanZheng-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_zhi_mang_xing",
            displayName = "钟齐志莽行书",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "潇洒行书 · 书法风格",
            sizeLabel = "3.9 MB",
            fileName = "ZhiMangXing-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/zhimangxing/ZhiMangXing-Regular.ttf"
        ),
        DownloadableFont(
            id = "ofl_long_cang",
            displayName = "龙藏体",
            license = "SIL OFL 1.1 · 可商用",
            styleTag = "飘逸行楷 · 轻灵俊秀",
            sizeLabel = "4.9 MB",
            fileName = "LongCang-Regular.ttf",
            downloadUrl = "https://github.com/google/fonts/raw/main/ofl/longcang/LongCang-Regular.ttf"
        )
    )

    fun downloadFontFile(
        urlStr: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ) {
        var currentUrl = urlStr
        var conn: HttpURLConnection? = null
        var redirects = 0

        while (redirects < 8) {
            val url = URL(currentUrl)
            val c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "YueDu-Android/0.2.0")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("重定向地址无效")
                c.disconnect()
                currentUrl = URL(url, loc).toString()
                redirects++
                continue
            }
            conn = c
            break
        }

        val activeConn = conn ?: throw IllegalStateException("下载重定向次数过多")
        try {
            if (activeConn.responseCode !in 200..299) {
                throw IllegalStateException("下载失败 (HTTP ${activeConn.responseCode})")
            }
            val total = activeConn.contentLengthLong
            var downloaded = 0L
            val buf = ByteArray(32768)
            activeConn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
        } finally {
            activeConn.disconnect()
        }
    }
}
