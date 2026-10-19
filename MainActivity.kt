package com.priv.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONTokener
import android.app.Activity
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Random
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

// Proxy: Orbot (Tor) ka SOCKS port. Agar ye kaam na kare to "http://127.0.0.1:8118" try karo.
// Khali "" rakhoge to proxy nahi lagega (tab phone ka VPN app use karo).
private const val PROXY = ""

private const val UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Mobile Safari/537.36"

private val TRACKERS = setOf(
    "google-analytics.com", "googletagmanager.com", "googletagservices.com",
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adservice.google.com", "facebook.net", "connect.facebook.com", "pixel.facebook.com",
    "hotjar.com", "scorecardresearch.com", "adnxs.com", "criteo.com", "criteo.net",
    "taboola.com", "outbrain.com", "mixpanel.com", "segment.io", "segment.com",
    "amplitude.com", "quantserve.com", "clarity.ms", "fullstory.com", "mouseflow.com",
    "crazyegg.com", "optimizely.com", "newrelic.com", "nr-data.net", "branch.io",
    "adjust.com", "appsflyer.com", "moatads.com", "rubiconproject.com", "pubmatic.com",
    "openx.net", "casalemedia.com", "advertising.com", "bluekai.com", "demdex.net",
    "everesttech.net", "krxd.net", "rlcdn.com", "tapad.com", "bat.bing.com",
    "ads.linkedin.com", "analytics.tiktok.com", "ads.twitter.com", "static.ads-twitter.com",
    "analytics.twitter.com", "snap.licdn.com", "ads.yahoo.com", "analytics.yahoo.com",
    "sentry.io", "bugsnag.com", "heap.io", "heapanalytics.com", "pendo.io",
    "chartbeat.com", "parsely.com", "adsrvr.org", "3lift.com", "media.net"
)

private val FP_JS = """
(function () {
  try {
    var seed = SEED_PLACEHOLDER;
    var hit = function (i) { return ((Math.imul(i + 1, 2654435761) ^ seed) >>> 0) % 100 < 6; };
    var perturb = function (d) { for (var i = 0; i < d.length; i += 4) if (hit(i)) d[i] ^= 1; };
    var def = function (o, k, v) {
      try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {}
    };

    // Canvas: copy pe noise, original safe
    var oGet = CanvasRenderingContext2D.prototype.getImageData;
    var oURL = HTMLCanvasElement.prototype.toDataURL;
    var oBlob = HTMLCanvasElement.prototype.toBlob;
    var noisy = function (cv) {
      if (!cv.width || !cv.height) return cv;
      var t = document.createElement('canvas');
      t.width = cv.width; t.height = cv.height;
      var c = t.getContext('2d');
      c.drawImage(cv, 0, 0);
      var d = oGet.call(c, 0, 0, t.width, t.height);
      perturb(d.data); c.putImageData(d, 0, 0);
      return t;
    };
    CanvasRenderingContext2D.prototype.getImageData = function () {
      var d = oGet.apply(this, arguments); perturb(d.data); return d;
    };
    HTMLCanvasElement.prototype.toDataURL = function () { return oURL.apply(noisy(this), arguments); };
    HTMLCanvasElement.prototype.toBlob = function () { return oBlob.apply(noisy(this), arguments); };

    // WebGL GPU info chhupao
    [window.WebGLRenderingContext, window.WebGL2RenderingContext].forEach(function (C) {
      if (!C) return;
      var o = C.prototype.getParameter;
      C.prototype.getParameter = function (p) {
        if (p === 37445) return 'Google Inc.';
        if (p === 37446) return 'ANGLE (Generic Renderer)';
        return o.call(this, p);
      };
    });

    // Navigator standardize
    def(Navigator.prototype, 'hardwareConcurrency', 4);
    def(Navigator.prototype, 'deviceMemory', 4);
    def(Navigator.prototype, 'languages', Object.freeze(['en-US', 'en']));
    def(Navigator.prototype, 'language', 'en-US');
    def(Navigator.prototype, 'doNotTrack', '1');
    def(Navigator.prototype, 'getBattery', undefined);
    def(Navigator.prototype, 'connection', undefined);
  } catch (e) {}
})();
"""

