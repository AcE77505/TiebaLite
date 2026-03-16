package com.huanchengfly.tieba.post.api.models

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.huanchengfly.tieba.post.api.adapters.MessageListAdapter
import com.huanchengfly.tieba.post.api.adapters.PortraitAdapter
import com.huanchengfly.tieba.post.models.BaseBean

class MessageListBean : BaseBean() {
    @SerializedName("error_code")
    val errorCode: String? = null

    @SerializedName("time")
    val time: Long = 0

    @JsonAdapter(MessageListAdapter::class)
    @SerializedName("reply_list")
    val replyList: List<MessageInfoBean>? = null

    @JsonAdapter(MessageListAdapter::class)
    @SerializedName("at_list")
    val atList: List<MessageInfoBean>? = null

    @SerializedName("page")
    val page: PageInfoBean? = null

    @SerializedName("message")
    val message: MessageBean? = null

    fun getErrorCode(): Int = Integer.valueOf(errorCode!!)

    data class UserInfoBean(
        @SerializedName("id")
        val id: String? = null,

        @SerializedName("name")
        val name: String? = null,

        @SerializedName("name_show")
        val nameShow: String? = null,

        @JsonAdapter(PortraitAdapter::class)
        @SerializedName("portrait")
        val portrait: String? = null,
    )

    data class ReplyerInfoBean(
        @SerializedName("id")
        val id: String? = null,

        @SerializedName("name")
        val name: String? = null,

        @SerializedName("name_show")
        val nameShow: String? = null,

        @JsonAdapter(PortraitAdapter::class)
        @SerializedName("portrait")
        val portrait: String? = null,

        @SerializedName("is_friend")
        val isFriend: String? = null,

        @SerializedName("is_fans")
        val isFans: String? = null,
    )

    data class MessageInfoBean(
        @SerializedName("is_floor")
        val isFloor: String? = null,

        @SerializedName("title")
        val title: String? = null,

        @SerializedName("content")
        val content: String? = null,

        @SerializedName("quote_content")
        //有时候会引用的回复楼，有时候引用的楼中楼
        val quoteContent: String? = null,

        @SerializedName("replyer")
        val replyer: ReplyerInfoBean? = null,

        @SerializedName("quote_user")
        val quoteUser: UserInfoBean? = null,

        @SerializedName("thread_id")
        val threadId: String? = null,

        @SerializedName("post_id")
        val postId: String? = null,

        @SerializedName("time")
        val time: String? = null,

        @SerializedName("fname")
        val forumName: String? = null,

        @SerializedName("quote_pid")
        val quotePid: String? = null,

        @SerializedName("thread_type")
        val threadType: String? = null,

        @SerializedName("unread")
        val unread: String? = null,
    )

    class MessageBean {
        @SerializedName("replyme")
        val replyMe: String? = null

        @SerializedName("atme")
        val atMe: String? = null

        @SerializedName("fans")
        val fans: String? = null

        @SerializedName("recycle")
        val recycle: String? = null

        @SerializedName("storethread")
        val storeThread: String? = null

    }

    class PageInfoBean {
        @SerializedName("current_page")
        val currentPage: String? = null

        @SerializedName("has_more")
        val hasMore: String? = null

        @SerializedName("has_prev")
        val hasPrev: String? = null
    }
}