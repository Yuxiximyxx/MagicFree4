package io.github.yuxiximyxx.magicfree4.pods.hem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 机型识别与两张降噪表。Free4 的默认函数必须和改机型前一致。
 */
class PodProfileTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `free4 name tokens do not match air4 pro`() {
        assertEquals(PodProfiles.FREE4, PodProfiles.matchName("OPPO Enco Free4"))
        assertEquals(PodProfiles.FREE4, PodProfiles.matchName("my free4"))
        assertEquals(PodProfiles.AIR4_PRO, PodProfiles.matchName("OPPO Enco Air4 Pro"))
        assertEquals(PodProfiles.AIR4_PRO, PodProfiles.matchName("Air4-left"))
        assertNull(PodProfiles.matchName("WH-1000XM4"))
        assertNull(PodProfiles.matchName(""))
        assertNull(PodProfiles.matchName(null))
    }

    @Test
    fun `longer token wins when both could match`() {
        assertEquals("air4pro", PodProfiles.matchName("OPPO Enco Air4 Pro")!!.id)
        assertEquals("free4", PodProfiles.matchName("OPPO Enco Free4")!!.id)
    }

    @Test
    fun `product id little endian matches the documented layout`() {
        // 00 <id 3B LE>：067C10 → 10 7C 06，068C10 → 10 8C 06，丹拿 06C010 → 10 C0 06
        assertEquals("067C10", PodProfiles.parseProductId(bytes(0x00, 0x10, 0x7C, 0x06)))
        assertEquals("068C10", PodProfiles.parseProductId(bytes(0x00, 0x10, 0x8C, 0x06)))
        assertEquals("06C010", PodProfiles.parseProductId(bytes(0x00, 0x10, 0xC0, 0x06)))
        assertEquals(PodProfiles.AIR4_PRO, PodProfiles.byProductId("067C10"))
        assertEquals(PodProfiles.FREE4, PodProfiles.byProductId("068C10"))
        assertEquals(PodProfiles.FREE4, PodProfiles.byProductId("06C010"))
    }

    @Test
    fun `product id accepts big endian and ascii`() {
        assertEquals("067C10", PodProfiles.parseProductId(bytes(0x06, 0x7C, 0x10)))
        assertEquals("067C10", PodProfiles.parseProductId("id=067C10".toByteArray(Charsets.US_ASCII)))
        assertNull(PodProfiles.parseProductId(bytes(0x00, 0x01, 0x02)))
        assertNull(PodProfiles.byProductId("000000"))
    }

    @Test
    fun `free4 adaptive mapping is unchanged`() {
        assertEquals(11, HemAnc.bitForStatus(7))
        assertEquals(7, HemAnc.statusForRaw(1 shl 11))
        assertEquals(0, HemAnc.bitForStatus(1))
        assertEquals(5, HemAnc.bitForStatus(5))
        assertEquals(4, HemAnc.bitForStatus(4))
        assertTrue(PodProfiles.FREE4.supportsAdaptiveAnc)
        assertTrue(PodProfiles.FREE4.supportsCustomEq)
        assertEquals(listOf(0, 1, 2, 7), PodProfiles.FREE4.eqPresets.map { it.slot })
    }

    @Test
    fun `air4 pro hides adaptive and keeps the two tables separate`() {
        val p = PodProfiles.AIR4_PRO
        assertFalse(p.supportsAdaptiveAnc)
        assertFalse(p.supportsCustomEq)
        assertEquals(listOf(0, 1, 2), p.eqPresets.map { it.slot })
        assertEquals(5, HemAnc.bitForStatus(7, p))
        assertEquals(5, HemAnc.bitForStatus(5, p))
        assertEquals(0, HemAnc.bitForStatus(1, p))
        assertEquals(4, HemAnc.bitForStatus(4, p))
        assertEquals(2, HemAnc.bitForStatus(2, p))
        // 1/5/6/7 都上报成 status 5，不能从 slot 5 反推回 1 或 7
        assertEquals(5, HemAnc.statusForRaw(1 shl 1, p))
        assertEquals(5, HemAnc.statusForRaw(1 shl 5, p))
        assertEquals(5, HemAnc.statusForRaw(1 shl 6, p))
        assertEquals(5, HemAnc.statusForRaw(1 shl 7, p))
        assertEquals(5, HemAnc.statusForRaw(1 shl 11, p))
        assertEquals(1, HemAnc.statusForRaw(1 shl 3, p))
        assertEquals(2, HemAnc.statusForRaw(1 shl 8, p))
        assertNull(HemAnc.statusForRaw(0, p))
    }
}
