package com.huanchengfly.tieba.post.ui.page.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.GlideImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.backup.BackupContentItem
import com.huanchengfly.tieba.post.backup.BackupData
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import com.huanchengfly.tieba.post.utils.DateTimeUtils
import com.huanchengfly.tieba.post.utils.GlideUtil
import java.io.File

@Composable
fun BackupViewerPage(
    navigator: NavController,
    viewModel: BackupViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item {
                    BackupViewerHeader(backup = backup, imagesDir = uiState.imagesDir)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(items = backup.contentItems) { item ->
                    BackupContentItemView(
                        item = item,
                        imagesDir = uiState.imagesDir,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupViewerHeader(backup: BackupData, imagesDir: File?) {
    val context = LocalContext.current
    val formattedTime = remember(backup.backupTime) {
        DateTimeUtils.getRelativeTimeString(context, backup.backupTime)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Forum chip
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val forumAvatarModel = remember(backup.imageKeyForumAvatar, backup.forumAvatar) {
                    imageKeyOrUrl(imagesDir, backup.imageKeyForumAvatar, backup.forumAvatar)
                }
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

        // Thread title
        Text(
            text = backup.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // Author row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val authorAvatarModel = remember(backup.imageKeyAuthorAvatar, backup.authorAvatar) {
                imageKeyOrUrl(imagesDir, backup.imageKeyAuthorAvatar, backup.authorAvatar)
            }
            Avatar(
                data = authorAvatarModel,
                modifier = Modifier.size(Sizes.Small),
            )
            Text(
                text = stringResource(
                    id = R.string.tip_backup_author_time,
                    backup.authorName,
                    formattedTime,
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupContentItemView(
    item: BackupContentItem,
    imagesDir: File?,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is BackupContentItem.Text -> {
            Text(
                text = item.content,
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is BackupContentItem.Image -> {
            val model = remember(item.imageKey, item.url) {
                imageKeyOrUrl(imagesDir, item.imageKey, item.originUrl.takeIf { it.isNotBlank() } ?: item.url)
            }
            if (model != null) {
                GlideImage(
                    model = model,
                    contentDescription = null,
                    modifier = modifier.aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                    failure = GlideUtil.DefaultErrorPlaceholder,
                )
            }
        }

        is BackupContentItem.Video -> {
            val coverModel = remember(item.imageKeyCover, item.coverUrl) {
                imageKeyOrUrl(imagesDir, item.imageKeyCover, item.coverUrl)
            }
            Box(modifier = modifier.aspectRatio(16f / 9f)) {
                if (coverModel != null) {
                    GlideImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        failure = GlideUtil.DefaultErrorPlaceholder,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = stringResource(R.string.tip_video_offline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * Returns a [File] for [imageKey] inside [imagesDir] if the file exists,
 * otherwise returns [fallbackUrl] (may be null when unavailable).
 */
private fun imageKeyOrUrl(imagesDir: File?, imageKey: String?, fallbackUrl: String?): Any? {
    if (!imageKey.isNullOrBlank() && imagesDir != null) {
        val file = File(imagesDir, imageKey)
        if (file.exists()) return file
    }
    return fallbackUrl?.takeIf { it.isNotBlank() }
}
