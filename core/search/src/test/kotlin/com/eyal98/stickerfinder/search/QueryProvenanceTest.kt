package com.eyal98.stickerfinder.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryProvenanceTest {

    @Test
    fun `chat text stays unlearnable after a backspace or an extra letter`() {
        val p = QueryProvenance()
        p.opened("doctor tomorrow")
        assertTrue(p.isFieldText)
        assertFalse(p.learnable)
        p.edited("doctor tomorro")
        assertFalse(p.isFieldText) // no longer exactly the chat's text: not removed from the chat
        assertFalse(p.learnable) // but still chat text: never saved
        p.edited("doctor tomorrow!")
        assertFalse(p.learnable)
    }

    @Test
    fun `clearing the search and typing a new one makes it learnable`() {
        val p = QueryProvenance()
        p.opened("doctor tomorrow")
        p.edited("")
        p.edited("k")
        p.edited("kermit")
        assertTrue(p.learnable)
    }

    @Test
    fun `an empty chat box starts learnable`() {
        val p = QueryProvenance()
        p.opened("")
        assertFalse(p.isFieldText)
        assertTrue(p.learnable)
    }
}
