package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.PageData
import com.huanchengfly.tieba.post.ui.models.PostData
import com.huanchengfly.tieba.post.ui.models.SimpleForum
import com.huanchengfly.tieba.post.ui.models.ThreadInfoData
import com.huanchengfly.tieba.post.ui.models.UserData

/**
 * Progress state for the multi-page reply backup operation.
 *
 * @param currentPage the last page that was successfully fetched
 * @param totalPage   the total number of reply pages
 * @param isPaused    whether the fetching is currently paused by the user
 */
@Stable
data class BackupProgressState(
    val currentPage: Int,
    val totalPage: Int,
    val isPaused: Boolean = false,
)

@Immutable
data class ThreadUiState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingLatestReply: Boolean = false,
    val error: Throwable? = null,
    val seeLz: Boolean = false,
    @ThreadSortType val sortType: Int = ThreadSortType.DEFAULT,
    val user: UserData? = null,
    val firstPost: PostData? = null,
    val thread: ThreadInfoData? = null,
    val tbs: String? = null,
    val data: List<PostData> = emptyList(),
    val latestPosts: List<PostData>? = null,
    val pageData: PageData = PageData(),
    /** Non-null while a multi-page backup operation is in progress. */
    val backupProgress: BackupProgressState? = null,
) : UiState {

    val lz: UserData?
        get() = firstPost?.author

    val forum: SimpleForum?
        get() = thread?.simpleForum
}
