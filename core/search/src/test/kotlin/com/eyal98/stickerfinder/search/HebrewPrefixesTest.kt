package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewPrefixesTest {

    @Test
    fun `strips stacked prefixes one letter at a time`() {
        assertEquals(listOf("והחתול", "החתול", "חתול"), HebrewPrefixes.variants("והחתול"))
    }

    @Test
    fun `keeps at least two letters of stem`() {
        assertEquals(listOf("ולב", "לב"), HebrewPrefixes.variants("ולב"))
        assertEquals(listOf("לב"), HebrewPrefixes.variants("לב"))
    }

    @Test
    fun `stops at the first non-prefix letter`() {
        assertEquals(listOf("חתול"), HebrewPrefixes.variants("חתול"))
    }

    @Test
    fun `leaves non-Hebrew tokens alone`() {
        assertEquals(listOf("cat"), HebrewPrefixes.variants("cat"))
    }
}
