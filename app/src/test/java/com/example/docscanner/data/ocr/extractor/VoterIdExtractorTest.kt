package com.example.docscanner.data.ocr.extractor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoterIdExtractorTest {

    private val extractor = VoterIdExtractor()

    @Test
    fun `extracts holder and spouse from standalone voter labels`() = runBlocking {
        val raw = """
            ELECTOR'S NAME
            Sunita Devi
            HUSBAND'S NAME
            Rajesh Kumar
            EPIC NO: ABC1234567
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Sunita Devi", result.name)
        assertEquals("Rajesh Kumar", result.spouseName)
    }

    @Test
    fun `drops relational voter name when it duplicates the holder`() = runBlocking {
        val raw = """
            NAME
            Anita Kumari
            FATHER'S NAME
            Anita Kumari
            EPIC NO: ABC1234567
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Anita Kumari", result.name)
        assertNull(result.fatherName)
        assertNull(result.spouseName)
    }

    @Test
    fun `extracts father when elector and father labels are adjacent in OCR flow`() = runBlocking {
        val raw = """
            Name of Elector Sunita Devi Father's Name Rajesh Kumar
            EPIC NO: ABC1234567
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Sunita Devi", result.name)
        assertEquals("Rajesh Kumar", result.fatherName)
        assertNull(result.spouseName)
    }

    @Test
    fun `extracts father when labels come before the two values`() = runBlocking {
        val raw = """
            Name of Elector
            Father's Name
            Sunita Devi
            Rajesh Kumar
            EPIC NO: ABC1234567
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Sunita Devi", result.name)
        assertEquals("Rajesh Kumar", result.fatherName)
        assertNull(result.spouseName)
    }
}
