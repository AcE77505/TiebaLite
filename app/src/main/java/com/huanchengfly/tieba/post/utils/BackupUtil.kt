package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.github.panpf.sketch.request.DownloadRequest
import com.github.panpf.sketch.request.DownloadResult
import com.github.panpf.sketch.request.execute
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.repository.PbPageRepository
import com.huanchengfly.tieba.post.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Threshold: show progress dialog when totalPage exceeds this value. */
const val BACKUP_PROGRESS_DIALOG_THRESHOLD = 1

// ──────────────────────────── JSON data classes ──────────────────────────────

data class BackupUserInfo(
    val id: String,
    val name: String,
    val display_name: String,
    val avatar_filename: String?,
)

data class BackupContentItem(
    val type: String,       // "text" | "image" | "emoticon" | "video" | "link" | "voice"
    val text: String? = null,
    val filename: String? = null,
    val url: String? = null,
)

data class BackupReply(
    val id: String,
    val floor: Int,
    val time: Long,
    val author: BackupUserInfo,
    val text: String,
)

data class BackupPost(
    val id: String,
    val floor: Int,
    val time: Long,
    val author: BackupUserInfo,
    val content: List<BackupContentItem>,
    val replies: List<BackupReply>,
)

data class BackupData(
    val thread_id: Long,
    val title: String,
    val url: String,
    val backup_time: Long,
    val author: BackupUserInfo,
    val posts: List<BackupPost>,
)

// ──────────────────────────── Progress callback ───────────────────────────────

sealed interface BackupProgress {
    data class FetchingPage(val current: Int, val total: Int) : BackupProgress
    data class DownloadingImages(val current: Int, val total: Int) : BackupProgress
    data class DownloadingVideos(val current: Int, val total: Int) : BackupProgress
    data object Saving : BackupProgress
}

// ──────────────────────────── BackupUtil ──────────────────────────────────────

object BackupUtil {

    fun isBackupPathConfigured(context: Context): Boolean =
        !context.appPreferences.backupPath.isNullOrEmpty()

    fun getBackupPathUri(context: Context): Uri? =
        context.appPreferences.backupPath?.let { Uri.parse(it) }

