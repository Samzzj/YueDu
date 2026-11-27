package com.yuedu.reader.server

import android.content.Context
import com.yuedu.reader.data.BookRepository
import com.yuedu.reader.model.TransferKind
import com.yuedu.reader.model.WifiTransferLog
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class WifiTransferServer(
    private val context: Context,
    private val repository: BookRepository
) {
    private var httpd: EmbeddedHttpd? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _recentTransfers = MutableStateFlow<List<WifiTransferLog>>(emptyList())
    val recentTransfers: StateFlow<List<WifiTransferLog>> = _recentTransfers.asStateFlow()

    private val _events = MutableSharedFlow<WifiTransferLog>(extraBufferCapacity = 16)
    val events: SharedFlow<WifiTransferLog> = _events.asSharedFlow()

    fun start(): Result<String> {
        if (_isRunning.value && _serverUrl.value != null) {
            return Result.success(_serverUrl.value!!)
        }
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)

        for (port in 8080..8090) {
            try {
                val instance = EmbeddedHttpd(port)
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                httpd = instance
                val ip = resolveLocalWifiIp() ?: "127.0.0.1"
                val url = "http://$ip:$port"
                _serverUrl.value = url
                _isRunning.value = true
                return Result.success(url)
            } catch (_: Exception) {
                // Try next port
            }
        }
        return Result.failure(IllegalStateException("端口 8080-8090 均被占用"))
    }

    fun refreshUrl(): String? {
        val current = _serverUrl.value ?: return null
        val port = current.substringAfterLast(':', "8080")
        val ip = resolveLocalWifiIp() ?: return current
        val updated = "http://$ip:$port"
        _serverUrl.value = updated
        return updated
    }

    fun stop() {
        runCatching { httpd?.stop() }
        httpd = null
        _isRunning.value = false
        _serverUrl.value = null
    }

    fun resolveLocalWifiIp(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val candidates = mutableListOf<Pair<Int, String>>()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") -> 1
                    name.startsWith("ap") || name.startsWith("swlan") -> 2
                    name.startsWith("eth") -> 3
                    else -> 4
                }
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        candidates.add(priority to addr.hostAddress!!)
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second
        }.getOrNull()
    }

    private inner class EmbeddedHttpd(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: "/"
            return when {
                session.method == Method.GET && uri == "/" -> {
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildWebPortalHtml())
                }
                session.method == Method.GET && uri == "/api/library" -> {
                    val json = JSONObject().apply {
                        put("books", JSONArray().apply {
                            repository.books.value.forEach { b ->
                                put(JSONObject().apply {
                                    put("id", b.id)
                                    put("title", b.title)
                                    put("fileSize", b.fileSize)
                                    put("chapterCount", b.chapterCount)
                                })
                            }
                        })
                        put("fonts", JSONArray().apply {
                            repository.fonts.value.filter { !it.isBuiltIn }.forEach { f ->
                                put(JSONObject().apply {
                                    put("id", f.id)
                                    put("name", f.displayName)
                                    put("fileName", f.fileName)
                                })
                            }
                        })
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
                }
                session.method == Method.POST && uri == "/upload" -> handleUpload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not Found")
            }
        }

        private fun handleUpload(session: IHTTPSession): Response {
            return try {
                val queryName = session.parameters["name"]?.firstOrNull()?.let { raw ->
                    runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull()
                }

                val files = HashMap<String, String>()
                session.parseBody(files)

                val tempPath = files["file"]
                    ?: return jsonError(Response.Status.BAD_REQUEST, "未接收到文件")
                val tempFile = File(tempPath)

                val encodedFormName = session.parameters["encodedName"]?.firstOrNull()?.let { raw ->
                    runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull()
                }
                val rawName = encodedFormName
                    ?: queryName
                    ?: session.parameters["originalName"]?.firstOrNull()?.let {
                        BookRepository.repairMojibakeIfNeeded(it)
                    }
                    ?: session.parameters["file"]?.firstOrNull()?.let {
                        BookRepository.repairMojibakeIfNeeded(it)
                    }
                    ?: "upload.txt"

                val fileName = BookRepository.repairMojibakeIfNeeded(
                    rawName.substringAfterLast('/').substringAfterLast('\\')
                )
                val ext = fileName.substringAfterLast('.', "").lowercase()

                val log = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    when (ext) {
                        "txt" -> {
                            val book = repository.importBookFromTempFile(tempFile, fileName)
                            WifiTransferLog(
                                id = UUID.randomUUID().toString(),
                                fileName = fileName,
                                kind = TransferKind.BOOK,
                                sizeBytes = book.fileSize,
                                timestamp = System.currentTimeMillis(),
                                targetId = book.id
                            )
                        }
                        "ttf", "otf", "ttc" -> {
                            val font = repository.importFontFromTempFile(tempFile, fileName)
                            WifiTransferLog(
                                id = UUID.randomUUID().toString(),
                                fileName = fileName,
                                kind = TransferKind.FONT,
                                sizeBytes = tempFile.length(),
                                timestamp = System.currentTimeMillis(),
                                targetId = font.id
                            )
                        }
                        else -> throw IllegalArgumentException("暂仅支持 .txt 书籍或 .ttf/.otf/.ttc 字体文件")
                    }
                }

                _recentTransfers.value = (listOf(log) + _recentTransfers.value).take(20)
                _events.tryEmit(log)

                val ok = JSONObject().apply {
                    put("ok", true)
                    put("fileName", fileName)
                    put("kind", log.kind.name)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", ok.toString())
            } catch (e: Exception) {
                jsonError(Response.Status.BAD_REQUEST, e.message ?: "上传失败")
            }
        }

        private fun jsonError(status: Response.Status, msg: String): Response {
            val err = JSONObject().apply {
                put("ok", false)
                put("error", msg)
            }
            return newFixedLengthResponse(status, "application/json; charset=utf-8", err.toString())
        }
    }

    private fun buildWebPortalHtml(): String = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>悦读 · Wi-Fi 传书与字体导入</title>
          <style>
            :root {
              color-scheme: light dark;
              --glass-bg: rgba(255, 255, 255, 0.88);
              --glass-border: rgba(255, 255, 255, 0.9);
              --text-primary: #1d1d1f;
              --text-secondary: #6e6e73;
              --accent: #007aff;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --glass-bg: rgba(28, 28, 34, 0.90);
                --glass-border: rgba(255, 255, 255, 0.14);
                --text-primary: #f5f5f7;
                --text-secondary: #a1a1a6;
                --accent: #3898ff;
              }
            }
            * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Microsoft YaHei", sans-serif; }
            body {
              margin: 0;
              min-height: 100vh;
              padding: 36px 20px;
              color: var(--text-primary);
              background:
                radial-gradient(circle at 15% 20%, rgba(0, 122, 255, 0.18), transparent 42%),
                radial-gradient(circle at 85% 18%, rgba(88, 86, 214, 0.18), transparent 45%),
                #eef2f8;
            }
            @media (prefers-color-scheme: dark) {
              body {
                background:
                  radial-gradient(circle at 15% 20%, rgba(10, 132, 255, 0.22), transparent 42%),
                  radial-gradient(circle at 85% 18%, rgba(94, 92, 230, 0.22), transparent 45%),
                  #090b10;
              }
            }
            .wrap { max-width: 760px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }
            .card {
              background: var(--glass-bg);
              backdrop-filter: blur(24px);
              border: 1px solid var(--glass-border);
              border-radius: 24px;
              box-shadow: 0 18px 40px rgba(0,0,0,0.08);
              padding: 26px;
            }
            h1 { margin: 0 0 6px; font-size: 28px; letter-spacing: -0.5px; }
            .sub { color: var(--text-secondary); margin: 0; font-size: 14px; }
            .dropzone {
              margin-top: 20px;
              border: 2px dashed rgba(0, 122, 255, 0.42);
              border-radius: 20px;
              padding: 40px 20px;
              text-align: center;
              cursor: pointer;
              background: rgba(0, 122, 255, 0.04);
            }
            .dropzone.drag { background: rgba(0, 122, 255, 0.12); border-color: var(--accent); }
            .btn-row { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; margin-top: 16px; }
            .btn {
              padding: 10px 20px;
              border-radius: 999px;
              background: var(--accent);
              color: #fff;
              font-weight: 600;
              font-size: 14px;
              border: none;
              cursor: pointer;
            }
            .btn.secondary { background: rgba(0,122,255,0.14); color: var(--accent); }
            .queue { margin-top: 18px; display: flex; flex-direction: column; gap: 10px; }
            .item {
              display: flex; align-items: center; justify-content: space-between;
              padding: 12px 16px; border-radius: 14px;
              background: rgba(120, 120, 128, 0.09);
              font-size: 14px;
            }
            .pill { font-size: 12px; padding: 4px 10px; border-radius: 999px; background: rgba(0,122,255,0.14); color: var(--accent); font-weight: 600; }
            .ok { background: rgba(52,199,89,0.16); color: #248a3d; }
            .err { background: rgba(255,59,48,0.16); color: #d70015; }
            .list-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
            @media (max-width: 640px) { .list-grid { grid-template-columns: 1fr; } }
            ul { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 8px; }
            li { padding: 10px 14px; border-radius: 12px; background: rgba(120,120,128,0.08); font-size: 13px; display: flex; justify-content: space-between; gap: 8px; }
          </style>
        </head>
        <body>
          <div class="wrap">
            <div class="card">
              <h1>悦读 · Wi-Fi 传书与字体导入</h1>
              <p class="sub">将电脑上的 <b>.txt 小说</b> 或 <b>.ttf / .otf / .ttc 字体</b> 拖入下方区域，自动同步至手机。</p>
              <div id="drop" class="dropzone">
                <div style="font-size:17px;font-weight:600;">拖拽书籍或字体文件到这里</div>
                <div class="sub" style="margin-top:6px;">支持中文文件名与批量传输</div>
                <div class="btn-row">
                  <button class="btn" type="button" onclick="document.getElementById('bookPicker').click()">上传 TXT 书籍</button>
                  <button class="btn secondary" type="button" onclick="document.getElementById('fontPicker').click()">上传自定义字体</button>
                </div>
                <input id="bookPicker" type="file" multiple accept=".txt" style="display:none" />
                <input id="fontPicker" type="file" multiple accept=".ttf,.otf,.ttc" style="display:none" />
              </div>
              <div id="queue" class="queue"></div>
            </div>

            <div class="list-grid">
              <div class="card">
                <div style="font-weight:700;font-size:16px;">手机书架书籍</div>
                <ul id="books"></ul>
              </div>
              <div class="card">
                <div style="font-weight:700;font-size:16px;">已导入字体</div>
                <ul id="fonts"></ul>
              </div>
            </div>
          </div>

          <script>
            const drop = document.getElementById('drop');
            const bookPicker = document.getElementById('bookPicker');
            const fontPicker = document.getElementById('fontPicker');
            const queue = document.getElementById('queue');

            function fmtSize(b) {
              if (b < 1024) return b + ' B';
              if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
              return (b / 1048576).toFixed(2) + ' MB';
            }

            async function refreshLibrary() {
              try {
                const res = await fetch('/api/library');
                const data = await res.json();
                const booksEl = document.getElementById('books');
                const fontsEl = document.getElementById('fonts');
                booksEl.innerHTML = data.books.length
                  ? data.books.map(b => `<li><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${'$'}{b.title}</span><span class="sub">${'$'}{b.chapterCount}章</span></li>`).join('')
                  : '<li class="sub">暂无书籍</li>';
                fontsEl.innerHTML = data.fonts.length
                  ? data.fonts.map(f => `<li><span>${'$'}{f.name}</span><span class="sub">${'$'}{f.fileName}</span></li>`).join('')
                  : '<li class="sub">暂无自定义字体</li>';
              } catch (e) {}
            }

            function uploadFile(file) {
              return new Promise((resolve) => {
                const row = document.createElement('div');
                row.className = 'item';
                row.innerHTML = `<span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:65%;">${'$'}{file.name} (${'$'}{fmtSize(file.size)})</span><span class="pill">0%</span>`;
                queue.prepend(row);
                const badge = row.querySelector('.pill');

                const fd = new FormData();
                fd.append('encodedName', encodeURIComponent(file.name));
                fd.append('file', file);

                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload?name=' + encodeURIComponent(file.name), true);
                xhr.upload.onprogress = (ev) => {
                  if (ev.lengthComputable) {
                    badge.textContent = Math.round((ev.loaded / ev.total) * 100) + '%';
                  }
                };
                xhr.onload = () => {
                  try {
                    const res = JSON.parse(xhr.responseText);
                    if (xhr.status === 200 && res.ok) {
                      badge.className = 'pill ok';
                      badge.textContent = res.kind === 'FONT' ? '字体已导入' : '书籍已入库';
                      refreshLibrary();
                    } else {
                      badge.className = 'pill err';
                      badge.textContent = res.error || '失败';
                    }
                  } catch {
                    badge.className = 'pill err';
                    badge.textContent = '上传失败';
                  }
                  resolve();
                };
                xhr.onerror = () => {
                  badge.className = 'pill err';
                  badge.textContent = '连接中断';
                  resolve();
                };
                xhr.send(fd);
              });
            }

            async function handleFiles(fileList) {
              for (const f of Array.from(fileList)) {
                await uploadFile(f);
              }
            }

            drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('drag'); });
            drop.addEventListener('dragleave', () => drop.classList.remove('drag'));
            drop.addEventListener('drop', (e) => {
              e.preventDefault();
              drop.classList.remove('drag');
              handleFiles(e.dataTransfer.files);
            });
            bookPicker.addEventListener('change', (e) => { handleFiles(e.target.files); bookPicker.value = ''; });
            fontPicker.addEventListener('change', (e) => { handleFiles(e.target.files); fontPicker.value = ''; });
            refreshLibrary();
          </script>
        </body>
        </html>
    """.trimIndent()
}
