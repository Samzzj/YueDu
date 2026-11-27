package com.yuedu.reader.data

import com.yuedu.reader.model.DownloadableFont
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object CommercialFontsCatalog {

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun fastChinaRepoMirrors(ghOrgRepoBranchPath: String, releaseUrl: String? = null): List<String> {
        val parts = ghOrgRepoBranchPath.split("@", limit = 2)
        val repo = parts[0]
        val branchAndPath = parts[1]
        val branch = branchAndPath.substringBefore("/")
        val path = branchAndPath.substringAfter("/")
        val rawGithub = releaseUrl ?: "https://github.com/$repo/raw/$branch/$path"
        return listOf(
            "https://testingcf.jsdelivr.net/gh/$ghOrgRepoBranchPath",
            "https://fastly.jsdelivr.net/gh/$ghOrgRepoBranchPath",
            "https://gcore.jsdelivr.net/gh/$ghOrgRepoBranchPath",
            "https://gh-proxy.com/$rawGithub"
        )
    }

    val curatedFonts: List<DownloadableFont> = listOf(
        DownloadableFont(
            id = "ofl_lxgw_wenkai",
            displayName = "霞鹜文楷",
            styleTag = "温润楷书 · 小说首选",
            sizeLabel = "18.2 MB",
            fileName = "LXGWWenKai-Regular.ttf",
            downloadUrls = fastChinaRepoMirrors(
                "lxgw/LxgwWenKai@v1.501/fonts/TTF/LXGWWenKai-Regular.ttf",
                "https://github.com/lxgw/LxgwWenKai/releases/download/v1.501/LXGWWenKai-Regular.ttf"
            )
        ),
        DownloadableFont(
            id = "ofl_lxgw_wenkai_mono",
            displayName = "霞鹜文楷等宽",
            styleTag = "等宽手写 · 清晰耐看",
            sizeLabel = "18.3 MB",
            fileName = "LXGWWenKaiMono-Regular.ttf",
            downloadUrls = fastChinaRepoMirrors(
                "lxgw/LxgwWenKai@v1.501/fonts/TTF/LXGWWenKaiMono-Regular.ttf",
                "https://github.com/lxgw/LxgwWenKai/releases/download/v1.501/LXGWWenKaiMono-Regular.ttf"
            )
        ),
        DownloadableFont(
            id = "ofl_noto_serif_sc",
            displayName = "思源宋体 (Noto Serif SC)",
            styleTag = "经典宋体 · 出版质感",
            sizeLabel = "14.1 MB",
            fileName = "NotoSerifSC-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/notoserifsc/v36/H4cyBXePl9DZ0Xe7gG9cyOj7uK2-n-D2rd4FY7SCqyWv.ttf",
                "https://gh-proxy.com/https://github.com/google/fonts/raw/main/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf"
            )
        ),
        DownloadableFont(
            id = "ofl_noto_sans_sc",
            displayName = "思源黑体 (Noto Sans SC)",
            styleTag = "现代黑体 · 极简清爽",
            sizeLabel = "10.1 MB",
            fileName = "NotoSansSC-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/notosanssc/v41/k3kCo84MPvpLmixcA63oeAL7Iqp5IZJF9bmaG9_FnYw.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf")
        ),
        DownloadableFont(
            id = "ofl_zcool_xiaowei",
            displayName = "站酷小薇 LOGO 体",
            styleTag = "优雅明体 · 标题正文皆宜",
            sizeLabel = "6.0 MB",
            fileName = "ZCOOLXiaoWei-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/zcoolxiaowei/v15/i7dMIFFrTRywPpUVX9_RJyM1YFI.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/zcoolxiaowei/ZCOOLXiaoWei-Regular.ttf")
        ),
        DownloadableFont(
            id = "ofl_zcool_kuaile",
            displayName = "站酷快乐体",
            styleTag = "圆润活泼 · 轻松阅读",
            sizeLabel = "1.4 MB",
            fileName = "ZCOOLKuaiLe-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/zcoolkuaile/v22/tssqApdaRQokwFjFJjvM6h2Wpg.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/zcoolkuaile/ZCOOLKuaiLe-Regular.ttf")
        ),
        DownloadableFont(
            id = "ofl_zcool_huangyou",
            displayName = "站酷庆科黄油体",
            styleTag = "圆角几何 · 现代感强",
            sizeLabel = "7.9 MB",
            fileName = "ZCOOLQingKeHuangYou-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/zcoolqingkehuangyou/v16/2Eb5L_R5IXJEWhD3AOhSvFC554MOOahI4mRIiw.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/zcoolqingkehuangyou/ZCOOLQingKeHuangYou-Regular.ttf")
        ),
        DownloadableFont(
            id = "ofl_ma_shan_zheng",
            displayName = "马善政毛笔楷书",
            styleTag = "手写毛笔 · 古风韵味",
            sizeLabel = "5.6 MB",
            fileName = "MaShanZheng-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/mashanzheng/v18/NaPecZTRCLxvwo41b4gvzkXaRMQ.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/mashanzheng/MaShanZheng-Regular.ttf")
        ),
        DownloadableFont(
            id = "ofl_zhi_mang_xing",
            displayName = "钟齐志莽行书",
            styleTag = "潇洒行书 · 书法风格",
            sizeLabel = "3.9 MB",
            fileName = "ZhiMangXing-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/zhimangxing/v19/f0Xw0ey79sErYFtWQ9a2rq-g0ac.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/zhimangxing/ZhiMangXing-Regular.ttf")
        ),
        DownloadableFont(
            id = "ofl_long_cang",
            displayName = "龙藏体",
            styleTag = "飘逸行楷 · 轻灵俊秀",
            sizeLabel = "4.9 MB",
            fileName = "LongCang-Regular.ttf",
            downloadUrls = listOf(
                "https://fonts.gstatic.com/s/longcang/v21/LYjAdGP8kkgoTec8zkRgrQ.ttf"
            ) + fastChinaRepoMirrors("google/fonts@main/ofl/longcang/LongCang-Regular.ttf")
        )
    )

    fun downloadFontFromMirrors(
        urls: List<String>,
        destFile: File,
        onProgress: (Float) -> Unit
    ) {
        var lastError: Exception? = null
        for (urlStr in urls) {
            try {
                destFile.delete()
                downloadSingleUrl(urlStr, destFile, onProgress)
                if (destFile.exists() && destFile.length() > 65_536L) {
                    return
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("镜像下载失败，请检查网络连接")
    }

    fun downloadSingleUrl(
        urlStr: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ) {
        val request = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("响应为空")
            val total = body.contentLength()
            var downloaded = 0L
            var lastReportedProgress = -1f
            val buf = ByteArray(65536)
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            val p = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            if (p - lastReportedProgress >= 0.015f || p >= 1f) {
                                lastReportedProgress = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            onProgress(1f)
        }
    }
}
