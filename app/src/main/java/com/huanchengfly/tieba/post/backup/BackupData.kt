package com.huanchengfly.tieba.post.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 6,
    val threadId: Long,
    val backupTime: Long,
    val forumId: Long,
    val forumName: String,
    val forumAvatar: String?,
    val imageKeyForumAvatar: String? = null,
    val title: String,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
    val imageKeyAuthorAvatar: String? = null,
    val contentItems: List<BackupContentItem>,
    val postTime: Long = 0L,
    val replies: List<BackupReply> = emptyList(),
    val replyNum: Int = 0,
    val likeCount: Long = 0L,
)

@Serializable
data class BackupReply(
    val id: Long,
    val floor: Int,
    val time: Long,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String,
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
        val imageKey: String? = null,
    ) : BackupContentItem()

    @Serializable
    @SerialName("video")
    data class Video(
        val videoUrl: String,
        val coverUrl: String,
        val webUrl: String,
        val imageKeyCover: String? = null,
    ) : BackupContentItem()
}
