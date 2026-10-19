package com.priv.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.FrameLayout
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

private val BASE_CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:system-ui,sans-serif;background:#0b1020;color:#e8ecf7;line-height:1.5}
a{color:#5aa2ff;text-decoration:none}
.sb{display:flex;align-items:center;gap:6px;background:#0f1a36;border:2px solid #2b7bff;border-radius:30px;padding:4px 5px 4px 16px;box-shadow:0 0 18px #2b7bff66}
.sb input{flex:1;min-width:0;background:none;border:0;outline:0;color:#fff;font-size:17px;padding:11px 2px}
.sb input::placeholder{color:#9aa7c7}
.sb button{border:0;border-radius:22px;background:#2b7bff;color:#fff;padding:10px 14px;font-size:15px}
.sb button.g{background:#2a3558}
.mini{width:100%;padding:11px 14px;border-radius:14px;border:1px solid #ffffff44;background:#0f1a36cc;color:#fff;font-size:15px;outline:0}
"""

private val WX_JS = """
var g=function(i){return document.getElementById(i)};
var esc=function(s){return String(s).replace(/[&<>"']/g,function(c){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})};
var IC=function(c){return c===0?"☀️":c<=2?"⛅":c===3?"☁️":c<=48?"🌫️":c<=67?"🌧️":c<=77?"❄️":c<=82?"🌦️":"⛈️"};
async function getWx(){
  var city=localStorage.getItem("city")||"Patna";
  var c=null;try{c=JSON.parse(localStorage.getItem("wxc")||"null")}catch(e){}
  if(c&&c.city===city&&Date.now()-c.t<1800000)return c;
  var gj=await (await fetch("https://geocoding-api.open-meteo.com/v1/search?count=1&name="+encodeURIComponent(city))).json();
  var p=gj.results&&gj.results[0];if(!p)return null;
  var w=await (await fetch("https://api.open-meteo.com/v1/forecast?latitude="+p.latitude+"&longitude="+p.longitude+"&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5")).json();
  c={city:city,place:p.name+(p.admin1?", "+p.admin1:""),t:Date.now(),w:w};
  localStorage.setItem("wxc",JSON.stringify(c));return c;
}
"""

private val HOME = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>__CSS__
body{min-height:100vh;background:linear-gradient(#070c24 0%,#241657 40%,#8a3f78 68%,#e8825f 100%) fixed}
.stars{position:absolute;left:0;right:0;top:0;height:60vh;pointer-events:none;background-image:radial-gradient(1.5px 1.5px at 12% 18%,#fff,transparent),radial-gradient(1px 1px at 33% 8%,#fff,transparent),radial-gradient(1.5px 1.5px at 58% 22%,#fff,transparent),radial-gradient(1px 1px at 81% 12%,#fff,transparent),radial-gradient(1px 1px at 90% 30%,#fff,transparent),radial-gradient(1px 1px at 45% 35%,#fff,transparent),radial-gradient(1px 1px at 22% 40%,#fff,transparent)}
.mt{position:fixed;left:0;right:0;bottom:0;width:100%;height:34vh;pointer-events:none}
.wrap{position:relative;padding:16px 16px 60px;text-align:center}
.wx{text-align:right;font-size:13px;min-height:64px;cursor:pointer;line-height:1.35}
.wx .t{font-size:21px}
.logo{width:76px;height:76px}
h1{font-size:34px;margin:6px 0 2px;font-weight:700}
.tag{opacity:.9;margin-bottom:22px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px 6px;margin-top:34px}
.ic{display:block;color:#fff;font-size:12px}
.ic i{display:flex;width:54px;height:54px;margin:0 auto 6px;border-radius:15px;align-items:center;justify-content:center;font-style:normal;font-weight:700;font-size:24px;color:#fff;box-shadow:0 4px 12px #0007}
</style></head><body>
<div class="stars"></div>
<svg class="mt" viewBox="0 0 400 140" preserveAspectRatio="none"><polygon points="0,140 0,90 50,50 90,85 140,30 200,95 250,45 310,90 360,55 400,85 400,140" fill="#150f33" opacity=".92"/><polygon points="0,140 0,112 70,82 130,116 200,78 270,118 340,88 400,112 400,140" fill="#0a0818"/></svg>
<div class="wrap">
<div class="wx" id="wx"></div>
<svg class="logo" viewBox="0 0 100 100"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#4fc3ff"/><stop offset="1" stop-color="#1b4fff"/></linearGradient></defs><circle cx="50" cy="50" r="44" fill="url(#lg)"/><path d="M28 55c10-22 38-26 46-6-12-8-30-4-38 12 8 10 24 10 34-2-2 20-34 30-42-4z" fill="#fff" opacity=".92"/></svg>
<h1>SkyBrowser</h1>
<div class="tag">तेज &bull; सुरक्षित &bull; आपका</div>
<form class="sb" action="https://home.local/s" method="get">
<input name="q" placeholder="कुछ भी खोजें..." autocomplete="off">
<button name="m" value="s">खोजें</button><button name="m" value="ai" class="g">AI</button></form>
<div class="grid" id="grid"></div>
<div id="add" style="margin-top:16px"></div>
</div>
<script>
__WXJS__
var SC=[["YouTube","https://www.youtube.com","#ff0000","▶"],["Google","https://www.google.com","#ffffff","G","#4285f4"],["WhatsApp","https://web.whatsapp.com","#25d366","✆"],["Instagram","https://www.instagram.com","linear-gradient(45deg,#feda75,#d62976,#4f5bd5)","◎"],["Facebook","https://www.facebook.com","#1877f2","f"],["X","https://x.com","#000000","X"],["Amazon","https://www.amazon.in","#ffffff","a","#111111"],["Flipkart","https://www.flipkart.com","#2874f0","F"]];
function draw(){
  var all=SC.slice();
  try{JSON.parse(localStorage.getItem("sc")||"[]").forEach(function(x){all.push([x.n,x.u,"#2b3a6b",x.n.charAt(0).toUpperCase()])})}catch(e){}
  g("grid").innerHTML=all.map(function(s){return '<a class="ic" href="'+esc(s[1])+'"><i style="background:'+s[2]+';'+(s[4]?"color:"+s[4]:"")+'">'+esc(s[3])+'</i>'+esc(s[0])+'</a>'}).join("")+'<a class="ic" href="#" onclick="addSc();return false"><i style="background:#ffffff2a">+</i>Add</a>';
}
function addSc(){
  var b=g("add");b.innerHTML='<input id="au" class="mini" placeholder="site.com likho, Enter dabao" autocomplete="off">';
  var i=g("au");i.focus();
  i.onkeydown=function(e){if(e.key!=="Enter")return;e.preventDefault();var v=i.value.trim();if(!v)return;
    if(!/^https?:/i.test(v))v="https://"+v;var h;try{h=new URL(v).hostname.replace(/^www\./,"")}catch(x){return}
    var a=JSON.parse(localStorage.getItem("sc")||"[]");a.push({n:h.split(".")[0],u:v});localStorage.setItem("sc",JSON.stringify(a));b.innerHTML="";draw()};
}
function loadWx(){
  var d=new Date();
  var dh="<div>"+d.toLocaleDateString("hi-IN",{day:"numeric",month:"long",year:"numeric"})+"</div><div>"+d.toLocaleDateString("hi-IN",{weekday:"long"})+"</div>";
  g("wx").innerHTML=dh;
  getWx().then(function(c){if(!c)return;var w=c.w.current;
    g("wx").innerHTML='<div class="t">'+IC(w.weather_code)+" "+Math.round(w.temperature_2m)+"°C</div><div>"+esc(c.place)+"</div>"+dh}).catch(function(){});
}
g("wx").onclick=function(e){
  if(e.target.id==="ci")return;
  g("wx").innerHTML='<input id="ci" class="mini" placeholder="शहर का नाम, Enter dabao" autocomplete="off">';
  var i=g("ci");i.focus();
  i.onkeydown=function(ev){if(ev.key!=="Enter")return;ev.preventDefault();var v=i.value.trim();if(!v)return;
    localStorage.setItem("city",v);localStorage.removeItem("wxc");loadWx()};
};
draw();loadWx();
</script></body></html>
"""

private val RESULTS_TPL = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>__CSS__
.top{padding:12px 12px 0;position:sticky;top:0;background:#0b1020;z-index:2}
.tabs{display:flex;gap:20px;padding:10px 4px 0;border-bottom:1px solid #ffffff22;overflow-x:auto}
.tabs a{padding:8px 2px;color:#aab4d0;white-space:nowrap}
.tabs a.on{color:#5aa2ff;border-bottom:3px solid #5aa2ff}
.list{padding:4px 14px 50px}
.r{margin:20px 0}
.src{display:flex;gap:10px;align-items:center}
.fv{width:28px;height:28px;border-radius:50%;background:#1d2b55;display:flex;align-items:center;justify-content:center;font-size:13px;color:#9fc4ff;flex:none}
.h{font-size:14px}.u{font-size:12px;color:#8793b5;word-break:break-all}
.t{display:block;font-size:19px;margin:5px 0 3px;color:#6fb0ff}
.s{color:#c3cbe3;font-size:15px}
.card{margin:14px;padding:14px;border-radius:16px;background:#101a33;border:1px solid #ffffff1a}
.big{font-size:34px;margin:6px 0}
.days{display:flex;justify-content:space-between;text-align:center;margin-top:12px;font-size:13px}
</style></head><body>
<div class="top"><form class="sb" action="https://home.local/s" method="get">
<input name="q" value="__Q__" autocomplete="off">
<button name="m" value="s">खोजें</button><button name="m" value="ai" class="g">AI</button></form>
<div class="tabs"><a class="__TA__">All</a><a class="__TN__" href="https://home.local/s?m=s&t=news&q=__QU__">News</a><a class="__TV__" href="https://home.local/s?m=s&t=videos&q=__QU__">Videos</a><a href="https://www.openstreetmap.org/search?query=__QU__">Maps</a></div></div>
<div id="card"></div>
<div class="list">__LIST__</div>
<script>
var Q=__QJS__;
__WXJS__
if(/weather|mausam|temperature|मौसम|तापमान/i.test(Q)){
  getWx().then(function(c){if(!c)return;var w=c.w.current,d=c.w.daily,days="";
    for(var i=0;i<d.time.length;i++){var dt=new Date(d.time[i]+"T00:00:00");
      days+="<div><div>"+dt.toLocaleDateString("hi-IN",{weekday:"short"})+"</div><div>"+IC(d.weather_code[i])+"</div><div>"+Math.round(d.temperature_2m_max[i])+"° "+Math.round(d.temperature_2m_min[i])+"°</div></div>"}
    g("card").innerHTML='<div class="card"><h3 style="margin:0">'+esc(c.city)+' का मौसम</h3><small>'+esc(c.place)+'</small><div class="big">'+IC(w.weather_code)+" "+Math.round(w.temperature_2m)+'°C</div><div>आर्द्रता: '+w.relative_humidity_2m+'% | हवा: '+Math.round(w.wind_speed_10m)+' किमी/घंटा</div><div class="days">'+days+'</div></div>'}).catch(function(){});
}
</script></body></html>
"""

private fun msg(t: String) = "<meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
    BASE_CSS + "</style><p style='padding:18px'>" + t + "</p>"

private val SEARCH_JS = """
JSON.stringify(Array.from(document.querySelectorAll('.result')).slice(0,10).map(function(r){
  var a=r.querySelector('a.result__a');var s=r.querySelector('.result__snippet');
  return a?{t:a.innerText,u:a.href,s:s?s.innerText:''}:null}).filter(Boolean))
"""

private val MOJEEK_JS = """
JSON.stringify(Array.from(document.querySelectorAll('ul.results-standard li')).slice(0,10).map(function(r){
  var a=r.querySelector('a.title')||r.querySelector('h2 a');var s=r.querySelector('p.s');
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var hiddenHost: FrameLayout
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
        hiddenHost = FrameLayout(this)
        root.addView(hiddenHost, LinearLayout.LayoutParams(1, 1))
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
                    val tb = u.getQueryParameter("t") ?: ""
                    if (u.getQueryParameter("m") == "ai") ask(qq) else if (tb.isNotEmpty()) search(qq, tb) else go(qq)
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
        if (!(t.contains(".") && !t.contains(" "))) {
            search(t)
            return
        }
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

    private fun showHome() = showHtml(HOME.replace("__CSS__", BASE_CSS).replace("__WXJS__", WX_JS))

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
        hiddenHost.addView(w, FrameLayout.LayoutParams(1, 1))
        var done = false
        val finish: (String?) -> Unit = { r ->
            if (!done) {
                done = true
                w.stopLoading()
                hiddenHost.removeView(w)
                w.destroy()
                cb(r)
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = blockResp(request.url.host)

            override fun onPageFinished(view: WebView, url: String) {
                handler.postDelayed({
                    if (!done) view.evaluateJavascript(js) { res -> finish(res) }
                }, 900L)
            }
        }
        handler.postDelayed({ finish(null) }, 15000L)
        w.loadUrl(url)
    }

    private fun decode(raw: String?): String? = try {
        if (raw == null || raw == "null") null else JSONTokener(raw).nextValue() as? String
    } catch (e: Exception) {
        null
    }

    private fun parseResults(raw: String?, max: Int = 4): List<Item> {
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
                if (!u.startsWith("https://") || host.contains("duckduckgo.com") || (max <= 4 && !seen.add(host))) continue
                out.add(Item(o.getString("t"), u, o.getString("s")))
                if (out.size >= max) break
            }
        } catch (e: Exception) {
        }
        return out
    }

    // Apna results page (SkyBrowser style): search engine ka page kabhi nahi dikhta
    private fun search(q: String, tab: String = "") {
        if (!ready) {
            status.text = "Proxy ready nahi. Orbot/WARP chalu karo."
            return
        }
        showHtml(msg("Search ho raha hai..."))
        val qq = when (tab) {
            "news" -> q + " news"
            "videos" -> q + " site:youtube.com"
            else -> q
        }
        val enc = URLEncoder.encode(qq, "UTF-8")
        fetchJs("https://html.duckduckgo.com/html/?q=" + enc, SEARCH_JS) { raw ->
            val a = parseResults(raw, 10)
            if (a.isNotEmpty()) {
                renderResults(q, a, tab)
                return@fetchJs
            }
            fetchJs("https://www.mojeek.com/search?q=" + enc, MOJEEK_JS) { raw2 ->
                val b = parseResults(raw2, 10)
                if (b.isEmpty()) {
                    val d1 = if (decode(raw) == null) "timeout ya block" else "0 result"
                    val d2 = if (decode(raw2) == null) "timeout ya block" else "0 result"
                    showHtml(msg("Kuch nahi mila. (DuckDuckGo: " + d1 + ", Mojeek: " + d2 + "). VPN (WARP) ON hai? Dobara try karo."))
                } else {
                    renderResults(q, b, tab)
                }
            }
        }
    }

    private fun renderResults(q: String, items: List<Item>, tab: String) {
        val list = items.joinToString("") {
            val host = (Uri.parse(it.u).host ?: "").removePrefix("www.")
            val disp = it.u.removePrefix("https://").trimEnd('/').replace("/", " \u203A ").take(70)
            "<div class='r'><div class='src'><span class='fv'>" + TextUtils.htmlEncode(host.take(1).uppercase()) +
                "</span><div><div class='h'>" + TextUtils.htmlEncode(host) + "</div><div class='u'>" +
                TextUtils.htmlEncode(disp) + "</div></div></div><a class='t' href='" + TextUtils.htmlEncode(it.u) + "'>" +
                TextUtils.htmlEncode(it.t) + "</a><div class='s'>" + TextUtils.htmlEncode(it.s) + "</div></div>"
        }
        val qu = TextUtils.htmlEncode(URLEncoder.encode(q, "UTF-8"))
        showHtml(
            RESULTS_TPL
                .replace("__CSS__", BASE_CSS).replace("__WXJS__", WX_JS)
                .replace("__QJS__", JSONObject.quote(q))
                .replace("__QU__", qu).replace("__Q__", TextUtils.htmlEncode(q))
                .replace("__TA__", if (tab == "") "on" else "")
                .replace("__TN__", if (tab == "news") "on" else "")
                .replace("__TV__", if (tab == "videos") "on" else "")
                .replace("__LIST__", list)
        )
    }

    // AI: search -> top pages padho -> free local summarizer
    private fun ask(q: String) {
        if (!ready) {
            status.text = "Proxy ready nahi. Orbot/WARP chalu karo."
            return
        }
        showHtml(msg("Search karke pages padh raha hu..."))
        fetchJs("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"), SEARCH_JS) { raw ->
            val items = parseResults(raw)
            if (items.isEmpty()) {
                showHtml(msg("Koi result nahi mila. Thodi der baad try karo."))
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
        val head = "<meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
            BASE_CSS + "</style><div style='padding:14px'>"
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
