package com.eyal98.stickerfinder.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaEstimatorTest {

    private val minute = 60_000L

    @Test
    fun `no estimate until there's enough progress`() {
        val eta = EtaEstimator()
        assertNull(eta.update(1000, 0))
        assertNull(eta.update(998, 30_000)) // too soon, too few
    }

    @Test
    fun `estimates from the recent rate`() {
        val eta = EtaEstimator()
        eta.update(1000, 0)
        // 20 stickers in 2 minutes: 10 a minute, 980 left -> 98 minutes.
        assertEquals(98 * minute, eta.update(980, 2 * minute))
    }

    @Test
    fun `newly found stickers restart the estimate`() {
        val eta = EtaEstimator()
        eta.update(100, 0)
        eta.update(80, 2 * minute)
        assertNull(eta.update(150, 3 * minute))
    }

    @Test
    fun `only the last ten minutes count`() {
        val eta = EtaEstimator()
        eta.update(1000, 0)
        eta.update(990, 20 * minute) // very slow at first
        // Then fast: 100 in 5 minutes. The old sample is dropped once it's out of the window.
        val left = eta.update(890, 25 * minute)!!
        assertEquals(890 * 5 * minute / 100, left)
    }
}
