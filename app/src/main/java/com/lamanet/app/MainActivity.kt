package com.lamanet.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var serverSocket: ServerSocket? = null
    private val serverExecutor = Executors.newSingleThreadExecutor()
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        setContentView(webView)

        startLocalServer()
    }

    private fun startLocalServer() {
        serverExecutor.execute {
            try {
                val localServer = ServerSocket(
                    8080,
                    16,
                    InetAddress.getByName("127.0.0.1")
                )
                serverSocket = localServer

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        webView.loadUrl("http://127.0.0.1:8080/")
                    }
                }

                while (!localServer.isClosed) {
                    val client = localServer.accept()
                    serveClient(client)
                }
            } catch (_: Exception) {
                // The activity may be closing, or the local port may be unavailable.
            }
        }
    }

    private fun serveClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val requestLine = input.readLine() ?: return

                // Consume HTTP headers until the empty separator line.
                while (true) {
                    val headerLine = input.readLine() ?: break
                    if (headerLine.isEmpty()) break
                }

                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                when (path) {
                    "/health" -> writeResponse(
                        socket,
                        status = "200 OK",
                        contentType = "application/json; charset=utf-8",
                        body = """{"status":"ok","service":"LamaNet","scope":"loopback"}"""
                    )
                    "/" -> writeResponse(
                        socket,
                        status = "200 OK",
                        contentType = "text/html; charset=utf-8",
                        body = PAGE
                    )
                    else -> writeResponse(
                        socket,
                        status = "404 Not Found",
                        contentType = "text/plain; charset=utf-8",
                        body = "Not found"
                    )
                }
            } catch (_: Exception) {
                // Ignore a client disconnect or malformed local request.
            }
        }
    }

    private fun writeResponse(
        socket: Socket,
        status: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = (
            "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n\r\n"
        ).toByteArray(StandardCharsets.US_ASCII)

        val output = socket.getOutputStream()
        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }

    override fun onDestroy() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverExecutor.shutdownNow()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private companion object {
        val PAGE = """<!doctype html>
<html lang="az">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0b1020">
<title>LamaNet</title>
<style>
:root{color-scheme:dark;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#0b1020;color:#eef2ff}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;padding:26px 20px;display:grid;place-items:center;background:radial-gradient(circle at 80% 0%,#1d3769 0,transparent 40%),#0b1020}
main{width:min(100%,520px)}
.brand{font-size:12px;letter-spacing:.24em;font-weight:900;color:#7dd3fc}
h1{font-size:38px;line-height:1.08;margin:14px 0}
.sub{color:#aab6d3;line-height:1.65}
.card{margin-top:26px;padding:22px;border:1px solid #293858;border-radius:22px;background:#121c33;box-shadow:0 18px 55px #0005}
.status{display:flex;align-items:center;gap:12px;font-weight:750}
.dot{width:11px;height:11px;border-radius:50%;background:#34d399;box-shadow:0 0 0 5px #064e3b}
small{display:block;color:#9eaccb;margin-top:9px;line-height:1.6;overflow-wrap:anywhere}
.line{height:1px;background:#293858;margin:21px 0}
button{width:100%;margin-top:18px;border:0;border-radius:13px;padding:14px 16px;background:#38bdf8;color:#082032;font-size:15px;font-weight:850}
button:active{transform:scale(.99)}
.note{font-size:12px;color:#8594b6;line-height:1.65;margin-top:20px}
.pill{display:inline-block;margin-top:14px;padding:6px 10px;border:1px solid #275b63;border-radius:99px;color:#6ee7b7;font-size:11px;font-weight:800;letter-spacing:.08em}
</style>
</head>
<body>
<main>
<div class="brand">LAMANET / LOCAL NODE</div>
<h1>Öz lokal bağlantına xoş gəldin.</h1>
<p class="sub">LamaNet-in Android telefonda işləyən ilk texniki prototipi.</p>
<section class="card">
<div class="status"><span class="dot"></span><span>Lokal server başladılıb</span></div>
<div class="pill">SERVER AKTİVDİR</div>
<small>Ünvan: 127.0.0.1:8080</small>
<div class="line"></div>
<strong>Server cavab verir?</strong>
<small>Bu düymə serverə ayrıca HTTP sorğusu göndərir və cavabı yoxlayır.</small>
<button onclick="checkServer()">Lokal bağlantını yoxla</button>
<small id="result" aria-live="polite">Yoxlama hələ aparılmayıb.</small>
</section>
<p class="note">Vacib: bu versiya yalnız eyni telefonda işləyir. İctimai internet yaratmır və başqa cihazları şəbəkəyə qoşmur. Server yalnız 127.0.0.1 ünvanında dinləyir; SIM kart və mobil internet tələb etmir.</p>
</main>
<script>
async function checkServer(){
  const result=document.getElementById('result');
  result.textContent='Server yoxlanılır...';
  try{
    const response=await fetch('/health',{cache:'no-store'});
    if(!response.ok) throw new Error('HTTP '+response.status);
    const data=await response.json();
    result.textContent='Uğurlu! '+data.service+' serveri cavab verdi.';
  }catch(error){
    result.textContent='Bağlantı alınmadı: '+error.message;
  }
}
</script>
</body>
</html>"""
    }
}
