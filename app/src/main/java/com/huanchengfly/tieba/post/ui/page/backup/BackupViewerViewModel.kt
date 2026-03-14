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
import javax.inject.Inject

data class BackupViewerUiState(
    val isLoading: Boolean = true,
    val backup: BackupData? = null,
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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    backup = backup,
                    error = if (backup == null) Exception("Backup not found") else null,
                )
            }
        }
    }
}
