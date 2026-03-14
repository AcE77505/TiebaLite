package com.huanchengfly.tieba.post.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for a backed-up thread. Serialized as JSON to a file.
 *
 * Version history:
 *  1 – initial schema (URLs only)
 *  2 – added localForumAvatar / localAuthorAvatar / per-item local paths for offline viewing
 *  3 – replaced private-dir local paths with ZIP-entry image keys; images stored as
 *      `{threadId}.zip` (or `{threadId}_{backupTime}.zip`) in the user-chosen SAF directory
 */
@Serializable
data class BackupData(
    val version: Int = 3,
    val threadId: Long,
    val backupTime: Long,
    val forumId: Long,
    val forumName: String,
    val forumAvatar: String?,
    /** Entry name inside the companion ZIP file for offline viewing. */
    val imageKeyForumAvatar: String? = null,
    val title: String,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
    /** Entry name inside the companion ZIP file for offline viewing. */
    val imageKeyAuthorAvatar: String? = null,
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
        /**
         * Entry name inside the companion ZIP file for the highest-quality image.
         * Only one image per content item is stored (originUrl preferred).
         */
        val imageKey: String? = null,
    ) : BackupContentItem()

    @Serializable
    @SerialName("video")
    data class Video(
        val videoUrl: String,
        val coverUrl: String,
        val webUrl: String,
        /** Entry name inside the companion ZIP file for the cover thumbnail. */
        val imageKeyCover: String? = null,
    ) : BackupContentItem()
}
