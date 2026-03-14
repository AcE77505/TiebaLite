package com.huanchengfly.tieba.post.ui.page.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.backup.BackupContentItem
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.backup.BackupImageContentRender
import com.huanchengfly.tieba.post.backup.BackupReply
import com.huanchengfly.tieba.post.backup.BackupRepository
import com.huanchengfly.tieba.post.backup.BackupVideoContentRender
import com.huanchengfly.tieba.post.backup.imageKeyOrUrl
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.PicItem
import com.huanchengfly.tieba.post.ui.common.PbContentRender
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.models.LikeZero
import com.huanchengfly.tieba.post.ui.models.PostData
import com.huanchengfly.tieba.post.ui.models.UserData
import com.huanchengfly.tieba.post.ui.page.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

data class BackupViewerUiState(
    val isLoading: Boolean = true,
    val backup: BackupData? = null,
    /** Directory containing extracted images for offline viewing (null if no ZIP or not yet extracted). */
    val imagesDir: File? = null,
    /** [PostData] built from [backup] for rendering with the same [PostCard] as the online viewer. */
    val postData: PostData? = null,
    /** [PostData] list built from [BackupData.replies] for rendering reply cards. */
    val replyDataList: List<PostData> = emptyList(),
    /** Whether the "只看楼主" (see author only) filter is active. */
    val seeLz: Boolean = false,
    val error: Throwable? = null,
) : UiState