private val HOME = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{color-scheme:light dark}
body{font-family:system-ui,sans-serif;margin:0;padding:28px 18px;text-align:center}
h1{font-size:30px;margin:34px 0 6px}p{opacity:.7;margin:0 0 24px}
input{width:100%;padding:14px;font-size:17px;border-radius:12px;border:1px solid #8886;box-sizing:border-box}
button{margin:14px 5px 0;padding:12px 22px;font-size:16px;border:0;border-radius:12px;background:#5b3df5;color:#fff}
</style></head><body>
<h1>PrivBrowser</h1><p>Private &middot; Tracker-free &middot; AI search</p>
<form action="https://home.local/s" method="get">
<input name="q" placeholder="Search ya sawal likho" autocomplete="off">
<button type="submit" name="m" value="s">Search</button>
<button type="submit" name="m" value="ai">Ask AI</button>
</form></body></html>
"""

private val SEARCH_JS = """
JSON.stringify(Array.from(document.querySelectorAll('.result')).slice(0,10).map(function(r){
  var a=r.querySelector('a.result__a');var s=r.querySelector('.result__snippet');
  return a?{t:a.innerText,u:a.href,s:s?s.innerText:''}:null}).filter(Boolean))
"""

private val PAGE_JS = """
(function(){var n=document.querySelector('article')||document.querySelector('main')||document.body;
return (n.innerText||'').slice(0,30000)})()
"""

private val STOP = ("the a an is are was of to in on and or for with what who how why " +
    "kya hai hain ka ki ke ko se me mein kaun kaise kyu kab").split(" ").toSet()

private data class Item(val t: String, val u: String, val s: String)

private fun toks(s: String): List<String> =
    Regex("[\\p{L}\\p{N}]+").findAll(s.lowercase()).map { it.value }.filter { it.length > 2 && it !in STOP }.toList()

private fun sentences(t: String, lo: Int = 40): List<String> =
    t.replace(Regex("\\s+"), " ").split(Regex("(?<=[.!?\u0964])\\s+")).map { it.trim() }.filter { it.length in lo..320 }

private class Cand(val s: String, val di: Int, val pos: Int, val score: Double, val t: Set<String>)

private fun summarize(q: String, docs: List<List<String>>, k: Int = 5): List<Pair<String, Int>> {
    val qs = toks(q).toSet()
    val cands = ArrayList<Cand>()
    docs.forEachIndexed { di, ss ->
        ss.forEachIndexed { pos, s ->
            val t = toks(s).toSet()
            val ov = t.count { it in qs }
            if (qs.isNotEmpty() && ov == 0) return@forEachIndexed
            cands.add(Cand(s, di, pos, 3.0 * ov / maxOf(qs.size, 1) + 1.0 / (1 + pos / 8.0) - 0.15 * di, t))
        }
    }
    cands.sortByDescending { it.score }
    val chosen = ArrayList<Cand>()
    for (c in cands) {
        if (chosen.any { it.t.intersect(c.t).size.toDouble() / maxOf(it.t.union(c.t).size, 1) > 0.6 }) continue
        chosen.add(c)
        if (chosen.size >= k) break
    }
    chosen.sortWith(compareBy({ it.di }, { it.pos }))
    return chosen.map { it.s to it.di }
}

private fun isTracker(host: String): Boolean {
    val parts = host.lowercase().split(".")
    for (i in parts.indices) {
        if (parts.drop(i).joinToString(".") in TRACKERS) return true
    }
    return false
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var status: TextView
    private var scriptHandler: ScriptHandler? = null
    private var ready = false
    private val blocked = AtomicInteger(0)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        urlBar = EditText(this).apply {
            hint = "Search ya URL"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        bar.addView(urlBar, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply {
            text = "Go"
            setOnClickListener { go(urlBar.text.toString()) }
        })
        bar.addView(Button(this).apply {
            text = "NEW ID"
            setOnClickListener { newIdentity() }
        })
        bar.addView(Button(this).apply {
            text = "AI"
            setOnClickListener {
                val t = urlBar.text.toString().trim()
                if (t.isNotEmpty()) ask(t)
            }
        })

        status = TextView(this).apply {
            setPadding(dp(8), dp(2), dp(8), dp(2))
            textSize = 12f
        }
        web = WebView(this)

        root.addView(bar)
        root.addView(status)
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        setupWebView()
        startProxy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setGeolocationEnabled(false)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.userAgentString = UA
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.mediaPlaybackRequiresUserGesture = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        installScript()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                if (isTracker(request.url.host ?: "")) {
                    blocked.incrementAndGet()
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "home.local" && u.path == "/s") {
                    val qq = u.getQueryParameter("q") ?: ""
                    if (u.getQueryParameter("m") == "ai") ask(qq) else go(qq)
                    return true
                }
                if (u.scheme == "http") {  // HTTPS-only
                    view.loadUrl(u.buildUpon().scheme("https").build().toString())
                    return true
                }
                return u.scheme != "https"  // intent:, file: etc. block
            }

            override fun onPageFinished(view: WebView, url: String) {
                urlBar.setText(if (url.contains("home.local")) "" else url)
                updateStatus()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) { callback?.invoke(origin, false, false) }

            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean = false  // popups band
        }
    }

    private fun installScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        scriptHandler?.remove()
        val seed = Random().nextInt(Int.MAX_VALUE)
        scriptHandler = WebViewCompat.addDocumentStartJavaScript(
            web, FP_JS.replace("SEED_PLACEHOLDER", seed.toString()), setOf("*")
        )
    }

    private fun startProxy() {
        if (PROXY.isBlank()) {
            ready = true
            updateStatus()
            showHome()
            return
        }
        // Fail-closed: proxy set na ho paye to browsing hi band (real IP leak nahi hoga)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            status.text = "Is phone ke WebView me proxy support nahi. Safety ke liye browsing band."
            return
        }
        try {
            val cfg = ProxyConfig.Builder().addProxyRule(PROXY).build()
            ProxyController.getInstance().setProxyOverride(
                cfg, Executor { r -> runOnUiThread(r) },
                Runnable {
                    ready = true
                    updateStatus()
                    showHome()
                }
            )
        } catch (e: Exception) {
            status.text = "Proxy error: ${e.message}"
        }
    }

    private fun updateStatus() {
        val p = if (PROXY.isBlank()) "OFF (VPN use karo)" else "ON"
        status.text = "Trackers blocked: ${blocked.get()}  |  Proxy: $p"
    }

    private fun go(input: String) {
        if (!ready) {
            status.text = "Proxy ready nahi. Orbot chalu karo aur app dobara kholo."
            return
        }
        val t = input.trim()
        if (t.isEmpty()) return
        val url = if (t.contains(".") && !t.contains(" ")) {
            if (t.contains("://")) t else "https://$t"
        } else {
            "https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8")
        }
        web.loadUrl(url.replaceFirst("http://", "https://"), mapOf("DNT" to "1", "Sec-GPC" to "1"))
    }

    private fun newIdentity() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
        installScript()
        showHome()
        status.text = "Naya identity: cookies saaf, fingerprint badla"
    }

    private fun showHome() = showHtml(HOME)

    private fun showHtml(h: String) {
        web.loadDataWithBaseURL("https://home.local/", h, "text/html", "utf-8", null)
    }

    private fun blockResp(host: String?): WebResourceResponse? {
        if (host != null && isTracker(host)) {
            blocked.incrementAndGet()
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    // Hidden WebView: page kholo, JS chalao, result do (wahi proxy/VPN jo browser use karta hai)
    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchJs(url: String, js: String, cb: (String?) -> Unit) {
        val w = WebView(this)
        val st = w.settings
        st.javaScriptEnabled = true
        st.userAgentString = UA
        st.allowFileAccess = false
        st.allowContentAccess = false
        st.setGeolocationEnabled(false)
        st.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        st.cacheMode = WebSettings.LOAD_NO_CACHE
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        var done = false
        val finish: (String?) -> Unit = { r ->
            if (!done) {
                done = true
                w.stopLoading()
                w.destroy()
                cb(r)
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = blockResp(request.url.host)

            override fun onPageFinished(view: WebView, url: String) {
                view.postDelayed({
                    if (!done) view.evaluateJavascript(js) { res -> finish(res) }
                }, 900L)
            }
        }
        w.postDelayed({ finish(null) }, 18000L)
        w.loadUrl(url)
    }

    private fun decode(raw: String?): String? = try {
        if (raw == null || raw == "null") null else JSONTokener(raw).nextValue() as? String
    } catch (e: Exception) {
        null
    }

    private fun parseResults(raw: String?): List<Item> {
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(decode(raw) ?: return out)
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val h = o.getString("u")
                if (h.contains("duckduckgo.com/y.js")) continue
                val u = (Uri.parse(h).getQueryParameter("uddg") ?: h).replaceFirst("http://", "https://")
                val host = Uri.parse(u).host ?: continue
                if (!u.startsWith("https://") || host.contains("duckduckgo.com") || !seen.add(host)) continue
                out.add(Item(o.getString("t"), u, o.getString("s")))
                if (out.size == 4) break
            }
        } catch (e: Exception) {
        }
        return out
    }

    // AI: search -> top pages padho -> free local summarizer
    private fun ask(q: String) {
        if (!ready) {
            status.text = "Proxy ready nahi. Orbot/WARP chalu karo."
            return
        }
        showHtml("<p style='font-family:sans-serif;padding:14px'>Search karke pages padh raha hu...</p>")
        fetchJs("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"), SEARCH_JS) { raw ->
            val items = parseResults(raw)
            if (items.isEmpty()) {
                showHtml("<p style='font-family:sans-serif;padding:14px'>Koi result nahi mila. Thodi der baad try karo.</p>")
                return@fetchJs
            }
            val docs = MutableList(items.size) { i -> sentences(items[i].s, 30) }
            var left = items.size
            items.forEachIndexed { i, item ->
                fetchJs(item.u, PAGE_JS) { txt ->
                    val t = decode(txt)
                    if (t != null) docs[i] = docs[i] + sentences(t)
                    left--
                    if (left == 0) renderAnswer(q, items, docs)
                }
            }
        }
    }

    private fun renderAnswer(q: String, items: List<Item>, docs: List<List<String>>) {
        val pick = summarize(q, docs)
        val head = "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>:root{color-scheme:light dark}body{font-family:sans-serif;padding:14px;line-height:1.55}a{color:#7b61ff}</style>"
        if (pick.isEmpty()) {
            showHtml(head + "<p>Kaam ka text nahi mila. Sawal alag tarah se pucho.</p>")
            return
        }
        val body = pick.joinToString("") {
            "<li>" + TextUtils.htmlEncode(it.first) + " <sup>[" + (it.second + 1) + "]</sup></li>"
        }
        val src = items.joinToString("") {
            "<li><a href='" + TextUtils.htmlEncode(it.u) + "'>" + TextUtils.htmlEncode(it.t) + "</a></li>"
        }
        showHtml(head + "<h3>" + TextUtils.htmlEncode(q) + "</h3><ul>" + body + "</ul><b>Sources</b><ol>" + src + "</ol>")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
