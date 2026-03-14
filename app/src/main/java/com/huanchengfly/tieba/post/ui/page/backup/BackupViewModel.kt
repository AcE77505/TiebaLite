package com.huanchengfly.tieba.post.ui.page.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.UiEvent
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val backups: List<BackupData> = emptyList(),
    val error: Throwable? = null,
) : UiState

sealed interface BackupUiEvent : UiEvent {
    data class Toast(val message: String) : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val backupRepository: BackupRepository,
) : BaseStateViewModel<BackupUiState>() {

    override fun createInitialState(): BackupUiState = BackupUiState()

    /** Flow of the persisted backup directory URI */
    val backupUri: StateFlow<Uri?> = backupRepository.backupUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadBackups()
    }

    fun loadBackups() {
        _uiState.update { it.copy(isLoading = true) }
        launchInVM {
            val backups = backupRepository.listBackups()
            _uiState.update { it.copy(isLoading = false, backups = backups) }
        }
    }

    fun setBackupUri(uri: Uri) {
        viewModelScope.launch {
            backupRepository.setBackupUri(uri)
            loadBackups()
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
