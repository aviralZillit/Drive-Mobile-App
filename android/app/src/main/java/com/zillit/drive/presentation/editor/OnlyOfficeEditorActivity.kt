package com.zillit.drive.presentation.editor

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zillit.drive.BuildConfig
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnlyOfficeEditorActivity : AppCompatActivity() {

    @Inject lateinit var repository: DriveRepository

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView

    companion object {
        const val EXTRA_FILE_ID = "fileId"
        const val EXTRA_FILE_NAME = "fileName"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout: toolbar + webview + loading indicator
        val contentView = android.widget.FrameLayout(this)

        webView = WebView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP
            }
            isIndeterminate = false
            max = 100
        }

        tvLoading = TextView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            text = "Loading editor..."
            textSize = 16f
        }

        contentView.addView(webView)
        contentView.addView(progressBar)
        contentView.addView(tvLoading)
        setContentView(contentView)

        // Title
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Document"
        title = fileName

        // Setup WebView with Collabora-optimized settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // Required for Collabora iframe to work
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Allow third-party cookies (Collabora needs this for WOPI auth)
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            // Enable zoom controls for document viewing
            builtInZoomControls = true
            displayZoomControls = false
            // Cache mode — use network when available
            cacheMode = WebSettings.LOAD_DEFAULT
            // Media playback
            mediaPlaybackRequiresUserGesture = false
            // Database/storage for Collabora editor state
            databaseEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.visibility = View.VISIBLE
                tvLoading.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tvLoading.text = "Failed to load editor"
                    tvLoading.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                }
            }

            // Allow self-signed certs in debug builds (Collabora dev setup)
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (BuildConfig.DEBUG) {
                    handler?.proceed()
                } else {
                    handler?.cancel()
                    tvLoading.text = "SSL certificate error"
                    tvLoading.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                }
            }

            // Allow all Collabora-related navigation (cross-origin iframes)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Support back navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load editor
        val fileId = intent.getStringExtra(EXTRA_FILE_ID)
        if (fileId != null) {
            loadEditor(fileId)
        } else {
            Toast.makeText(this, "File ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadEditor(fileId: String) {
        lifecycleScope.launch {
            repository.getEditorPageToken(fileId).fold(
                onSuccess = { token ->
                    val baseUrl = BuildConfig.DRIVE_BASE_URL
                    val url = "$baseUrl/v2/drive/editor/$fileId/page?token=$token"
                    webView.loadUrl(url)
                },
                onFailure = { error ->
                    tvLoading.text = "Failed to load editor: ${error.message}"
                    tvLoading.visibility = View.VISIBLE
                    Toast.makeText(this@OnlyOfficeEditorActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
