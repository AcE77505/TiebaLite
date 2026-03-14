package com.huanchengfly.tieba.post.ui.page.backup

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.backup.BackupRepository
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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    backup = backup,
                    imagesDir = imagesDir,
                    error = if (backup == null) Exception("Backup not found") else null,
                )
            }
        }
    }
}
