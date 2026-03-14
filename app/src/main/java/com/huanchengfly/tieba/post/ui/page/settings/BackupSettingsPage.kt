package com.huanchengfly.tieba.post.ui.page.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.backup.BackupRepository
import com.huanchengfly.tieba.post.backup.DuplicateBackupAction
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.preference.ListPref
import com.huanchengfly.tieba.post.ui.widgets.compose.preference.SwitchPref
import com.huanchengfly.tieba.post.ui.widgets.compose.preference.TextPref
import com.huanchengfly.tieba.post.ui.widgets.compose.preference.TextPrefsScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun BackupSettingsPage(
    onBack: () -> Unit,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val backupUri by viewModel.backupUri.collectAsStateWithLifecycle()
    val replyFetchInterval by viewModel.replyFetchInterval.collectAsStateWithLifecycle()
    val autoBackupOwnPosts by viewModel.autoBackupOwnPosts.collectAsStateWithLifecycle()
    val duplicateBackupAction by viewModel.duplicateBackupAction.collectAsStateWithLifecycle()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.setBackupUri(uri)
    }

    val intervalDialogState = rememberDialogState()

    MyScaffold(
        topBar = {
            TitleCentredToolbar(
                title = stringResource(id = R.string.title_backup_settings),
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) }
            )
        },
    ) { paddingValues ->
        TextPrefsScreen(contentPadding = paddingValues) {
            TextPref(
                title = stringResource(id = R.string.title_set_backup_path),
                summary = if (backupUri != null) {
                    stringResource(id = R.string.summary_backup_path_set)
                } else {
                    stringResource(id = R.string.summary_backup_path_not_set)
                },
                onClick = { dirPickerLauncher.launch(null) },
                leadingIcon = Icons.Outlined.Folder,
            )

            TextPref(
                title = stringResource(id = R.string.title_backup_reply_interval),
                summary = stringResource(
                    id = R.string.summary_backup_reply_interval,
                    replyFetchInterval,
                ),
                onClick = intervalDialogState::show,
                leadingIcon = Icons.Outlined.Timer,
            )

            SwitchPref(
                checked = autoBackupOwnPosts,
                onCheckedChange = viewModel::setAutoBackupOwnPosts,
                title = R.string.title_auto_backup_own_posts,
                summary = R.string.summary_auto_backup_own_posts,
                leadingIcon = Icons.Rounded.Backup,
            )

            ListPref(
                value = duplicateBackupAction,
                title = R.string.title_duplicate_backup_action,
                onValueChange = viewModel::setDuplicateBackupAction,
                leadingIcon = Icons.Outlined.ContentCopy,
                options = persistentMapOf(
                    DuplicateBackupAction.OVERWRITE to R.string.title_backup_overwrite,
                    DuplicateBackupAction.KEEP_BOTH to R.string.title_backup_keep_both,
                    DuplicateBackupAction.ASK to R.string.title_backup_action_ask,
                ),
            )
        }
    }

    PromptDialog(
        dialogState = intervalDialogState,
        onConfirm = { input ->
            val value = input.toLongOrNull()
            if (value != null && value >= BackupRepository.MIN_REPLY_FETCH_INTERVAL) {
                viewModel.setReplyFetchInterval(value)
            }
        },
        keyboardType = KeyboardType.Number,
        initialValue = replyFetchInterval.toString(),
        onValueChange = { new, _ -> new.isEmpty() || new.all { it.isDigit() } },
        isError = { text -> (text.toLongOrNull() ?: -1L) < BackupRepository.MIN_REPLY_FETCH_INTERVAL },
        title = { Text(text = stringResource(id = R.string.title_backup_reply_interval)) },
    ) {
        Text(text = stringResource(id = R.string.message_backup_reply_interval))
    }
}
