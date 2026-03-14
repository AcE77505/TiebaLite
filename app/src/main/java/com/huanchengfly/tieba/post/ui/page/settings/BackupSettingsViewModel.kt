package com.huanchengfly.tieba.post.ui.page.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.backup.BackupRepository
import com.huanchengfly.tieba.post.backup.DuplicateBackupAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val backupUri: StateFlow<Uri?> = backupRepository.backupUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val replyFetchInterval: StateFlow<Long> = backupRepository.replyFetchInterval
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupRepository.DEFAULT_REPLY_FETCH_INTERVAL)

    val autoBackupOwnPosts: StateFlow<Boolean> = backupRepository.autoBackupOwnPosts
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val duplicateBackupAction: StateFlow<DuplicateBackupAction> = backupRepository.duplicateBackupAction
        .stateIn(viewModelScope, SharingStarted.Eagerly, DuplicateBackupAction.ASK)

    fun setBackupUri(uri: Uri) {
        viewModelScope.launch { backupRepository.setBackupUri(uri) }
    }

    fun setReplyFetchInterval(interval: Long) {
        viewModelScope.launch { backupRepository.setReplyFetchInterval(interval) }
    }

    fun setAutoBackupOwnPosts(enabled: Boolean) {
        viewModelScope.launch { backupRepository.setAutoBackupOwnPosts(enabled) }
    }

    fun setDuplicateBackupAction(action: DuplicateBackupAction) {
        viewModelScope.launch { backupRepository.setDuplicateBackupAction(action) }
    }
}
