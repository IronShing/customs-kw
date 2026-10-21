package com.ironshing.customs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

/** The EPA search page in a WebView, with the importer CR filled in and searched on first load. */
class SiteActivity : ComponentActivity() {
    private var searched = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cr = Store.crs(this).firstOrNull()
        val web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (searched || cr == null || !url.contains("BrokerSearchDeclaration")) return
                    searched = true
                    val v = JSONObject.quote(cr)
                    view.evaluateJavascript(
                        """(function(){var f=document.getElementById('ContentPlaceHolder1_tbCommericalNo');
                           if(!f)return;f.value=$v;__doPostBack('ctl00${'$'}ContentPlaceHolder1${'$'}btnSearch','');})()""",
                        null,
                    )
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back from the auto-search result would just re-post the form; leave instead.
                finish()
            }
        })
        web.loadUrl(Epa.PAGE)
    }
}
