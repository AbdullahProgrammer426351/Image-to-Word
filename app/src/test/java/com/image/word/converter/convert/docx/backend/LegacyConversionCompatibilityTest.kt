package com.image.word.converter.convert.docx.backend

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyConversionCompatibilityTest {
    @Test
    fun phaseA_keepsLegacyImageConversionContractUnchanged() {
        val source = File(
            "src/main/java/com/image/word/converter/convert/docx/data/ImageToWordApi.kt",
        ).readText()

        assertTrue(
            source.contains(
                "https://us-central1-converter-api-project.cloudfunctions.net/imageToWord",
            ),
        )
        assertTrue(source.contains("JSONObject().put(\"image\", base64)"))
        assertFalse(source.contains("imageToWordV2"))
        assertFalse(source.contains("idempotencyKey"))
    }
}
