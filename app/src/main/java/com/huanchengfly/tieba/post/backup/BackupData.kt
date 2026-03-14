package com.huanchengfly.tieba.post.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for a backed-up thread. Serialized as JSON to a file.
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val threadId: Long,
    val backupTime: Long,
    val forumId: Long,
    val forumName: String,
    val forumAvatar: String?,
    val title: String,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
    val contentItems: List<BackupContentItem>,
)

@Serializable
sealed class BackupContentItem {

    @Serializable
    @SerialName("text")
    data class Text(val content: String) : BackupContentItem()

    @Serializable
    @SerialName("image")
    data class Image(val url: String, val originUrl: String) : BackupContentItem()

    @Serializable
    @SerialName("video")
    data class Video(val videoUrl: String, val coverUrl: String, val webUrl: String) : BackupContentItem()
}
