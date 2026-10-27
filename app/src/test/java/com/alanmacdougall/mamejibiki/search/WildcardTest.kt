package com.alanmacdougall.mamejibiki.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardTest {
    @Test fun plainQuery() {
        assertEquals("食", Wildcard.plainQuery("食"))
        assertEquals("食", Wildcard.plainQuery("食**"))
        assertEquals("日?", Wildcard.plainQuery("日?*"))
        assertNull(Wildcard.plainQuery("*食"))
        assertNull(Wildcard.plainQuery("*食*"))
        assertNull(Wildcard.plainQuery("食*会"))
    }

    @Test fun fullWidthAndOnlyWildcards() {
        assertEquals("*食?", Wildcard.normalize("＊食？"))
        assertTrue(Wildcard.isOnlyWildcards("**"))
        assertTrue(Wildcard.isOnlyWildcards("*?"))
    }

    @Test fun highlight() {
        assertEquals(listOf(1..1), Wildcard.highlight("*食", "夕食"))
        assertEquals(listOf(1..1), Wildcard.highlight("*食*", "試食会"))
        assertEquals(listOf(0..0, 3..3), Wildcard.highlight("食*会", "食事会会"))
        assertEquals(listOf(1..2), Wildcard.highlight("?べる*", "たべる"))
        assertNull(Wildcard.highlight("*食", "食事"))
    }

    @Test fun outsideTheBmp() {
        // 𠮟 is a surrogate pair: `?` takes the whole character, and ranges are in chars.
        assertEquals(listOf(2..2), Wildcard.highlight("?る", "𠮟る"))
        assertEquals(listOf(0..1), Wildcard.highlight("*𠮟*", "𠮟る"))
    }

    @Test fun glob() {
        assertEquals("*[[]食*", Wildcard.glob("*[食*"))
        assertEquals("食会", Wildcard.literal("*食*会?"))
    }
}
