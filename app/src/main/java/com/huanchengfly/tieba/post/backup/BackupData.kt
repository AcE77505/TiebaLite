package com.huanchengfly.tieba.post.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for a backed-up thread. Serialized as JSON to a file.
 *
 * Version history:
 *  1 – initial schema (URLs only)
 *  2 – added localForumAvatar / localAuthorAvatar / per-item local paths for offline viewing
 */
@Serializable
data class BackupData(
    val version: Int = 2,
    val threadId: Long,
    val backupTime: Long,
    val forumId: Long,
    val forumName: String,
    val forumAvatar: String?,
    /** Filename (relative to the app-internal backup-images directory) for offline viewing. */
    val localForumAvatar: String? = null,
    val title: String,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
    /** Filename (relative to the app-internal backup-images directory) for offline viewing. */
    val localAuthorAvatar: String? = null,
    val contentItems: List<BackupContentItem>,
)

@Serializable
sealed class BackupContentItem {

    @Serializable
    @SerialName("text")
    data class Text(val content: String) : BackupContentItem()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val originUrl: String,
        /** Filename (relative to the app-internal backup-images directory) for the thumbnail. */
        val localUrl: String? = null,
        /** Filename (relative to the app-internal backup-images directory) for the full-size image. */
        val localOriginUrl: String? = null,
    ) : BackupContentItem()

    @Serializable
    @SerialName("video")
    data class Video(
        val videoUrl: String,
        val coverUrl: String,
        val webUrl: String,
        /** Filename (relative to the app-internal backup-images directory) for the cover thumbnail. */
        val localCoverUrl: String? = null,
    ) : BackupContentItem()
}

/**
 * Returns the relative path (from [android.content.Context.getFilesDir]) where locally cached
 * images for this backup are stored.  Used by both [BackupRepository] and the viewer UI so the
 * paths always agree.
 */
fun BackupData.localImagesRelativePath() = backupImagesRelativePath(threadId)

/** @see BackupData.localImagesRelativePath */
fun backupImagesRelativePath(threadId: Long) = "backups/$threadId"
