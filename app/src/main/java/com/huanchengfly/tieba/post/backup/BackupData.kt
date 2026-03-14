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
 *  4 – added [replies] field containing all fetched post replies
 *  5 – added [replyNum] (total post count) and [likeCount] (total like/agree count)
 *  6 – added [postTime] (original creation time of the first/楼主 post)
 */
@Serializable
data class BackupData(
    val version: Int = 6,
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
    /** Original creation time (Unix ms) of the first/楼主 post. Zero for v5 and earlier backups. */
    val postTime: Long = 0L,
    /** All fetched replies (楼层 ≥ 2). May be a partial list if backup was cancelled. */
    val replies: List<BackupReply> = emptyList(),
    /**
     * Total reply count for the thread at the time of backup (equal to the number shown to the
     * left of the "只看楼主" button).  May be greater than [replies].size when some replies
     * were removed due to violations and therefore could not be fetched.
     */
    val replyNum: Int = 0,
    /** Thread-level total like/agree count at the time of backup. */
    val likeCount: Long = 0L,
)

/**
 * A single reply (post) in a backed-up thread.
 */
@Serializable
data class BackupReply(
    val id: Long,
    val floor: Int,
    val time: Long,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
    /** Entry name inside the companion ZIP file for the author avatar. */
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
