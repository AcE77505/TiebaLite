package com.huanchengfly.tieba.post.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huanchengfly.tieba.post.utils.GlideUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

private val BACKUP_URI_KEY = stringPreferencesKey("backup_uri")
private val BACKUP_REPLY_INTERVAL_KEY = longPreferencesKey("backup_reply_interval")
private val AUTO_BACKUP_OWN_POSTS_KEY = booleanPreferencesKey("auto_backup_own_posts")
private val DUPLICATE_BACKUP_ACTION_KEY = stringPreferencesKey("duplicate_backup_action")

/** How to handle saving a backup when one already exists for the same thread. */
enum class DuplicateBackupAction { OVERWRITE, KEEP_BOTH, ASK }

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        classDiscriminator = "type"
    }

    /** Flow of the persisted backup directory URI (null if not set). */
    val backupUri: Flow<Uri?> = context.backupDataStore.data
        .map { prefs -> prefs[BACKUP_URI_KEY]?.let { Uri.parse(it) } }

    /** Flow of the reply fetch interval in milliseconds (default: [DEFAULT_REPLY_FETCH_INTERVAL]). */
    val replyFetchInterval: Flow<Long> = context.backupDataStore.data
        .map { prefs -> prefs[BACKUP_REPLY_INTERVAL_KEY] ?: DEFAULT_REPLY_FETCH_INTERVAL }

    /** Flow of whether to automatically backup own posts when their reply count changes. */
    val autoBackupOwnPosts: Flow<Boolean> = context.backupDataStore.data
        .map { prefs -> prefs[AUTO_BACKUP_OWN_POSTS_KEY] ?: false }

    /** Flow of the action to take when a backup already exists for the same thread. */
    val duplicateBackupAction: Flow<DuplicateBackupAction> = context.backupDataStore.data
        .map { prefs ->
            DuplicateBackupAction.entries.firstOrNull {
                it.name == prefs[DUPLICATE_BACKUP_ACTION_KEY]
            } ?: DuplicateBackupAction.ASK
        }

    /** Persist the backup directory URI and request persistent permissions. */
    suspend fun setBackupUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.backupDataStore.edit { it[BACKUP_URI_KEY] = uri.toString() }
    }

    /** Persist the reply fetch interval. [interval] must be ≥ [MIN_REPLY_FETCH_INTERVAL]. */
    suspend fun setReplyFetchInterval(interval: Long) {
        require(interval >= MIN_REPLY_FETCH_INTERVAL)
        context.backupDataStore.edit { it[BACKUP_REPLY_INTERVAL_KEY] = interval }
    }

    /** Persist the auto-backup-own-posts setting. */
    suspend fun setAutoBackupOwnPosts(enabled: Boolean) {
        context.backupDataStore.edit { it[AUTO_BACKUP_OWN_POSTS_KEY] = enabled }
    }

    /** Persist the duplicate backup action. */
    suspend fun setDuplicateBackupAction(action: DuplicateBackupAction) {
        context.backupDataStore.edit { it[DUPLICATE_BACKUP_ACTION_KEY] = action.name }
    }

    /**
     * Check whether any backup for [threadId] already exists.
     * Handles both the legacy `{threadId}.json` / `{threadId}_{ts}.json` naming and the
     * new `{threadId}_m{replyNum}[_{n}].json` / `{threadId}[_{n}].json` naming.
     */
    suspend fun checkExists(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name.endsWith(".json") && isBackupFileForThread(name, threadId)) {
                    return@withContext true
                }
            }
        }
        false
    }

    /**
     * Save a full backup (JSON + image ZIP) to the user-chosen SAF directory.
     *
     * **File naming:**
     * - Own post (isOwnPost=true): base name is `{threadId}_m{replyNum}`
     * - Other post: base name is `{threadId}`
     *
     * When [keepBoth] is true and the primary file `{baseName}.json` already exists, the new
     * file is named `{baseName}_2.json`, `{baseName}_3.json`, … (incrementing until a free slot
     * is found). No timestamp suffix is used.
     *
     * @param overwrite  Replace the existing primary file (`{baseName}.json`).
     * @param keepBoth   Keep the existing file and create a numerically-suffixed duplicate.
     * @param isOwnPost  When true the base name includes `_m{replyNum}`.
     *
     * When neither [overwrite] nor [keepBoth] is true and the primary file exists, the function
     * does nothing (user cancelled). Returns true on success, false when no backup directory is
     * configured.
     */
    suspend fun saveBackup(
        data: BackupData,
        overwrite: Boolean = false,
        keepBoth: Boolean = false,
        isOwnPost: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)

        // Determine base name based on whether this is an own post.
        val baseName = if (isOwnPost) "${data.threadId}_m${data.replyNum}" else "${data.threadId}"

        // Look up existing primary files.
        val existingJsonUri = findDocumentUri(treeUri, "$baseName.json")
        val existingZipUri  = findDocumentUri(treeUri, "$baseName.zip")

        // Determine actual target base name and whether to overwrite an existing URI.
        val targetBaseName: String
        val targetJsonUri: Uri?
        val targetZipUri: Uri?

        when {
            existingJsonUri == null -> {
                // New backup: no existing file with this base name.
                targetBaseName = baseName
                targetJsonUri = null
                targetZipUri = null
            }
            overwrite -> {
                // Overwrite existing primary files.
                targetBaseName = baseName
                targetJsonUri = existingJsonUri
                targetZipUri = existingZipUri
            }
            keepBoth -> {
                // Keep both: find the next available numeric suffix (_2, _3, …).
                var n = 2
                while (findDocumentUri(treeUri, "${baseName}_$n.json") != null) n++
                targetBaseName = "${baseName}_$n"
                targetJsonUri = null
                targetZipUri = null
            }
            else -> return@withContext true  // Cancel: do nothing but report success.
        }

        // 1. Download images and build the ZIP in memory.
        val (dataWithKeys, zipBytes) = downloadAndBuildZip(data)

        // 2. Write ZIP (only if there are images to store).
        if (zipBytes != null) {
            when {
                targetZipUri != null ->
                    context.contentResolver.openOutputStream(targetZipUri, "wt")
                        ?.use { it.write(zipBytes) }
                else ->
                    createAndWriteDoc(treeUri, treeDocId, "$targetBaseName.zip", "application/zip", zipBytes)
            }
        }

        // 3. Write JSON.
        val jsonBytes = json.encodeToString(BackupData.serializer(), dataWithKeys)
            .toByteArray(Charsets.UTF_8)
        when {
            targetJsonUri != null ->
                context.contentResolver.openOutputStream(targetJsonUri, "wt")
                    ?.use { it.write(jsonBytes) }
            else ->
                createAndWriteDoc(treeUri, treeDocId, "$targetBaseName.json", "application/json", jsonBytes)
        }
        true
    }

    /** Return all successfully parsed backup files from the backup directory. */
    suspend fun listBackups(): List<BackupData> = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext emptyList()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val result = mutableListOf<BackupData>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(1)
                val mimeType = cursor.getString(2)
                if (!displayName.endsWith(".json") &&
                    mimeType != "application/json"
                ) continue

                val docId = cursor.getString(0)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                runCatching {
                    context.contentResolver.openInputStream(docUri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        json.decodeFromString<BackupData>(text)
                    }
                }.getOrNull()?.let { result.add(it) }
            }
        }
        result.sortedByDescending { it.backupTime }
    }

    /**
     * Delete a backup identified by [threadId] and [backupTime].
     * Locates the correct file by scanning all `{threadId}*.json` files and matching the
     * [backupTime] field inside the JSON — this handles both the legacy timestamp-based naming
     * and the new `_m{replyNum}` / numeric-suffix naming.
     * Also removes the companion ZIP and cleans up any private viewer cache.
     * Returns true if the JSON file was deleted, false if not found.
     */
    suspend fun deleteBackup(threadId: Long, backupTime: Long): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        var foundDocId: String? = null
        var foundDisplayName: String? = null

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(1)
                if (!displayName.endsWith(".json") || !isBackupFileForThread(displayName, threadId)) continue

                val docId = cursor.getString(0)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val data = runCatching {
                    context.contentResolver.openInputStream(docUri)?.use { stream ->
                        json.decodeFromString<BackupData>(stream.bufferedReader().readText())
                    }
                }.getOrNull()

                if (data?.backupTime == backupTime) {
                    foundDocId = docId
                    foundDisplayName = displayName
                    break
                }
            }
        }

        if (foundDocId == null) return@withContext false

        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundDocId!!)
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, docUri)
        if (deleted) {
            // Delete the companion ZIP (same base name, .zip extension).
            val zipName = foundDisplayName!!.removeSuffix(".json") + ".zip"
            findDocumentUri(treeUri, zipName)?.let { zipUri ->
                DocumentsContract.deleteDocument(context.contentResolver, zipUri)
            }
            // Clean up leftover viewer cache for this thread.
            viewerCacheDir(threadId).deleteRecursively()
        }
        deleted
    }

    /**
     * Read the most-recent [BackupData] for [threadId] by scanning all backup files whose
     * name starts with `{threadId}` and returning the one with the greatest [BackupData.backupTime].
     * Returns null when no backup is found or the directory is not configured.
     */
    suspend fun getBackupByThreadId(threadId: Long): BackupData? = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext null
        findBackupsForThread(threadId, treeUri).maxByOrNull { it.backupTime }
    }

    /**
     * Returns the [BackupData.replyNum] of the most-recent backup for [threadId], or null if no
     * backup exists. Used by the auto-backup feature to detect changes.
     */
    suspend fun getLatestReplyNumForThread(threadId: Long): Int? = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext null
        findBackupsForThread(threadId, treeUri).maxByOrNull { it.backupTime }?.replyNum
    }

    /**
     * Extracts the companion ZIP for [threadId] (from the user-chosen SAF directory) into a
     * temporary viewer cache directory and returns that directory.
     *
     * Returns null when no ZIP is found or extraction fails.
     * Subsequent calls reuse the cache if it is still intact.
     */
    suspend fun extractImagesToCache(threadId: Long): File? = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext null
        val zipUri = findZipUri(treeUri, threadId) ?: return@withContext null

        val cacheDir = viewerCacheDir(threadId)
        // Re-extract only when the cache is empty or stale.
        if (cacheDir.isDirectory && (cacheDir.listFiles()?.isNotEmpty() == true)) {
            return@withContext cacheDir
        }
        cacheDir.mkdirs()

        runCatching {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        // Guard against zip-slip attacks: only allow simple filenames without
                        // path separators, parent references, or other special components.
                        val entryName = entry.name
                        if (entryName.isNotEmpty() &&
                            !entryName.contains('/') &&
                            !entryName.contains('\\') &&
                            !entryName.contains("..") &&
                            !entryName.contains('\u0000')
                        ) {
                            File(cacheDir, entryName).outputStream().use { out ->
                                zip.copyTo(out)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            cacheDir
        }.getOrNull()
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Downloads all images referenced in [data] and packs them into an in-memory ZIP.
     *
     * Only the highest-quality image is stored per content item (originUrl preferred for images).
     * Individual download failures are silently ignored.
     *
     * @return The updated [BackupData] (with `imageKey*` fields populated) and the ZIP bytes
     *         (null when no images were downloaded).
     */
    private suspend fun downloadAndBuildZip(data: BackupData): Pair<BackupData, ByteArray?> {
        // key → Glide-cached File
        val imageEntries = linkedMapOf<String, File>()

        /** Downloads [url] into [imageEntries] under [key] and returns the key, or null on failure. */
        suspend fun tryDownload(key: String, url: String?): String? =
            url?.takeIf { it.isNotBlank() }?.let { u ->
                runCatching {
                    imageEntries[key] = GlideUtil.downloadCancelable(context, u, null)
                    key
                }.getOrNull()
            }

        val imageKeyForumAvatar = tryDownload("forum_avatar", data.forumAvatar)
        val imageKeyAuthorAvatar = tryDownload("author_avatar", data.authorAvatar)

        val updatedItems = data.contentItems.mapIndexed { index, item ->
            when (item) {
                is BackupContentItem.Image -> {
                    // Prefer the highest-quality URL; only one image per content item is stored.
                    val effectiveUrl = item.originUrl.takeIf { it.isNotBlank() } ?: item.url
                    item.copy(imageKey = tryDownload("img_$index", effectiveUrl))
                }
                is BackupContentItem.Video ->
                    item.copy(imageKeyCover = tryDownload("vid_${index}_cover", item.coverUrl))
                else -> item
            }
        }

        // Download reply author avatars and reply content images.
        val updatedReplies = data.replies.mapIndexed { replyIndex, reply ->
            val replyAvatarKey = tryDownload("reply_${replyIndex}_author_avatar", reply.authorAvatar)
            val updatedReplyItems = reply.contentItems.mapIndexed { itemIndex, item ->
                when (item) {
                    is BackupContentItem.Image -> {
                        val effectiveUrl = item.originUrl.takeIf { it.isNotBlank() } ?: item.url
                        item.copy(imageKey = tryDownload("reply_${replyIndex}_img_$itemIndex", effectiveUrl))
                    }
                    is BackupContentItem.Video ->
                        item.copy(imageKeyCover = tryDownload("reply_${replyIndex}_vid_${itemIndex}_cover", item.coverUrl))
                    else -> item
                }
            }
            reply.copy(imageKeyAuthorAvatar = replyAvatarKey, contentItems = updatedReplyItems)
        }

        val updatedData = data.copy(
            imageKeyForumAvatar = imageKeyForumAvatar,
            imageKeyAuthorAvatar = imageKeyAuthorAvatar,
            contentItems = updatedItems,
            replies = updatedReplies,
        )

        if (imageEntries.isEmpty()) return updatedData to null

        val zipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                // Use STORED (no compression) to avoid the CPU cost of DEFLATE.
                zip.setMethod(ZipOutputStream.STORED)
                for ((key, file) in imageEntries) {
                    val bytes = file.readBytes()
                    val crc = CRC32().also { it.update(bytes) }.value
                    val entry = ZipEntry(key).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        return updatedData to zipBytes
    }

    /** Creates a new document in [treeUri] and writes [bytes] to it. */
    private fun createAndWriteDoc(
        treeUri: Uri,
        treeDocId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val newDocUri = DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
            mimeType,
            fileName,
        ) ?: return
        context.contentResolver.openOutputStream(newDocUri)?.use { os ->
            os.write(bytes)
        }
    }

    /**
     * Returns the URI of a document named [fileName] in the top-level of [treeUri],
     * or null if it does not exist.
     */
    private fun findDocumentUri(treeUri: Uri, fileName: String): Uri? {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == fileName) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, cursor.getString(0)
                    )
                }
            }
        }
        return null
    }

    /**
     * Finds the ZIP file for [threadId] in [treeUri].
     * Prefers the exact name `{threadId}.zip`; falls back to any `{threadId}[_.].*.zip`.
     */
    private fun findZipUri(treeUri: Uri, threadId: Long): Uri? {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        var exactMatch: Uri? = null
        var prefixMatch: Uri? = null
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, cursor.getString(0)
                )
                when {
                    name == "$threadId.zip" -> exactMatch = docUri
                    name.endsWith(".zip") && isBackupFileForThread(name.removeSuffix(".zip") + ".json", threadId) ->
                        if (prefixMatch == null) prefixMatch = docUri
                }
            }
        }
        return exactMatch ?: prefixMatch
    }

    /**
     * Reads all backup JSON files for [threadId] from [treeUri] and returns their parsed
     * [BackupData] objects.  Handles all naming schemes (legacy and new).
     */
    private fun findBackupsForThread(threadId: Long, treeUri: Uri): List<BackupData> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val result = mutableListOf<BackupData>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(1)
                if (!displayName.endsWith(".json") || !isBackupFileForThread(displayName, threadId)) continue

                val docId = cursor.getString(0)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                runCatching {
                    context.contentResolver.openInputStream(docUri)?.use { stream ->
                        json.decodeFromString<BackupData>(stream.bufferedReader().readText())
                    }
                }.getOrNull()?.takeIf { it.threadId == threadId }?.let { result.add(it) }
            }
        }
        return result
    }

    /**
     * Returns true when [fileName] (a `.json` filename) belongs to a backup for [threadId].
     * Matches:
     *   - `{threadId}.json`             (primary, non-own-post)
     *   - `{threadId}_{anything}.json`  (legacy timestamp or new numeric suffix for non-own-post)
     *   - `{threadId}_m{n}.json`        (primary, own-post)
     *   - `{threadId}_m{n}_{k}.json`    (numeric duplicate, own-post)
     */
    private fun isBackupFileForThread(fileName: String, threadId: Long): Boolean {
        val prefix = threadId.toString()
        return fileName == "$prefix.json" || fileName.startsWith("${prefix}_")
    }

    /** Returns the viewer cache directory for [threadId] (may not yet exist). */
    private fun viewerCacheDir(threadId: Long) =
        File(context.cacheDir, "backup_viewer/$threadId")

    companion object {
        const val DEFAULT_REPLY_FETCH_INTERVAL = 1500L
        const val MIN_REPLY_FETCH_INTERVAL = 500L
    }
}
