package com.example.wajuscanner.data.local.db.converter

import androidx.room.TypeConverter
import java.util.Date

object DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
