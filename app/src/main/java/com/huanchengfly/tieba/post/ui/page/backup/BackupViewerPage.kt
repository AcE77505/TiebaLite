package com.huanchengfly.tieba.post.ui.page.backup

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.backup.imageKeyOrUrl
import com.huanchengfly.tieba.post.components.glide.TbGlideUrl
import com.huanchengfly.tieba.post.navigateDebounced
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.thread.PostCard
import com.huanchengfly.tieba.post.ui.page.thread.ThreadHeader
import com.huanchengfly.tieba.post.ui.page.thread.ThreadSortType
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.CenterAlignedTopAppBar
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen

@Composable
fun BackupViewerPage(
    navigator: NavController,
    viewModel: BackupViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backup = uiState.backup

    ProvideNavigator(navigator = navigator) {
        MyScaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        if (backup != null) {
                            val forumAvatarModel = remember(
                                backup.imageKeyForumAvatar,
                                backup.forumAvatar,
                                uiState.imagesDir,
                            ) {
                                val offlineModel = imageKeyOrUrl(
                                    uiState.imagesDir,
                                    backup.imageKeyForumAvatar,
                                    backup.forumAvatar,
                                )
                                offlineModel ?: backup.forumAvatar?.let { TbGlideUrl(url = it) }
                            }
                            val chipDesc = backup.forumName
                            BackupForumChip(
                                forumName = backup.forumName,
                                forumAvatarModel = forumAvatarModel,
                                contentDescription = chipDesc,
                                onClick = {
                                    navigator.navigateDebounced(
                                        Destination.Forum(forumName = backup.forumName)
                                    )
                                },
                            )
                        }
                    },
                    navigationIcon = {
                        BackNavigationIcon(onBackPressed = navigator::navigateUp)
                    },
                )
            },
        ) { contentPadding ->
            StateScreen(
                isEmpty = backup == null && !uiState.isLoading,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onReload = viewModel::loadBackup,
                screenPadding = contentPadding,
            ) {
                val checkedBackup = uiState.backup ?: return@StateScreen
                val postData = uiState.postData ?: return@StateScreen

                // When seeLz is active, filter replies to only the OP's posts.
                val visibleReplies = if (uiState.seeLz) {
                    uiState.replyDataList.filter { it.author.isLz }
                } else {
                    uiState.replyDataList
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    item {
                        PostCard(
                            post = postData,
                            onUserClick = {
                                navigator.navigateDebounced(
                                    Destination.UserProfile(user = postData.author)
                                )
                            },
                            onMenuCopyClick = {
                                navigator.navigateDebounced(Destination.CopyText(it))
                            },
                        )
                    }

                    // Show ThreadHeader only when there are replies stored in the backup.
                    if (uiState.replyDataList.isNotEmpty() || checkedBackup.replyNum > 0) {
                        item(key = "thread_header") {
                            HorizontalDivider(
                                thickness = 2.dp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                            )
                            ThreadHeader(
                                replyNum = checkedBackup.replyNum,
                                sortType = ThreadSortType.BY_ASC,
                                isSeeLz = uiState.seeLz,
                                onSeeLzChanged = viewModel::toggleSeeLz,
                            )
                        }

                        items(items = visibleReplies, key = { it.id }) { reply ->
                            PostCard(
                                post = reply,
                                onUserClick = {
                                    navigator.navigateDebounced(
                                        Destination.UserProfile(user = reply.author)
                                    )
                                },
                                onMenuCopyClick = {
                                    navigator.navigateDebounced(Destination.CopyText(it))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clickable forum-name chip displayed in the top bar of the backup viewer.
 * Matches the style of [ForumTitleChip] in the online thread viewer.
 */
@Composable
private fun BackupForumChip(
    forumName: String,
    forumAvatarModel: Any?,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            this.contentDescription = contentDescription
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .height(intrinsicSize = IntrinsicSize.Min)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                data = forumAvatarModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
            )
            val forumStyle = MaterialTheme.typography.titleMedium
            Text(
                text = stringResource(id = R.string.title_forum, forumName),
                modifier = Modifier.padding(horizontal = 8.dp),
                autoSize = TextAutoSize.StepBased(8.sp, forumStyle.fontSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = forumStyle,
            )
        }
    }
}
