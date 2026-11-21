package com.midasbuy.egypt

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offlinePanel: LinearLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var firstPageLoaded = false
    private val homeUrl = "https://www.midasbuy.com/midasbuy/eg?lang=ar"

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = if (result.resultCode == RESULT_OK && result.data != null)
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) else null
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 17, 25)
        window.navigationBarColor = Color.rgb(15, 17, 25)
        buildInterface()
        showSplash()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString MidasbuyEgyptApp/2.0"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return true
                return if (scheme == "http" || scheme == "https") false else openExternal(uri)
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                offlinePanel.visibility = View.GONE
            }
            override fun onPageFinished(view: WebView, url: String) {
                firstPageLoaded = true
                if (progress.progress >= 100) progress.visibility = View.GONE
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !isOnline()) showOffline()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
            override fun onShowFileChooser(
                webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    filePicker.launch(fileChooserParams.createIntent()); true
                } catch (_: Exception) {
                    fileCallback = null; false
                }
            }
        }
        webView.setDownloadListener(DownloadListener { url, userAgent, disposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, disposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .setTitle(name)
                    .setDescription("Midasbuy Egypt")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                toast("بدأ التنزيل")
            } catch (_: Exception) { toast("تعذر تنزيل الملف") }
        })

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            // Restore previous browsing session.
        } else {
            loadHome()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(15,17,25)) }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.rgb(22,24,34))
        }
        val brand = TextView(this).apply {
            text = "M  MIDASBUY"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(46), 1f))
        toolbar.addView(toolButton("⌂", "الرئيسية") { loadHome() })
        toolbar.addView(toolButton("↻", "تحديث") {
            if (isOnline()) { offlinePanel.visibility = View.GONE; webView.reload() } else showOffline()
        })
        toolbar.addView(toolButton("‹", "رجوع") {
            if (webView.canGoBack()) webView.goBack() else loadHome()
        })
        page.addView(toolbar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(245,185,25))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(50,52,62))
        }
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        webView = WebView(this)
        page.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        offlinePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = rounded(Color.rgb(28,30,42), dp(18))
            visibility = View.GONE
        }
        val msg = TextView(this).apply {
            text = "لا يوجد اتصال بالإنترنت"
            textSize = 20f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "تحقق من Wi-Fi أو بيانات الهاتف ثم حاول مرة أخرى."
            textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }
        val retry = toolButton("إعادة المحاولة", "إعادة المحاولة") {
            if (isOnline()) { offlinePanel.visibility = View.GONE; webView.reload() }
            else toast("ما زال الاتصال غير متاح")
        }.apply { setText("إعادة المحاولة"); textSize = 15f }
        offlinePanel.addView(msg); offlinePanel.addView(sub); offlinePanel.addView(retry)
        val overlayParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            setMargins(dp(22), 0, dp(22), 0)
        }
        root.addView(offlinePanel, overlayParams)
        setContentView(root)
    }

    private fun toolButton(symbol: String, description: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            contentDescription = description
            textSize = if (symbol.length == 1) 25f else 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245,185,25))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(39,41,52), dp(12))
            setPadding(dp(11), dp(5), dp(11), dp(5))
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(6) }
            layoutParams = lp
        }
    }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15,17,25))
        }
        val logo = TextView(this).apply {
            text = "M"
            textSize = 58f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(15,17,25))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(245,185,25), dp(28))
            setPadding(dp(25), dp(8), dp(25), dp(8))
        }
        val name = TextView(this).apply {
            text = "MIDASBUY EGYPT"
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }
        val loading = TextView(this).apply {
            text = "جاري تجهيز المتجر..."
            textSize = 13f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        splash.addView(logo); splash.addView(name); splash.addView(loading)
        splash.startAnimation(AlphaAnimation(0.25f, 1f).apply { duration = 550; fillAfter = true })
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        splash.postDelayed({ root.removeView(splash) }, 1200)
    }

    private fun showOffline() { offlinePanel.visibility = View.VISIBLE; progress.visibility = View.GONE }
    private fun loadHome() {
        if (isOnline()) { offlinePanel.visibility = View.GONE; webView.loadUrl(homeUrl) }
        else showOffline()
    }
    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun openExternal(uri: Uri): Boolean {
        return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
        catch (_: ActivityNotFoundException) { toast("لا يوجد تطبيق مناسب لفتح الرابط"); true }
        catch (_: Exception) { true }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        if (::webView.isInitialized) { webView.stopLoading(); webView.destroy() }
        super.onDestroy()
    }
}