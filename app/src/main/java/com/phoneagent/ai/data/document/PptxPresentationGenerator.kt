package com.phoneagent.ai.data.document

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PptxSlideData(
    val title: String,
    val subtitle: String? = null,
    val bulletPoints: List<String> = emptyList(),
    val notes: String? = null
)

class PptxPresentationGenerator(private val context: Context) {

    private val documentsDir by lazy {
        File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
    }

    suspend fun createPresentation(
        deckTitle: String,
        slides: List<PptxSlideData>,
        customFileName: String? = null
    ): GeneratedDocumentResult = withContext(Dispatchers.IO) {
        try {
            val safeBase = (customFileName ?: "deck_${System.currentTimeMillis()}").replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
            val fileName = if (safeBase.endsWith(".pptx", ignoreCase = true)) safeBase else "$safeBase.pptx"
            val targetFile = File(documentsDir, fileName)

            val slideCount = slides.size.coerceAtLeast(1)

            FileOutputStream(targetFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. [Content_Types].xml
                    writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(slideCount))

                    // 2. _rels/.rels
                    writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())

                    // 3. ppt/presentation.xml
                    writeZipEntry(zos, "ppt/presentation.xml", buildPresentationXml(slideCount))

                    // 4. ppt/_rels/presentation.xml.rels
                    writeZipEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(slideCount))

                    // 5. ppt/slides/slide{N}.xml
                    slides.forEachIndexed { index, slideData ->
                        val slideNum = index + 1
                        writeZipEntry(zos, "ppt/slides/slide$slideNum.xml", buildSlideXml(slideNum, slideData))
                    }
                }
            }

            // Verify ZIP integrity
            var verifiedSlideCount = 0
            var hasContentTypes = false
            var hasPresentationXml = false

            targetFile.inputStream().use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "[Content_Types].xml") hasContentTypes = true
                        if (entry.name == "ppt/presentation.xml") hasPresentationXml = true
                        if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                            verifiedSlideCount++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (!hasContentTypes || !hasPresentationXml || verifiedSlideCount != slideCount) {
                return@withContext GeneratedDocumentResult(
                    success = false,
                    fileType = "PPTX",
                    fileName = fileName,
                    filePath = targetFile.absolutePath,
                    fileSizeBytes = targetFile.length(),
                    unitCount = verifiedSlideCount,
                    contentUri = "",
                    message = "Verification failed: Corrupt OpenXML package structure.",
                    previewSnippet = ""
                )
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)

            GeneratedDocumentResult(
                success = true,
                fileType = "PPTX",
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                unitCount = verifiedSlideCount,
                contentUri = uri.toString(),
                message = "Editable OpenXML presentation generated successfully ($verifiedSlideCount slides, ${targetFile.length()} bytes).",
                previewSnippet = "Deck: $deckTitle | Slides: $verifiedSlideCount | Fully editable in PowerPoint & Google Slides"
            )
        } catch (e: Exception) {
            GeneratedDocumentResult(
                success = false,
                fileType = "PPTX",
                fileName = customFileName ?: "error.pptx",
                filePath = "",
                fileSizeBytes = 0L,
                unitCount = 0,
                contentUri = "",
                message = "PPTX creation failed: ${e.message}",
                previewSnippet = ""
            )
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(slideCount: Int): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
        sb.appendLine("""  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        for (i in 1..slideCount) {
            sb.appendLine("""  <Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        sb.appendLine("""</Types>""")
        return sb.toString()
    }

    private fun buildRootRelsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
            </Relationships>
        """.trimIndent()
    }

    private fun buildPresentationXml(slideCount: Int): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        sb.appendLine("""  <p:sldIdLst>""")
        for (i in 1..slideCount) {
            val sldId = 255 + i
            val rId = "rId$i"
            sb.appendLine("""    <p:sldId id="$sldId" r:id="$rId" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>""")
        }
        sb.appendLine("""  </p:sldIdLst>""")
        // 16:9 widescreen format (9,144,000 x 5,143,500 EMUs)
        sb.appendLine("""  <p:sldSz cx="9144000" cy="5143500" type="screen16x9"/>""")
        sb.appendLine("""</p:presentation>""")
        return sb.toString()
    }

    private fun buildPresentationRelsXml(slideCount: Int): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..slideCount) {
            sb.appendLine("""  <Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
        }
        sb.appendLine("""</Relationships>""")
        return sb.toString()
    }

    private fun buildSlideXml(slideNum: Int, slideData: PptxSlideData): String {
        val escapedTitle = escapeXml(slideData.title)
        val escapedSub = slideData.subtitle?.let { escapeXml(it) }

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        sb.appendLine("""  <p:cSld>""")
        sb.appendLine("""    <p:spTree>""")
        sb.appendLine("""      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
        sb.appendLine("""      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""")

        // 1. Title Shape
        sb.appendLine("""      <p:sp>""")
        sb.appendLine("""        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>""")
        sb.appendLine("""        <p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="7772400" cy="800100"/></a:xfrm></p:spPr>""")
        sb.appendLine("""        <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr><a:t>$escapedTitle</a:t></a:r></a:p>""")
        if (escapedSub != null) {
            sb.appendLine("""        <a:p><a:r><a:rPr lang="en-US" sz="1400" i="1"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr><a:t>$escapedSub</a:t></a:r></a:p>""")
        }
        sb.appendLine("""        </p:txBody>""")
        sb.appendLine("""      </p:sp>""")

        // 2. Content / Bullets Shape
        if (slideData.bulletPoints.isNotEmpty()) {
            sb.appendLine("""      <p:sp>""")
            sb.appendLine("""        <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>""")
            sb.appendLine("""        <p:spPr><a:xfrm><a:off x="685800" y="1463040"/><a:ext cx="7772400" cy="3200400"/></a:xfrm></p:spPr>""")
            sb.appendLine("""        <p:txBody><a:bodyPr/><a:lstStyle/>""")
            slideData.bulletPoints.forEach { bp ->
                val escapedBp = escapeXml(bp)
                sb.appendLine("""          <a:p><a:pPr lvl="0"><a:buChar char="•"/></a:pPr><a:r><a:rPr lang="en-US" sz="1600"><a:solidFill><a:srgbClr val="334155"/></a:solidFill></a:rPr><a:t>$escapedBp</a:t></a:r></a:p>""")
            }
            sb.appendLine("""        </p:txBody>""")
            sb.appendLine("""      </p:sp>""")
        }

        sb.appendLine("""    </p:spTree>""")
        sb.appendLine("""  </p:cSld>""")
        sb.appendLine("""</p:sld>""")
        return sb.toString()
    }
}
