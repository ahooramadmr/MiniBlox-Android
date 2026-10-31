package ir.mineblox.app

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val downloadManager by lazy {
        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val pendingDownloads = mutableMapOf<Long, String>()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val fileName = pendingDownloads.remove(id) ?: return
            val query = DownloadManager.Query().setFilterById(id)

            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusColumn >= 0) cursor.getInt(statusColumn) else -1

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val fileUri = downloadManager.getUriForDownloadedFile(id)
                    if (fileUri != null && !isFinishing && !isDestroyed) {
                        showOpenMinecraftDialog(fileUri, fileName)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "دانلود $fileName کامل شد.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "دانلود ناموفق بود. دوباره تلاش کن.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            allowFileAccess = false
            allowContentAccess = true
            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                val scheme = uri.scheme?.lowercase() ?: return true
                val host = uri.host?.lowercase().orEmpty()

                // فقط دامنه اصلی و زیردامنه‌های MineBlox داخل اپ باز می‌شوند.
                if (scheme == "https" &&
                    (host == "mineblox.ir" || host.endsWith(".mineblox.ir"))
                ) {
                    return false
                }

                // تبلیغات و لینک‌های خارجی با انتخاب کاربر در مرورگر/برنامه مناسب باز می‌شوند.
                return openExternalLink(uri)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.setDownloadListener(
            DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                startFileDownload(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType
                )
            }
        )

        registerDownloadReceiver()

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL)
        } else {
            val restored = webView.restoreState(savedInstanceState)
            if (restored == null) webView.loadUrl(HOME_URL)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun openExternalLink(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "برنامه‌ای برای باز کردن این لینک پیدا نشد.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun startFileDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            Toast.makeText(this, "آدرس دانلود معتبر نیست.", Toast.LENGTH_SHORT).show()
            return
        }

        if (uri.scheme != "https" && uri.scheme != "http") {
            Toast.makeText(this, "این نوع لینک دانلود پشتیبانی نمی‌شود.", Toast.LENGTH_SHORT).show()
            return
        }

        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val safeName = guessedName
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "mineblox-download" }

        try {
            val request = DownloadManager.Request(uri)
                .setTitle(safeName)
                .setDescription("دانلود از MineBlox")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    safeName
                )

            if (!userAgent.isNullOrBlank()) {
                request.addRequestHeader("User-Agent", userAgent)
            }

            // برای لینک‌های دانلودی که به ورود کاربر نیاز دارند.
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) {
                request.addRequestHeader("Cookie", cookies)
            }

            val referer = webView.url
            if (!referer.isNullOrBlank()) {
                request.addRequestHeader("Referer", referer)
            }

            val downloadId = downloadManager.enqueue(request)
            pendingDownloads[downloadId] = safeName

            Toast.makeText(
                this,
                "دانلود شروع شد: $safeName",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            Toast.makeText(this, "شروع دانلود ممکن نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOpenMinecraftDialog(fileUri: Uri, fileName: String) {
        val isMinecraftPack = fileName.endsWith(".mcpack", true) ||
            fileName.endsWith(".mcaddon", true)

        if (!isMinecraftPack) {
            Toast.makeText(this, "دانلود کامل شد: $fileName", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("دانلود کامل شد")
            .setMessage("فایل $fileName دانلود شد. می‌خواهی آن را با Minecraft یا برنامه سازگار دیگری باز کنی؟")
            .setNegativeButton("بعداً", null)
            .setPositiveButton("باز کردن") { _, _ ->
                openDownloadedPack(fileUri)
            }
            .show()
    }

    private fun openDownloadedPack(fileUri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(viewIntent, "باز کردن فایل با"))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "برنامه‌ای برای باز کردن این فایل پیدا نشد. مطمئن شو Minecraft نصب است.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver قبلاً آزاد شده است.
        }
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://mineblox.ir/"
    }
}
