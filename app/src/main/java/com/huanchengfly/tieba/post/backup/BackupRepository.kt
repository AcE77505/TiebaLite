package com.huanchengfly.tieba.post.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
        findExactBackupUri(treeUri, threadId) != null
    }

    /**
     * Save a backup.
     *
     * @param overwrite Replace the existing `{threadId}.json` file.
     * @param keepBoth  Create a new file named `{threadId}_{backupTime}.json` alongside the existing one.
     *
     * When neither [overwrite] nor [keepBoth] is true the function does nothing (user chose cancel).
     * Returns true on success, false when no backup directory is configured.
     */
    suspend fun saveBackup(
        data: BackupData,
        overwrite: Boolean = false,
        keepBoth: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val jsonBytes = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)

        val existingUri = findExactBackupUri(treeUri, data.threadId)

        when {
            existingUri == null -> {
                // No duplicate – create a new file
                createAndWrite(treeUri, treeDocId, "${data.threadId}.json", jsonBytes)
            }
            overwrite -> {
                // Truncate and overwrite the existing file
                context.contentResolver.openOutputStream(existingUri, "wt")?.use { os ->
                    os.write(jsonBytes)
                }
            }
            keepBoth -> {
                // Keep the old file and create a new one with a timestamp suffix
                createAndWrite(treeUri, treeDocId, "${data.threadId}_${data.backupTime}.json", jsonBytes)
            }
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
     * Returns true if deleted, false if not found.
     */
    suspend fun deleteBackup(threadId: Long, backupTime: Long): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupUri.first() ?: return@withContext false
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
                val displayName = cursor.getString(1)
                // Match both single-backup name and timestamped name
                if (displayName == "${threadId}.json" || displayName == "${threadId}_${backupTime}.json") {
                    val docId = cursor.getString(0)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    return@withContext DocumentsContract.deleteDocument(context.contentResolver, docUri)
                }
            }
        }
        false
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun findExactBackupUri(treeUri: Uri, threadId: Long): Uri? {
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
                val displayName = cursor.getString(1)
                if (displayName == "${threadId}.json") {
                    val docId = cursor.getString(0)
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }
            }
        }
        return null
    }

    private fun createAndWrite(treeUri: Uri, treeDocId: String, fileName: String, bytes: ByteArray) {
        val newDocUri = DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
            "application/json",
            fileName,
        ) ?: return
        context.contentResolver.openOutputStream(newDocUri)?.use { os ->
            os.write(bytes)
        }
    }
}
