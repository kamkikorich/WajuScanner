package com.example.wajuscanner.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun sampleSizeStaysOneForSlightlyOversizedImage() {
        // 2412px dengan had 2048/2560 TIDAK lagi dipotong separuh (regresi lama: jadi 2).
        assertEquals(1, ImageUtils.calculateInSampleSize(1080, 2412, 2048))
        assertEquals(1, ImageUtils.calculateInSampleSize(1080, 2412, 2560))
    }

    @Test
    fun sampleSizeHalvesOnlyWhenFarLarger() {
        assertEquals(2, ImageUtils.calculateInSampleSize(5000, 5000, 2048))
        assertEquals(4, ImageUtils.calculateInSampleSize(9000, 9000, 2048))
    }

    @Test
    fun sampleSizeStaysOneWhenWithinLimit() {
        assertEquals(1, ImageUtils.calculateInSampleSize(1024, 768, 2048))
    }
}
