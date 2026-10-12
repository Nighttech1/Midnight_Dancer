package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.exchange.PackageExportLimits

/**
 * Caps for exchange zip / .mdance (C-107 / C-115).
 *
 * Count limits live in [PackageExportLimits]. Media/archive byte caps below.
 */
object ExchangeZipLimits {
    /** Align media pack budget with product package ceiling (500 MB hard). */
    val MAX_MEDIA_BYTES: Long = PackageExportLimits.MAX_PACKAGE_BYTES

    /** Hard reject on import if archive exceeds product package ceiling. */
    val MAX_ARCHIVE_BYTES: Long = PackageExportLimits.MAX_PACKAGE_BYTES

    const val EXCHANGE_DIR: String = "exchange"
    /** C-108: encrypted container (not open zip). */
    const val EXPORT_FILE_NAME: String = "debug_export.mdance"
    /** Legacy open zip still accepted for migration inbox. */
    const val LEGACY_EXPORT_ZIP_NAME: String = "debug_export.zip"
    const val IMPORT_INBOX_FILE_NAME: String = "import_inbox.mdance"
    const val IMPORT_INBOX_ZIP_NAME: String = "import_inbox.zip"
    const val FORMAT_ID: String = "midnight-dancer-full-backup"
    const val FORMAT_VERSION: Int = 1
}
