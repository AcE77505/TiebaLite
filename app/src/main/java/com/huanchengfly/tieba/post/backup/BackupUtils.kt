package com.huanchengfly.tieba.post.backup

import java.io.File

/**
 * Returns a local [File] for [imageKey] inside [imagesDir] when the file exists on disk,
 * otherwise returns [fallbackUrl] when it is non-blank, or `null` if neither is available.
 *
 * Used in both the ViewModel (when building [PostData]) and the Page (when building the
 * forum-chip header), so it lives here in the shared backup package.
 */
internal fun imageKeyOrUrl(imagesDir: File?, imageKey: String?, fallbackUrl: String?): Any? {
    if (!imageKey.isNullOrBlank() && imagesDir != null) {
        val file = File(imagesDir, imageKey)
        if (file.exists()) return file
    }
    return fallbackUrl?.takeIf { it.isNotBlank() }
}
