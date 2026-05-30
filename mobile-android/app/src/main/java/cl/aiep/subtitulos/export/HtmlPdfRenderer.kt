package cl.aiep.subtitulos.export

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.WebViewPdfPrinter
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders an HTML document to a PDF file using an offscreen [WebView] and the
 * platform print pipeline (no system dialog). Runs on the main thread because
 * WebView is not thread-safe; suspends until the PDF is written.
 *
 * The WebView is attached (1x1, fully transparent) to the Activity content view:
 * a WebView that never gets laid out does not complete the print layout pass, so
 * the print callback would hang forever. A timeout guards against that anyway.
 */
object HtmlPdfRenderer {
    private const val TAG = "AiepSubtitulos"
    private const val RENDER_TIMEOUT_MS = 45_000L

    suspend fun render(context: Context, html: String, outFile: File): File =
        withContext(Dispatchers.Main) {
            outFile.parentFile?.mkdirs()
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true // template enhancer numbers sections + builds the index
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                alpha = 0f
            }
            val container = (context as? Activity)
                ?.findViewById<ViewGroup>(android.R.id.content)
            container?.addView(webView, ViewGroup.LayoutParams(1, 1))
            Log.i(TAG, "PDF render: attached=${container != null}, file=${outFile.name}")
            try {
                withTimeout(RENDER_TIMEOUT_MS) { loadAndPrint(webView, html, outFile) }
            } finally {
                container?.removeView(webView)
                webView.destroy()
            }
        }

    private suspend fun loadAndPrint(webView: WebView, html: String, outFile: File): File =
        suspendCancellableCoroutine { cont ->
            var started = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (started) return
                    started = true
                    Log.i(TAG, "PDF render: onPageFinished, scheduling print")
                    // Let webfonts/layout settle before snapshotting to PDF.
                    view.postDelayed({
                        runCatching {
                            val adapter = view.createPrintDocumentAdapter("aiep-study")
                            val attributes = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                            Log.i(TAG, "PDF render: starting print to file")
                            WebViewPdfPrinter.print(
                                adapter,
                                attributes,
                                outFile,
                                object : WebViewPdfPrinter.Callback {
                                    override fun onSuccess(file: File) {
                                        Log.i(TAG, "PDF render: success (${file.length()} bytes)")
                                        if (cont.isActive) cont.resume(file)
                                    }

                                    override fun onError(error: Throwable) {
                                        Log.e(TAG, "PDF render: error", error)
                                        if (cont.isActive) cont.resumeWithException(error)
                                    }
                                },
                            )
                        }.onFailure { e ->
                            Log.e(TAG, "PDF render: print setup failed", e)
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }, 300L)
                }
            }
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        }
}