@HiltViewModel
class BackupViewerViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    savedStateHandle: SavedStateHandle,
) : BaseStateViewModel<BackupViewerUiState>() {

    private val threadId: Long = savedStateHandle.toRoute<Destination.BackupViewer>().threadId

    override fun createInitialState() = BackupViewerUiState()

    init {
        loadBackup()
    }

    fun loadBackup() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        launchInVM {
            val backup = backupRepository.getBackupByThreadId(threadId)
            // Extract the companion ZIP to a temp cache dir so images can be loaded offline.
            val imagesDir = if (backup != null) {
                backupRepository.extractImagesToCache(threadId)
            } else null
            val postData = backup?.toPostData(imagesDir)
            val replyDataList = backup?.let { b ->
                b.replies.map { reply -> reply.toPostData(imagesDir, lzId = b.authorId) }
            } ?: emptyList()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    backup = backup,
                    imagesDir = imagesDir,
                    postData = postData,
                    replyDataList = replyDataList,
                    error = if (backup == null) Exception("Backup not found") else null,
                )
            }
        }
    }

    fun toggleSeeLz() {
        _uiState.update { it.copy(seeLz = !it.seeLz) }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Converts a [BackupData] into a [PostData] so the backup can be displayed using the same
 * [com.huanchengfly.tieba.post.ui.page.thread.PostCard] composable as the online thread viewer.
 */
private fun BackupData.toPostData(imagesDir: File?): PostData {
    // Resolve author avatar: prefer local file, fall back to network URL.
    val authorAvatarUrl: String = when (
        val m = imageKeyOrUrl(imagesDir, imageKeyAuthorAvatar, authorAvatar)
    ) {
        is File -> m.absolutePath
        is String -> m
        else -> authorAvatar
    }

    val author = UserData(
        id = authorId,
        name = authorName,
        nameShow = authorName,
        showBothName = false,
        avatarUrl = authorAvatarUrl,
        portrait = "",
        ip = "",
        levelId = 0,
        bawuType = null,
        isLz = true,
    )

    // Build a PicItem list for all images in the post so the photo viewer can show the full
    // gallery (correct "N / total" indicator and swipe-between-images).
    val imageItems = contentItems.filterIsInstance<BackupContentItem.Image>()
    val picItems: List<PicItem> = imageItems.mapIndexed { idx, item ->
        val fallback = item.originUrl.takeIf { it.isNotBlank() } ?: item.url
        val url = when (val m = imageKeyOrUrl(imagesDir, item.imageKey, fallback)) {
            is File -> Uri.fromFile(m).toString()
            is String -> m
            else -> item.url
        }
        PicItem(picId = url, picIndex = idx + 1, originUrl = url)
    }

    var imageIdx = 0
    val renders: List<PbContentRender> = contentItems.map { item ->
        when (item) {
            is BackupContentItem.Text -> TextContentRender(item.content)

            is BackupContentItem.Image -> {
                val currentIdx = imageIdx++
                BackupImageContentRender(
                    model = imageKeyOrUrl(
                        imagesDir,
                        item.imageKey,
                        item.originUrl.takeIf { it.isNotBlank() } ?: item.url,
                    ),
                    photoViewData = PhotoViewData(
                        data = null,
                        picItems = picItems,
                        index = currentIdx,
                    ).takeIf { picItems.isNotEmpty() },
                )
            }

            is BackupContentItem.Video -> BackupVideoContentRender(
                coverModel = imageKeyOrUrl(imagesDir, item.imageKeyCover, item.coverUrl),
                videoUrl = item.videoUrl,
                webUrl = item.webUrl,
            )
        }
    }

    val plainText = contentItems
        .filterIsInstance<BackupContentItem.Text>()
        .joinToString("\n") { it.content }

    return PostData(
        id = threadId,
        author = author,
        floor = 1,
        title = title,
        time = backupTime,
        like = LikeZero,
        blocked = false,
        plainText = plainText,
        contentRenders = renders,
        subPosts = null,
        subPostNumber = 0,
    )
}

/**
 * Converts a [BackupReply] into a [PostData] for display in the backup viewer.
 *
 * @param imagesDir  directory containing extracted offline images (may be null)
 * @param lzId       author ID of the original post (used to set [UserData.isLz])
 */
private fun BackupReply.toPostData(imagesDir: File?, lzId: Long): PostData {
    val avatarModel = imageKeyOrUrl(imagesDir, imageKeyAuthorAvatar, authorAvatar)
    val avatarUrl: String = when (avatarModel) {
        is File -> avatarModel.absolutePath
        is String -> avatarModel
        else -> authorAvatar
    }

    val author = UserData(
        id = authorId,
        name = authorName,
        nameShow = authorName,
        showBothName = false,
        avatarUrl = avatarUrl,
        portrait = "",
        ip = "",
        levelId = 0,
        bawuType = null,
        isLz = authorId == lzId,
    )

    // Build per-reply PicItem gallery.
    val imageItems = contentItems.filterIsInstance<BackupContentItem.Image>()
    val picItems: List<PicItem> = imageItems.mapIndexed { idx, item ->
        val fallback = item.originUrl.takeIf { it.isNotBlank() } ?: item.url
        val url = when (val m = imageKeyOrUrl(imagesDir, item.imageKey, fallback)) {
            is File -> Uri.fromFile(m).toString()
            is String -> m
            else -> item.url
        }
        PicItem(picId = url, picIndex = idx + 1, originUrl = url)
    }

    var imageIdx = 0
    val renders: List<PbContentRender> = contentItems.map { item ->
        when (item) {
            is BackupContentItem.Text -> TextContentRender(item.content)

            is BackupContentItem.Image -> {
                val currentIdx = imageIdx++
                BackupImageContentRender(
                    model = imageKeyOrUrl(
                        imagesDir,
                        item.imageKey,
                        item.originUrl.takeIf { it.isNotBlank() } ?: item.url,
                    ),
                    photoViewData = PhotoViewData(
                        data = null,
                        picItems = picItems,
                        index = currentIdx,
                    ).takeIf { picItems.isNotEmpty() },
                )
            }

            is BackupContentItem.Video -> BackupVideoContentRender(
                coverModel = imageKeyOrUrl(imagesDir, item.imageKeyCover, item.coverUrl),
                videoUrl = item.videoUrl,
                webUrl = item.webUrl,
            )
        }
    }

    val plainText = contentItems
        .filterIsInstance<BackupContentItem.Text>()
        .joinToString("\n") { it.content }

    return PostData(
        id = id,
        author = author,
        floor = floor,
        title = null,
        time = time,
        like = LikeZero,
        blocked = false,
        plainText = plainText,
        contentRenders = renders,
        subPosts = null,
        subPostNumber = 0,
    )
}
