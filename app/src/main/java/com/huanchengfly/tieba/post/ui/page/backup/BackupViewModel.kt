package com.huanchengfly.tieba.post.ui.page.backup

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.UiEvent
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val backups: List<BackupData> = emptyList(),
    val error: Throwable? = null,
) : UiState

sealed interface BackupUiEvent : UiEvent {
    data class Toast(val message: String) : BackupUiEvent
    data object BackupPathNotSet : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val backupRepository: BackupRepository,
) : BaseStateViewModel<BackupUiState>() {

    override fun createInitialState(): BackupUiState = BackupUiState()

    init {
        checkBackupPath()
        loadBackups()
    }

    private fun checkBackupPath() {
        launchInVM {
            if (backupRepository.backupUri.first() == null) {
                sendUiEvent(BackupUiEvent.BackupPathNotSet)
            }
        }
    }

    fun loadBackups() {
        _uiState.update { it.copy(isLoading = true) }
        launchInVM {
            val backups = backupRepository.listBackups()
            _uiState.update { it.copy(isLoading = false, backups = backups) }
        }
    }

    fun deleteBackup(backup: BackupData) {
        launchInVM {
            backupRepository.deleteBackup(backup.threadId, backup.backupTime)
            loadBackups()
            sendUiEvent(BackupUiEvent.Toast(context.getString(R.string.toast_delete_success)))
        }
    }
}

