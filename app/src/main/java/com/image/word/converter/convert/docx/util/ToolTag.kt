package com.image.word.converter.convert.docx.util

import androidx.annotation.StringRes
import com.image.word.converter.convert.docx.R

object ToolTag {
    const val IMAGE_TO_EXCEL = "image_to_excel"
    const val IMAGE_TO_PDF = "image_to_pdf"
    const val OCR = "ocr_scanner"
    const val DOCUMENT = "document_scanner"
    const val QR = "qr_code_scanner"

    fun normalize(raw: String): String {
        val value = raw.trim().lowercase()
        return when {
            value == IMAGE_TO_EXCEL || value.contains("excel") -> IMAGE_TO_EXCEL
            value == IMAGE_TO_PDF || value.contains("pdf") -> IMAGE_TO_PDF
            value == OCR || value.contains("ocr") -> OCR
            value == DOCUMENT || value.contains("document") -> DOCUMENT
            value == QR || value.contains("qr") -> QR
            else -> IMAGE_TO_EXCEL
        }
    }

    @StringRes
    fun labelRes(codeOrRaw: String): Int {
        return when (normalize(codeOrRaw)) {
            IMAGE_TO_PDF -> R.string.image_to_pdf
            OCR -> R.string.ocr_scanner
            DOCUMENT -> R.string.document_scanner
            QR -> R.string.qr_code_scanner
            else -> R.string.image_to_excel
        }
    }
}
