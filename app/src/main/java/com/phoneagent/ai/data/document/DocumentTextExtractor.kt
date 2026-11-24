package com.phoneagent.ai.data.document

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream

object DocumentTextExtractor {

    suspend fun extractText(
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val lowerName = fileName.lowercase()
            when {
                lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") ||
                lowerName.endsWith(".csv") || lowerName.endsWith(".xml") || mimeType.startsWith("text/") -> {
                    extractPlainText(contentResolver, uri)
                }
                lowerName.endsWith(".pptx") || mimeType.contains("presentation") -> {
                    extractPptxText(contentResolver, uri)
                }
                lowerName.endsWith(".pdf") || mimeType.contains("pdf") -> {
                    extractPdfText(contentResolver, uri)
                }
                else -> null
            }
        } catch (e: Exception) {
            "Error extracting text from $fileName: ${e.message}"
        }
    }

    private fun extractPlainText(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            val text = String(bytes)
            if (text.length > 50000) {
                text.take(50000) + "\n...[truncated at 50,000 characters]"
            } else {
                text
            }
        }
    }

    fun extractPptxTextFromStream(inputStream: InputStream): String {
        val slidesMap = sortedMapOf<Int, StringBuilder>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
            val textTagRegex = Regex("""<a:t[^>]*>(.*?)</a:t>""")

            while (entry != null) {
                val match = slideRegex.matchEntire(entry.name)
                if (match != null) {
                    val slideNum = match.groupValues[1].toIntOrNull() ?: 999
                    val slideBuilder = slidesMap.getOrPut(slideNum) { StringBuilder() }

                    val bytes = zip.readBytes()
                    val xmlContent = String(bytes)

                    textTagRegex.findAll(xmlContent).forEach { tMatch ->
                        val text = unescapeXml(tMatch.groupValues[1].trim())
                        if (text.isNotEmpty()) {
                            slideBuilder.appendLine(text)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (slidesMap.isEmpty()) {
            return "No text content found in PowerPoint presentation."
        }

        return buildString {
            appendLine("PowerPoint Presentation Extraction (${slidesMap.size} slides):")
            slidesMap.forEach { (num, text) ->
                appendLine("\n--- Slide $num ---")
                append(text.toString().trim())
            }
        }
    }

    private fun unescapeXml(str: String): String {
        return str.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun extractPptxText(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { stream ->
            extractPptxTextFromStream(stream)
        }
    }

    fun extractPdfTextFromStream(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        val textBuilder = StringBuilder()

        // Pure Kotlin bounded PDF text stream parser
        // Searches for PDF text blocks: BT ... ET with (text) Tj or [(text)] TJ
        val pdfStr = String(bytes, Charsets.ISO_8859_1)
        val tjRegex = Regex("""\(([^)]*)\)\s*Tj""")
        val arrayTjRegex = Regex("""\[(.*?)\]\s*TJ""")

        var matchesFound = 0
        tjRegex.findAll(pdfStr).forEach { m ->
            val decoded = decodePdfString(m.groupValues[1])
            if (decoded.isNotBlank()) {
                textBuilder.append(decoded).append(" ")
                matchesFound++
            }
        }

        if (matchesFound < 5) {
            arrayTjRegex.findAll(pdfStr).forEach { m ->
                val inner = m.groupValues[1]
                val itemRegex = Regex("""\(([^)]*)\)""")
                itemRegex.findAll(inner).forEach { itemMatch ->
                    val decoded = decodePdfString(itemMatch.groupValues[1])
                    if (decoded.isNotBlank()) {
                        textBuilder.append(decoded).append(" ")
                        matchesFound++
                    }
                }
            }
        }

        val rawText = textBuilder.toString().trim().replace(Regex("""\s+"""), " ")
        if (rawText.isBlank()) {
            return "PDF document attached (${bytes.size} bytes). [Note: Document contains scanned images, compressed streams, or vectors without extractable plain text]."
        }

        val bounded = if (rawText.length > 50000) {
            rawText.take(50000) + "\n...[truncated at 50,000 characters]"
        } else {
            rawText
        }

        return "PDF Document Text Extraction:\n$bounded"
    }

    private fun extractPdfText(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { stream ->
            extractPdfTextFromStream(stream)
        }
    }

    private fun decodePdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
