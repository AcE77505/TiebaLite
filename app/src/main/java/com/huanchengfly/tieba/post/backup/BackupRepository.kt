package com.huanchengfly.tieba.post.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

private val BACKUP_URI_KEY = stringPreferencesKey("backup_uri")

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

    /** Persist the backup directory URI and request persistent permissions. */
    suspend fun setBackupUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.backupDataStore.edit { it[BACKUP_URI_KEY] = uri.toString() }
    }

    /**
     * Check whether a backup with [threadId] already exists (exact filename `{threadId}.json`).
     */
    suspend fun checkExists(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        findDocumentUri(treeUri, "${threadId}.json") != null
    }

    /**
     * Save a full backup (JSON + image ZIP) to the user-chosen SAF directory.
     *
     * This function downloads all images, packs them into a ZIP file named `{baseName}.zip`,
     * and writes the updated [BackupData] as `{baseName}.json` — where `baseName` is
     * `{threadId}` for a new backup and `{threadId}_{backupTime}` when [keepBoth] is true.
     *
     * @param overwrite Replace the existing `{threadId}.json` and `{threadId}.zip`.
     * @param keepBoth  Create timestamped files alongside the existing ones.
     *
     * When neither [overwrite] nor [keepBoth] is true the function does nothing.
     * Returns true on success, false when no backup directory is configured.
     */
    suspend fun saveBackup(
        data: BackupData,
        overwrite: Boolean = false,
        keepBoth: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)

        val suffix = if (keepBoth) "_${data.backupTime}" else ""
        val baseName = "${data.threadId}$suffix"

        // For the non-keepBoth case, look up existing files to overwrite.
        val existingJsonUri = if (suffix.isEmpty()) findDocumentUri(treeUri, "$baseName.json") else null
        val existingZipUri  = if (suffix.isEmpty()) findDocumentUri(treeUri, "$baseName.zip")  else null

        // 1. Download images and build the ZIP in memory.
        val (dataWithKeys, zipBytes) = downloadAndBuildZip(data)

        // 2. Write ZIP (only if there are images to store).
        if (zipBytes != null) {
            when {
                existingZipUri != null && overwrite ->
                    context.contentResolver.openOutputStream(existingZipUri, "wt")
                        ?.use { it.write(zipBytes) }
                else ->
                    createAndWriteDoc(treeUri, treeDocId, "$baseName.zip", "application/zip", zipBytes)
            }
        }

        // 3. Write JSON.
        val jsonBytes = json.encodeToString(BackupData.serializer(), dataWithKeys)
            .toByteArray(Charsets.UTF_8)
        when {
            existingJsonUri == null ->
                createAndWriteDoc(treeUri, treeDocId, "$baseName.json", "application/json", jsonBytes)
            overwrite ->
                context.contentResolver.openOutputStream(existingJsonUri, "wt")
                    ?.use { it.write(jsonBytes) }
            keepBoth ->
                createAndWriteDoc(treeUri, treeDocId, "$baseName.json", "application/json", jsonBytes)
            // else: user cancelled – do nothing
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
     * Removes both the JSON file and its companion ZIP (same base name).
     * Also cleans up any leftover private cache/files for this thread.
     * Returns true if the JSON file was deleted, false if not found.
     */
    suspend fun deleteBackup(threadId: Long, backupTime: Long): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val jsonName1 = "$threadId.json"
        val jsonName2 = "${threadId}_$backupTime.json"

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
                if (displayName != jsonName1 && displayName != jsonName2) continue

                val docId = cursor.getString(0)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, docUri)
                if (deleted) {
                    // Delete the companion ZIP (same base name, .zip extension).
                    val zipName = displayName.removeSuffix(".json") + ".zip"
                    findDocumentUri(treeUri, zipName)?.let { zipUri ->
                        DocumentsContract.deleteDocument(context.contentResolver, zipUri)
                    }
                    // Clean up leftover viewer cache for this thread.
                    viewerCacheDir(threadId).deleteRecursively()
                }
                return@withContext deleted
            }
        }
        false
    }

    /**
     * Read the primary [BackupData] for [threadId] from the JSON file (`{threadId}.json`).
     * Returns null when no backup is found or the directory is not configured.
     */
    suspend fun getBackupByThreadId(threadId: Long): BackupData? = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext null
        val jsonUri = findDocumentUri(treeUri, "$threadId.json") ?: return@withContext null
        runCatching {
            context.contentResolver.openInputStream(jsonUri)?.use { stream ->
                json.decodeFromString<BackupData>(stream.bufferedReader().readText())
            }
        }.getOrNull()
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

        val imageKeyForumAvatar = data.forumAvatar?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                val key = "forum_avatar"
                imageEntries[key] = GlideUtil.downloadCancelable(context, url, null)
                key
            }.getOrNull()
        }

        val imageKeyAuthorAvatar = data.authorAvatar.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                val key = "author_avatar"
                imageEntries[key] = GlideUtil.downloadCancelable(context, url, null)
                key
            }.getOrNull()
        }

        val updatedItems = data.contentItems.mapIndexed { index, item ->
            when (item) {
                is BackupContentItem.Image -> {
                    // Prefer the highest-quality URL; only one image per content item is stored.
                    val effectiveUrl = item.originUrl.takeIf { it.isNotBlank() } ?: item.url
                    val imageKey = effectiveUrl.takeIf { it.isNotBlank() }?.let { url ->
                        runCatching {
                            val key = "img_$index"
                            imageEntries[key] = GlideUtil.downloadCancelable(context, url, null)
                            key
                        }.getOrNull()
                    }
                    item.copy(imageKey = imageKey)
                }
                is BackupContentItem.Video -> {
                    val imageKeyCover = item.coverUrl.takeIf { it.isNotBlank() }?.let { url ->
                        runCatching {
                            val key = "vid_${index}_cover"
                            imageEntries[key] = GlideUtil.downloadCancelable(context, url, null)
                            key
                        }.getOrNull()
                    }
                    item.copy(imageKeyCover = imageKeyCover)
                }
                else -> item
            }
        }

        val updatedData = data.copy(
            imageKeyForumAvatar = imageKeyForumAvatar,
            imageKeyAuthorAvatar = imageKeyAuthorAvatar,
            contentItems = updatedItems,
        )

        if (imageEntries.isEmpty()) return updatedData to null

        val zipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                for ((key, file) in imageEntries) {
                    zip.putNextEntry(ZipEntry(key))
                    file.inputStream().use { it.copyTo(zip) }
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
     * Prefers the exact name `{threadId}.zip`; falls back to any `{threadId}_*.zip`.
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
                    name.startsWith("$threadId") && name.endsWith(".zip") ->
                        if (prefixMatch == null) prefixMatch = docUri
                }
            }
        }
        return exactMatch ?: prefixMatch
    }

    /** Returns the viewer cache directory for [threadId] (may not yet exist). */
    private fun viewerCacheDir(threadId: Long) =
        File(context.cacheDir, "backup_viewer/$threadId")
}
