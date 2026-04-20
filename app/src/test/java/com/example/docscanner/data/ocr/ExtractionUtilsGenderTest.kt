package com.example.docscanner.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionUtilsGenderTest {

    @Test
    fun `extracts gender when label and value are on separate lines`() {
        val raw = """
            Name of Elector
            Sunita Devi
            Sex
            Female
        """.trimIndent()

        assertEquals("Female", ExtractionUtils.extractGender(raw))
    }

    @Test
    fun `extracts gender when gender hint and value appear anywhere in OCR text`() {
        val raw = """
            VOTER ID CARD
            Gender available on card
            Name of Elector Sunita Devi
            Male
        """.trimIndent()

        assertEquals("Male", ExtractionUtils.extractGender(raw))
    }
}
