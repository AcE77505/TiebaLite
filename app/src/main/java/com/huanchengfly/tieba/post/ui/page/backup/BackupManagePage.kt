package com.huanchengfly.tieba.post.ui.page.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.destinations.BackupSettingsPageDestination
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.appPreferences
import com.huanchengfly.tieba.post.utils.isBackupPathConfigured
import com.huanchengfly.tieba.post.utils.listBackupFiles
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@OptIn(ExperimentalMaterialApi::class)
@Destination
@Composable
fun BackupManagePage(
    navigator: DestinationsNavigator,
) {
    val context = LocalContext.current
    var backupFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    val noPathDialogState = rememberDialogState()

    // Navigate to settings if path not configured
    ConfirmDialog(
        dialogState = noPathDialogState,
        onConfirm = { navigator.navigate(BackupSettingsPageDestination) },
        confirmText = stringResource(id = R.string.btn_go_to_backup_settings),
        cancelText = stringResource(id = R.string.button_cancel),
        title = { Text(text = stringResource(id = R.string.title_backup_settings)) },
    ) {
        Text(text = stringResource(id = R.string.msg_backup_path_not_configured))
    }

    LaunchedEffect(Unit) {
        if (!context.isBackupPathConfigured()) {
            noPathDialogState.show()
        } else {
            backupFiles = context.listBackupFiles()
        }
    }

    MyScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            TitleCentredToolbar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_backup_management),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.h6,
                    )
                },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                },
                actions = {
                    IconButton(onClick = { navigator.navigate(BackupSettingsPageDestination) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(id = R.string.title_backup_settings),
                            tint = ExtendedTheme.colors.onTopBar,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (backupFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Backup,
                        contentDescription = null,
                        tint = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.title_no_backup),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExtendedTheme.colors.text,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(id = R.string.msg_no_backup),
                        fontSize = 14.sp,
                        color = ExtendedTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                items(backupFiles) { file ->
                    BackupFileItem(file = file)
                }
            }
        }
    }
}

@Composable
private fun BackupFileItem(file: DocumentFile) {
    Box(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = file.name ?: "",
                style = MaterialTheme.typography.body1,
                color = ExtendedTheme.colors.text,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = android.text.format.DateFormat.format(
                    "yyyy-MM-dd HH:mm:ss",
                    file.lastModified()
                ).toString(),
                style = MaterialTheme.typography.caption,
                color = ExtendedTheme.colors.textSecondary,
            )
        }
    }
}
