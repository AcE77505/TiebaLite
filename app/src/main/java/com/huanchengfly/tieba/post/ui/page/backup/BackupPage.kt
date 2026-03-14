package com.huanchengfly.tieba.post.ui.page.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.collectUiEventWithLifecycle
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.navigateDebounced
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.LongClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.SharedTransitionUserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberSnackbarHostState
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import com.huanchengfly.tieba.post.utils.DateTimeUtils

@Composable
fun BackupPage(
    navigator: NavController,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = rememberSnackbarHostState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupUri by viewModel.backupUri.collectAsStateWithLifecycle()

    // SAF directory picker launcher
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBackupUri(uri)
        }
    }

    // Show "set backup path" dialog if no path is set yet
    val setPathDialogState = rememberDialogState()
    if (backupUri == null) {
        ConfirmDialog(
            dialogState = setPathDialogState,
            onConfirm = { dirPickerLauncher.launch(null) },
            confirmText = stringResource(id = R.string.title_set_backup_path),
            title = { Text(text = stringResource(id = R.string.title_set_backup_path)) },
        ) {
            Text(text = stringResource(id = R.string.message_set_backup_path))
        }
    }

    // Delete confirmation dialog
    var backupToDelete by remember { mutableStateOf<BackupData?>(null) }
    val deleteDialogState = rememberDialogState()

    backupToDelete?.let {
        ConfirmDialog(
            dialogState = deleteDialogState,
            onConfirm = {
                viewModel.deleteBackup(it)
                backupToDelete = null
            },
            onCancel = { backupToDelete = null },
            title = { Text(text = stringResource(id = R.string.title_backup_delete_confirm)) },
        ) {
            Text(text = stringResource(id = R.string.message_backup_delete_confirm))
        }
    }

    viewModel.uiEvent.collectUiEventWithLifecycle { event ->
        val message = when (event) {
            is BackupUiEvent.Toast -> event.message
            else -> Unit
        }
        if (message is String) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    MyScaffold(
        topBar = {
            TitleCentredToolbar(
                title = stringResource(id = R.string.title_backup_management),
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = navigator::navigateUp)
                },
                actions = {
                    // Change backup path button
                    androidx.compose.material3.IconButton(onClick = { dirPickerLauncher.launch(null) }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = stringResource(id = R.string.title_set_backup_path)
                        )
                    }
                }
            )
        },
    ) { contentPadding ->
        val isEmpty = uiState.backups.isEmpty() && !uiState.isLoading

        StateScreen(
            isEmpty = isEmpty,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onReload = viewModel::loadBackups,
            screenPadding = contentPadding,
            emptyScreen = {
                Text(
                    text = stringResource(id = R.string.tip_no_backup),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(items = uiState.backups, key = { "${it.threadId}_${it.backupTime}" }) { backup ->
                    BackupItem(
                        backup = backup,
                        onDeleteClick = {
                            backupToDelete = backup
                            deleteDialogState.show()
                        },
                        onOpenClick = {
                            navigator.navigateDebounced(
                                Destination.BackupViewer(threadId = backup.threadId)
                            )
                        },
                    )
                }
            }
        }
    }

    // Prompt user to set path on first entry
    if (backupUri == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            setPathDialogState.show()
        }
    }
}

@Composable
private fun BackupItem(
    backup: BackupData,
    onDeleteClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formattedTime = remember(backup.backupTime) {
        DateTimeUtils.getRelativeTimeString(context, backup.backupTime)
    }

    LongClickMenu(
        menuContent = {
            TextMenuItem(
                text = R.string.action_delete_backup,
                onClick = onDeleteClick,
            )
        },
        onClick = onOpenClick,
    ) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SharedTransitionUserHeader(
                uid = backup.authorId,
                avatar = backup.authorAvatar,
                name = backup.authorName,
                desc = formattedTime,
                onClick = null,
            ) {
                Spacer(Modifier.weight(1.0f))

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(id = R.string.title_backup_forum, backup.forumName),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Thread title
            Text(
                text = backup.title,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
