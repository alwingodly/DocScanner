package com.example.docscanner.data.ocr.extractor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanExtractorTest {

    private val extractor = PanExtractor()

    @Test
    fun `keeps holder name when holder has PAN style initials`() = runBlocking {
        val raw = """
            NAME
            A K Kumar
            FATHER'S NAME
            Mahendra Kumar
            PAN
            ABCDE1234F
            DATE OF BIRTH
            01/01/1990
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("A K Kumar", result.name)
        assertEquals("Mahendra Kumar", result.fatherName)
    }

    @Test
    fun `extracts holder and father from sequential pan labels`() = runBlocking {
        val raw = """
            Name
            Father's Name
            Ravi Kumar
            Suresh Kumar
            ABCDE1234F
            01/01/1990
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Ravi Kumar", result.name)
        assertEquals("Suresh Kumar", result.fatherName)
    }

    @Test
    fun `does not return label keywords as pan names`() = runBlocking {
        val raw = """
            Name.
            Father's Name.
            ABCDE1234F
            01/01/1990
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertNull(result.name)
        assertNull(result.fatherName)
    }

    @Test
    fun `ignores huf style label artifacts when extracting pan name`() = runBlocking {
        val raw = """
            Hih Name
            Ravi Kumar
            ABCDE1234F
            01/01/1990
        """.trimIndent()

        val result = extractor.extract(emptyList(), raw, docHeight = 0)

        assertEquals("Ravi Kumar", result.name)
        assertNull(result.fatherName)
    }
}
