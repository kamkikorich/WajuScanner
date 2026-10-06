package com.example.wajuscanner.core.util

import android.content.Context
import com.example.wajuscanner.core.common.Constants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun ensurePrivateDirectory(context: Context, dirName: String): File {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createUniqueFile(directory: File, baseName: String, extension: String): File {
        val sanitizedBase = baseName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        var file = File(directory, "$sanitizedBase.$extension")
        var counter = 1
        while (file.exists()) {
            file = File(directory, "${sanitizedBase}_$counter.$extension")
            counter++
        }
        return file
    }

    fun generateScanFileName(prefix: String = Constants.DEFAULT_FILE_PREFIX): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$timestamp"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups.coerceAtMost(units.lastIndex)])
    }

    fun deleteDirectoryContents(directory: File): Boolean {
        if (!directory.isDirectory) return false
        return directory.listFiles()?.all { it.deleteRecursively() } ?: true
    }
}
