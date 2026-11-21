package com.devekene.notebook.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.devekene.notebook.core.crypto.CryptoEngine
import com.devekene.notebook.core.crypto.PassphraseEncryptedBundle
import com.devekene.notebook.domain.model.Category
import com.devekene.notebook.domain.model.NoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class DecryptedVaultPayload(
    val exportedAt: Long,
    val appVersion: String = "1.0",
    val categories: List<Category>,
    val notes: List<NoteItem>
)

class NoteExporter(
    private val context: Context,
    private val cryptoEngine: CryptoEngine
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun buildMarkdown(note: NoteItem): String = buildString {
        appendLine("---")
        appendLine("title: \"${note.title.ifBlank { "Untitled Note" }.replace("\"", "\\\"")}\"")
        if (note.description.isNotBlank()) {
            appendLine("description: \"${note.description.replace("\"", "\\\"")}\"")
        }
        appendLine("category: \"${note.categoryName}\"")
        if (note.tags.isNotEmpty()) {
            appendLine("tags: [${note.tags.joinToString(", ") { "\"$it\"" }}]")
        }
        appendLine("created: \"${dateFormatter.format(Date(note.createdAt))}\"")
        appendLine("updated: \"${dateFormatter.format(Date(note.updatedAt))}\"")
        appendLine("encrypted: ${note.isEncrypted}")
        appendLine("---")
        appendLine()
        appendLine("# ${note.title.ifBlank { "Untitled Note" }}")
        if (note.description.isNotBlank()) {
            appendLine()
            appendLine("> ${note.description}")
        }
        appendLine()
        appendLine(note.content)
        if (note.tags.isNotEmpty()) {
            appendLine()
            appendLine(note.tags.joinToString(" ") { "#$it" })
        }
    }

    fun buildMarkdownBundle(notes: List<NoteItem>): String = buildString {
        appendLine("# Notebook Encrypted Vault Export")
        appendLine("Exported on ${dateFormatter.format(Date())} · ${notes.size} note(s)")
        appendLine()
        notes.forEachIndexed { index, note ->
            if (index > 0) {
                appendLine()
                appendLine("---")
                appendLine()
            }
            append(buildMarkdown(note))
        }
    }

    suspend fun writeMarkdownToUri(uri: Uri, notes: List<NoteItem>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = if (notes.size == 1) buildMarkdown(notes.first()) else buildMarkdownBundle(notes)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Unable to open destination file.")
        }
    }

    suspend fun writePdfToUri(uri: Uri, notes: List<NoteItem>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                writePdfStream(notes, stream)
            } ?: error("Unable to open destination PDF file.")
        }
    }

    private fun writePdfStream(notes: List<NoteItem>, outputStream: OutputStream) {
        val pageWidth = 595 // A4 width in points (72 dpi)
        val pageHeight = 842 // A4 height in points
        val marginH = 48f
        val contentWidth = (pageWidth - marginH * 2).toInt()
        val topMargin = 56f
        val bottomLimit = pageHeight - 54f

        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = topMargin

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 37, 77)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rulePaint = Paint().apply {
            color = Color.rgb(37, 99, 235)
            strokeWidth = 2f
        }
        val subtleRulePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 22, 40)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 94, 122)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 37, 58)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 136, 158)
            textSize = 9.5f
        }

        fun drawPageHeaderAndFooter() {
            canvas.drawText("NOTEBOOK · E2EE VAULT EXPORT", marginH, 34f, brandPaint)
            canvas.drawLine(marginH, 42f, pageWidth - marginH, 42f, rulePaint)
            canvas.drawText(
                "Page $pageNumber · Generated ${dateFormatter.format(Date())}",
                marginH,
                pageHeight - 24f,
                footerPaint
            )
        }

        fun nextPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = topMargin + 8f
            drawPageHeaderAndFooter()
        }

        drawPageHeaderAndFooter()
        y += 8f

        notes.forEachIndexed { idx, note ->
            if (idx > 0) {
                if (y + 90f > bottomLimit) {
                    nextPage()
                } else {
                    y += 14f
                    canvas.drawLine(marginH, y, pageWidth - marginH, y, subtleRulePaint)
                    y += 22f
                }
            }

            val titleText = note.title.ifBlank { "Untitled Note" }
            val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.1f)
                .build()

            if (y + titleLayout.height > bottomLimit) nextPage()
            canvas.save()
            canvas.translate(marginH, y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleLayout.height + 6f

            val tagsPart = if (note.tags.isNotEmpty()) "  ·  ${note.tags.joinToString(" ") { "#$it" }}" else ""
            val metaText = "${note.categoryName}  ·  ${dateFormatter.format(Date(note.updatedAt))}  ·  ${note.wordCount} words$tagsPart"
            val metaLayout = StaticLayout.Builder.obtain(metaText, 0, metaText.length, metaPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            if (y + metaLayout.height > bottomLimit) nextPage()
            canvas.save()
            canvas.translate(marginH, y)
            metaLayout.draw(canvas)
            canvas.restore()
            y += metaLayout.height + 10f

            if (note.description.isNotBlank()) {
                val descLayout = StaticLayout.Builder.obtain(
                    note.description,
                    0,
                    note.description.length,
                    metaPaint,
                    contentWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .build()
                if (y + descLayout.height > bottomLimit) nextPage()
                canvas.save()
                canvas.translate(marginH, y)
                descLayout.draw(canvas)
                canvas.restore()
                y += descLayout.height + 10f
            } else {
                y += 4f
            }

            val paragraphs = note.content.ifBlank { "(Empty note)" }.split("\n")
            for (paragraph in paragraphs) {
                val paraText = if (paragraph.isEmpty()) " " else paragraph
                val paraLayout = StaticLayout.Builder.obtain(paraText, 0, paraText.length, bodyPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2.5f, 1.25f)
                    .build()

                if (y + paraLayout.height <= bottomLimit) {
                    canvas.save()
                    canvas.translate(marginH, y)
                    paraLayout.draw(canvas)
                    canvas.restore()
                    y += paraLayout.height + 4f
                } else {
                    // Draw line-by-line across pages for long paragraphs
                    for (lineIdx in 0 until paraLayout.lineCount) {
                        val lineStart = paraLayout.getLineStart(lineIdx)
                        val lineEnd = paraLayout.getLineEnd(lineIdx)
                        val lineHeight = paraLayout.getLineBottom(lineIdx) - paraLayout.getLineTop(lineIdx)
                        if (y + lineHeight > bottomLimit) {
                            nextPage()
                        }
                        val lineStr = paraText.substring(lineStart, lineEnd)
                        canvas.drawText(lineStr, marginH, y + lineHeight - 3f, bodyPaint)
                        y += lineHeight + 2f
                    }
                    y += 4f
                }
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        outputStream.flush()
        pdf.close()
    }

    suspend fun writeEncryptedVaultToUri(
        uri: Uri,
        notes: List<NoteItem>,
        categories: List<Category>,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = DecryptedVaultPayload(
                exportedAt = System.currentTimeMillis(),
                categories = categories,
                notes = notes
            )
            val plaintextJson = json.encodeToString(payload)
            val bundle = cryptoEngine.encryptWithPassphrase(plaintextJson, passphrase)
            val bundleJson = json.encodeToString(bundle)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bundleJson.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Unable to open destination backup file.")
        }
    }

    suspend fun readEncryptedVaultFromUri(
        uri: Uri,
        passphrase: String
    ): Result<DecryptedVaultPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("Unable to read backup file.")
            val bundle = json.decodeFromString<PassphraseEncryptedBundle>(raw)
            val decryptedJson = cryptoEngine.decryptWithPassphrase(bundle, passphrase)
            json.decodeFromString<DecryptedVaultPayload>(decryptedJson)
        }
    }

    suspend fun createShareIntent(note: NoteItem, isPdf: Boolean): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = note.title.ifBlank { "note" }
                .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                .take(40)
            val file = if (isPdf) {
                File(exportDir, "${safeName}.pdf").also { f ->
                    f.outputStream().use { writePdfStream(listOf(note), it) }
                }
            } else {
                File(exportDir, "${safeName}.md").also { f ->
                    f.writeText(buildMarkdown(note), Charsets.UTF_8)
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = if (isPdf) "application/pdf" else "text/markdown"
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Notebook Note" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
