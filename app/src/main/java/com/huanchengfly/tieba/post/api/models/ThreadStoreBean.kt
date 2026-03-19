package com.huanchengfly.tieba.post.api.models

import com.google.gson.annotations.SerializedName
import com.huanchengfly.tieba.post.models.BaseBean
import javax.annotation.concurrent.Immutable

data class ThreadStoreBean(
    @SerializedName("error_code")
    val errorCode: String? = null,
    val error: ErrorInfo? = null,
    @SerializedName("store_thread")
    val storeThread: List<ThreadStoreInfo>? = null
) : BaseBean() {
    @Immutable
    data class ThreadStoreInfo(
        @SerializedName("thread_id")
        val threadId: Long,
        val title: String? = null,
        @SerializedName("forum_name")
        val forumName: String? = null,
        val author: AuthorInfo? = null,
        val media: List<MediaInfo>? = null,
        @SerializedName("is_deleted")
        val isDeleted: Int,
        @SerializedName("last_time")
        val lastTime: String? = null,
        val type: String? = null,
        val status: String? = null,
        @SerializedName("max_pid")
        val maxPid: String? = null,
        @SerializedName("min_pid")
        val minPid: String? = null,
        @SerializedName("mark_pid")
        val markPid: String? = null,
        @SerializedName("mark_status")
        val markStatus: String? = null,
        @SerializedName("post_no")
        val postNo: Int,
        @SerializedName("post_no_msg")
        val postNoMsg: String? = null,
        val count: Int
    ) : BaseBean()

    data class MediaInfo(
        val type: String? = null,
        @SerializedName("small_Pic")
        val smallPic: String? = null,
        @SerializedName("big_pic")
        val bigPic: String? = null,
        val width: String? = null,
        val height: String? = null
    ) : BaseBean()

    data class AuthorInfo(
        @SerializedName("lz_uid")
        val lzUid: Long? = null,
        val name: String? = null,
        @SerializedName("name_show")
        val nameShow: String? = null,
        @SerializedName("user_portrait")
        val userPortrait: String? = null

    ) : BaseBean()

    data class ErrorInfo(
        @SerializedName("errno")
        val errorCode: String? = null,
        @SerializedName("errmsg")
        val errorMsg: String? = null
    ) : BaseBean()
}
