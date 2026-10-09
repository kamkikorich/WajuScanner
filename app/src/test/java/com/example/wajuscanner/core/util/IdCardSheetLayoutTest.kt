package com.example.wajuscanner.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mengunci saiz cetakan Kad ID: kad MESTI pada saiz fizikal sebenar
 * (85.6 × 54 mm) atas helaian A4 — bukan diregangkan memenuhi halaman.
 */
class IdCardSheetLayoutTest {

    @Test
    fun `kad pada saiz fizikal 85_6 x 54 mm @300 dpi`() {
        val layout = ImageUtils.idCardSheetLayout(vertical = true)
        // Standard cetakan kad (300 dpi): 85.6 mm × 300/25.4 = 1011 px, 54 mm = 638 px
        assertEquals(1011, layout.cardWidth)
        assertEquals(638, layout.cardHeight)
    }

    @Test
    fun `helaian A4 pada 300 dpi`() {
        val layout = ImageUtils.idCardSheetLayout(vertical = true)
        // 210 mm × 300/25.4 = 2480; 297 mm × 300/25.4 = 3508
        assertEquals(2480, layout.sheetWidth)
        assertEquals(3508, layout.sheetHeight)
    }

    @Test
    fun `nisbah kad kekal ID-1 (1_586)`() {
        val layout = ImageUtils.idCardSheetLayout(vertical = true)
        val ratio = layout.cardWidth.toFloat() / layout.cardHeight
        assertTrue("nisbah $ratio bukan ID-1", ratio in 1.57f..1.60f)
    }

    @Test
    fun `kedua-dua sisi muat dalam helaian A4`() {
        listOf(true, false).forEach { vertical ->
            val layout = ImageUtils.idCardSheetLayout(vertical)
            val right = layout.startX + layout.cardWidth * (if (vertical) 1 else 2) +
                (if (vertical) 0 else layout.gap)
            val bottom = layout.startY + (layout.cardHeight + layout.labelHeight) *
                (if (vertical) 2 else 1) + (if (vertical) layout.gap else 0)
            assertTrue("startX negatif", layout.startX >= 0)
            assertTrue("startY negatif", layout.startY >= 0)
            assertTrue("kad terkeluar lebar helaian", right <= layout.sheetWidth)
            assertTrue("kad terkeluar tinggi helaian", bottom <= layout.sheetHeight)
        }
    }
}
