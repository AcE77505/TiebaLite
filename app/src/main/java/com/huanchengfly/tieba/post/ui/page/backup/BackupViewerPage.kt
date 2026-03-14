package com.huanchengfly.tieba.post.ui.page.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.backup.imageKeyOrUrl
import com.huanchengfly.tieba.post.navigateDebounced
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.thread.PostCard
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import java.io.File

@Composable
fun BackupViewerPage(
    navigator: NavController,
    viewModel: BackupViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProvideNavigator(navigator = navigator) {
        MyScaffold(
            topBar = {
                TitleCentredToolbar(
                    title = uiState.backup?.title
                        ?: stringResource(id = R.string.title_backup_management),
                    navigationIcon = {
                        BackNavigationIcon(onBackPressed = navigator::navigateUp)
                    },
                )
            },
        ) { contentPadding ->
            StateScreen(
                isEmpty = uiState.backup == null && !uiState.isLoading,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onReload = viewModel::loadBackup,
                screenPadding = contentPadding,
            ) {
                val backup = uiState.backup ?: return@StateScreen
                val postData = uiState.postData ?: return@StateScreen

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    item {
                        BackupForumHeader(backup = backup, imagesDir = uiState.imagesDir)
                        HorizontalDivider(
                            thickness = 2.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                        )
                    }

                    item {
                        PostCard(
                            post = postData,
                            onUserClick = {
                                navigator.navigateDebounced(
                                    Destination.UserProfile(user = postData.author)
                                )
                            },
                            onMenuCopyClick = { navigator.navigateDebounced(Destination.CopyText(it)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small forum-chip header shown above the post card, displaying which forum the thread belongs to.
 * This mirrors the forum context shown in online thread viewing.
 */
@Composable
private fun BackupForumHeader(backup: BackupData, imagesDir: File?) {
    val forumAvatarModel = remember(backup.imageKeyForumAvatar, backup.forumAvatar) {
        imageKeyOrUrl(imagesDir, backup.imageKeyForumAvatar, backup.forumAvatar)
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (forumAvatarModel != null) {
                    Avatar(
                        data = forumAvatarModel,
                        modifier = Modifier.size(Sizes.Tiny),
                    )
                }
                Text(
                    text = stringResource(id = R.string.title_backup_forum, backup.forumName),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

