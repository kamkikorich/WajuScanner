package com.example.wajuscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sejarah hasil imbasan QR / barcode. CamScanner-parity: setiap kod yang
 * berjaya dibaca disimpan supaya user boleh kembali padanya selepas
 * menutup dialog hasil (kandungan tak hilang lagi dahulu).
 */
@Entity(tableName = "qr_results")
data class QrResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val content: String,
    /** Nilai konstanta Barcode.FORMAT_* daripada ML Kit. */
    val format: Int,
    val timestamp: Long = System.currentTimeMillis(),
)
