package com.example.wajuscanner

import android.content.Intent

/**
 * How this Activity was launched.
 *
 * - [NORMAL]: standard launch from the launcher icon.
 * - [EXTERNAL_SCAN_TO_PDF]: another app invoked us via the
 *   `com.example.wajuscanner.action.SCAN_TO_PDF` intent; the user scans
 *   documents and the resulting PDF is returned as a URI.
 */
enum class LaunchMode {
    NORMAL,
    EXTERNAL_SCAN_TO_PDF;

    companion object {
        const val ACTION_SCAN_TO_PDF = "com.example.wajuscanner.action.SCAN_TO_PDF"
    }
}

fun Intent.resolveLaunchMode(): LaunchMode = when (action) {
    LaunchMode.ACTION_SCAN_TO_PDF -> LaunchMode.EXTERNAL_SCAN_TO_PDF
    else -> LaunchMode.NORMAL
}