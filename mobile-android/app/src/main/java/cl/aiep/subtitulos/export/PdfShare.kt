package cl.aiep.subtitulos.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object PdfShare {
    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun share(context: Context, file: File, mimeType: String = MIME_PDF) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Descargar"))
    }
}