    /**
     * Backup a thread.
     *
     * @param threadId   Thread (贴子) ID
     * @param saveVideos When true, video files are downloaded and saved alongside the JSON
     * @param onProgress Progress callback invoked on the calling coroutine dispatcher
     * @throws Exception on any failure
     */
    suspend fun backupThread(
        context: Context,
        threadId: Long,
        saveVideos: Boolean = false,
        onProgress: suspend (BackupProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // ── 1. Determine backup directory ─────────────────────────────────────
        val treeUri = getBackupPathUri(context)
            ?: error("Backup path not configured")
        val treeDir = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot open backup directory")

        // ── 2. Fetch first page to get totalPage / title / author ─────────────
        val firstResponse = PbPageRepository.pbPage(threadId = threadId, page = 1).first()
        val data_ = firstResponse.data_ ?: error("Empty response")
        val totalPage = data_.page?.new_total_page ?: 1
        val threadInfo = data_.thread ?: error("No thread info")
        val title = threadInfo.title.orEmpty()
        val threadAuthor = threadInfo.author ?: User()

        // ── 3. Collect posts from all pages ───────────────────────────────────
        val allPosts = mutableListOf<Post>()
        // Collect posts from the already-fetched first page
        allPosts += data_.post_list.filter { it.floor != 1 }
        data_.first_floor_post?.let { first ->
            if (allPosts.none { it.floor == 1 }) allPosts.add(0, first)
        }

        onProgress(BackupProgress.FetchingPage(1, totalPage))

        for (page in 2..totalPage) {
            onProgress(BackupProgress.FetchingPage(page, totalPage))
            val resp = PbPageRepository.pbPage(threadId = threadId, page = page).first()
            val posts = resp.data_?.post_list?.filter { it.floor != 1 } ?: emptyList()
            allPosts += posts
        }

        // ── 4. Build avatar and image URL maps ────────────────────────────────
        // key = url, value = filename within zip (images/<filename>)
        val imageUrlToFilename = linkedMapOf<String, String>()
        // Ordered list of video URLs found in the thread (in reading order)
        val videoUrls = mutableListOf<String>()

        fun avatarFilename(user: User?): String? {
            val portrait = user?.portrait ?: return null
            if (portrait.isBlank()) return null
            val url = StringUtil.getBigAvatarUrl(portrait)
            return imageUrlToFilename.getOrPut(url) {
                "avatar_${user.id}.jpg"
            }
        }

        // ── 5. Build JSON model ───────────────────────────────────────────────
        val authorInfo = threadAuthor.toBackupUser { avatarFilename(threadAuthor) }

        val backupPosts = allPosts.map { post ->
            val postAuthor = post.author ?: User()
            val postAuthorInfo = postAuthor.toBackupUser { avatarFilename(postAuthor) }

            val contentItems = post.content.map { c ->
                val item = c.toBackupContent(imageUrlToFilename)
                if (item.type == "video" && !item.url.isNullOrBlank()) {
                    videoUrls.add(item.url)
                }
                item
            }

            val replies = post.sub_post_list?.sub_post_list?.map { sub ->
                val subAuthor = sub.author ?: User()
                BackupReply(
                    id = sub.id.toString(),
                    floor = sub.floor,
                    time = sub.time.toLong(),
                    author = subAuthor.toBackupUser { avatarFilename(subAuthor) },
                    text = sub.plainText(),
                )
            } ?: emptyList()

            BackupPost(
                id = post.id.toString(),
                floor = post.floor,
                time = post.time.toLong(),
                author = postAuthorInfo,
                content = contentItems,
                replies = replies,
            )
        }.sortedBy { it.floor }

        val backupTime = System.currentTimeMillis() / 1000L
        val basename = "${threadId}_$backupTime"
        val backupData = BackupData(
            thread_id = threadId,
            title = title,
            backup_time = backupTime,
            url = "https://tieba.baidu.com/p/$threadId",
            author = authorInfo,
            posts = backupPosts,
        )

        // ── 6. Download images ────────────────────────────────────────────────
        // key = filename within zip, value = raw bytes
        val imageBytes = linkedMapOf<String, ByteArray>()
        val imageEntries = imageUrlToFilename.entries.toList()
        for ((index, entry) in imageEntries.withIndex()) {
            onProgress(BackupProgress.DownloadingImages(index + 1, imageEntries.size))
            val (url, filename) = entry
            try {
                val result = DownloadRequest(context, url).execute()
                if (result is DownloadResult.Success) {
                    imageBytes[filename] = result.data.data.newInputStream().use { it.readBytes() }
                }
            } catch (_: Exception) {
                // Skip images that fail to download
            }
        }

        // ── 6b. Download videos (if saveVideos is enabled) ───────────────────
        // video files are saved directly in the backup directory as
        // <basename>_video_<N>.<ext>  (collected for writing after basename is known)
        val videoFileData = mutableListOf<Pair<String, ByteArray>>() // filename -> bytes
        if (saveVideos && videoUrls.isNotEmpty()) {
            val uniqueVideoUrls = videoUrls.distinct()
            for ((index, url) in uniqueVideoUrls.withIndex()) {
                onProgress(BackupProgress.DownloadingVideos(index + 1, uniqueVideoUrls.size))
                try {
                    val result = DownloadRequest(context, url).execute()
                    if (result is DownloadResult.Success) {
                        val ext = url.substringAfterLast('.').substringBefore('?')
                            .lowercase().let { if (it.length in 2..4) it else "mp4" }
                        val videoFilename = "${basename}_video_${index + 1}.$ext"
                        val bytes = result.data.data.newInputStream().use { it.readBytes() }
                        videoFileData.add(videoFilename to bytes)
                    }
                } catch (_: Exception) {
                    // Skip videos that fail to download
                }
            }
        }

        onProgress(BackupProgress.Saving)

        // ── 7. Save JSON ──────────────────────────────────────────────────────
        val jsonFilename = "$basename.json"
        val jsonBytes = backupData.toJson().toByteArray(Charsets.UTF_8)
        val existingJson = treeDir.findFile(jsonFilename)
        existingJson?.delete()
        val jsonFile = treeDir.createFile("application/json", jsonFilename)
            ?: error("Cannot create JSON file in backup directory")
        context.contentResolver.openOutputStream(jsonFile.uri)?.use { it.write(jsonBytes) }
            ?: error("Cannot write JSON file")

        // ── 8. Save ZIP (STORED – no compression) ────────────────────────────
        if (imageBytes.isNotEmpty()) {
            val zipFilename = "$basename.zip"
            val existingZip = treeDir.findFile(zipFilename)
            existingZip?.delete()
            val zipFile = treeDir.createFile("application/zip", zipFilename)
                ?: error("Cannot create ZIP file in backup directory")
            context.contentResolver.openOutputStream(zipFile.uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    zos.setMethod(ZipOutputStream.STORED)
                    for ((filename, bytes) in imageBytes) {
                        val entry = ZipEntry("images/$filename")
                        entry.method = ZipEntry.STORED
                        entry.size = bytes.size.toLong()
                        val crc = CRC32().apply { update(bytes) }
                        entry.crc = crc.value
                        entry.compressedSize = bytes.size.toLong()
                        zos.putNextEntry(entry)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            } ?: error("Cannot write ZIP file")
        }

        // ── 9. Save video files ───────────────────────────────────────────────
        for ((videoFilename, bytes) in videoFileData) {
            val mime = "video/${videoFilename.substringAfterLast('.')}"
            val existingVideo = treeDir.findFile(videoFilename)
            existingVideo?.delete()
            val videoFile = treeDir.createFile(mime, videoFilename) ?: continue
            context.contentResolver.openOutputStream(videoFile.uri)?.use { it.write(bytes) }
        }
    }

    // ──────────────────────────── Helpers ────────────────────────────────────

    private fun User.toBackupUser(avatarFilename: () -> String?): BackupUserInfo =
        BackupUserInfo(
            id = id.toString(),
            name = name.orEmpty(),
            display_name = (nameShow ?: name).orEmpty(),
            avatar_filename = avatarFilename(),
        )

    private fun PbContent.toBackupContent(imageUrlToFilename: MutableMap<String, String>): BackupContentItem =
        when (type) {
            3, 20 -> {
                // Image: prefer originSrc for highest quality
                val url = if (originSrc.isNotBlank()) originSrc
                else if (bigCdnSrc.isNotBlank()) bigCdnSrc
                else if (bigSrc.isNotBlank()) bigSrc
                else src
                val picId = ImageUtil.getPicId(url).ifBlank { url.hashCode().toString() }
                val filename = imageUrlToFilename.getOrPut(url) { "img_$picId.jpg" }
                BackupContentItem(type = "image", filename = filename, url = url)
            }
            1 -> BackupContentItem(type = "link", text = text, url = link)
            2 -> BackupContentItem(type = "emoticon", text = "#($c)")
            5 -> BackupContentItem(type = "video", url = link.ifBlank { text })
            10 -> BackupContentItem(type = "voice")
            else -> BackupContentItem(type = "text", text = text)
        }

    private fun SubPostList.plainText(): String =
        content.joinToString("") { c ->
            when (c.type) {
                0, 1, 4, 9, 27, 35, 40 -> c.text
                2 -> "#(${c.c})"
                else -> ""
            }
        }
}

// ──────────────────────────── DocumentFile listing ───────────────────────────

/** Returns a list of backup json files in the configured backup directory. */
fun Context.listBackupFiles(): List<DocumentFile> {
    val uri = appPreferences.backupPath?.let { Uri.parse(it) } ?: return emptyList()
    val dir = DocumentFile.fromTreeUri(this, uri) ?: return emptyList()
    return dir.listFiles()
        .filter { it.isFile && it.name?.endsWith(".json") == true }
        .sortedByDescending { it.lastModified() }
}

fun Context.isBackupPathConfigured(): Boolean =
    !appPreferences.backupPath.isNullOrEmpty()
