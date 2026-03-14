package com.huanchengfly.tieba.post.backup

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.huanchengfly.tieba.post.activities.VideoViewActivity
import com.huanchengfly.tieba.post.navigateDebounced
import com.huanchengfly.tieba.post.ui.common.PbContentRender
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.isWindowWidthCompact
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.page.LocalNavController
import com.huanchengfly.tieba.post.ui.page.photoview.PhotoViewActivity
import com.huanchengfly.tieba.post.ui.widgets.compose.singleMediaFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoThumbnail
import com.huanchengfly.tieba.post.utils.GlideUtil
import java.io.File

/**
 * [PbContentRender] for a backup image.
 *
 * [model] is either a local [File] (extracted from the companion ZIP) or a remote URL [String].
 * Both are handled natively by Glide.
 */
@Immutable
class BackupImageContentRender(val model: Any?) : PbContentRender {

    @Composable
    override fun Render() {
        val m = model ?: return
        val context = LocalContext.current

        // Aspect ratio is unknown until Glide decodes the image; start with 1f and update once
        // the real dimensions are available so the image is never cropped.
        val intrinsicSizeState = remember(m) { mutableStateOf<IntSize?>(null) }
        val intrinsicSize = intrinsicSizeState.value
        val ratio = intrinsicSize?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height } ?: 1f

        val listener = remember(m) {
            object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    val w = resource.intrinsicWidth
                    val h = resource.intrinsicHeight
                    if (w > 0 && h > 0) intrinsicSizeState.value = IntSize(w, h)
                    return false
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean = false
            }
        }

        GlideImage(
            model = m,
            contentDescription = null,
            modifier = Modifier
                .pointerInput(m) {
                    detectTapGestures(onTap = {
                        val url = when (m) {
                            is File -> Uri.fromFile(m).toString()
                            is String -> m
                            else -> return@detectTapGestures
                        }
                        PhotoViewActivity.launchSinglePhoto(
                            context = context,
                            url = url,
                            useTbGlideUrl = m is String,
                        )
                    })
                }
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth(singleMediaFraction)
                .aspectRatio(ratio),
            contentScale = ContentScale.FillWidth,
            failure = GlideUtil.DefaultErrorPlaceholder,
        ) { req -> req.addListener(listener) }
    }

    override fun toString(): String = PbContentRender.MEDIA_PICTURE
}

/**
 * [PbContentRender] for a backup video.
 *
 * [coverModel] is either a local [File] or a URL [String] for the cover thumbnail.
 * If [videoUrl] is available the video is played natively; otherwise [webUrl] is opened in the
 * in-app browser (same fallback as the online [VideoContentRender]).
 */
@Immutable
class BackupVideoContentRender(
    val coverModel: Any?,
    val videoUrl: String,
    val webUrl: String,
) : PbContentRender {

    @Composable
    override fun Render() {
        val context = LocalContext.current
        val navigator = LocalNavController.current
        val widthFraction = if (isWindowWidthCompact()) 1f else 0.5f

        val picModifier = Modifier
            .fillMaxWidth(widthFraction)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.small)

        // Convert model to a string accepted by VideoThumbnail / VideoViewActivity
        val coverUrl: String? = when (val m = coverModel) {
            is File -> m.absolutePath
            is String -> m.takeIf { it.isNotBlank() }
            else -> null
        }

        when {
            videoUrl.isNotBlank() -> VideoThumbnail(
                modifier = picModifier,
                thumbnailUrl = coverUrl,
                onClick = { VideoViewActivity.launch(context, videoUrl, coverUrl ?: "") },
            )

            webUrl.isNotBlank() -> VideoThumbnail(
                modifier = picModifier,
                thumbnailUrl = coverUrl,
                onClick = { navigator.navigateDebounced(Destination.WebView(webUrl)) },
            )

            coverModel != null -> GlideImage(
                model = coverModel,
                contentDescription = null,
                modifier = picModifier,
                contentScale = ContentScale.Crop,
                failure = GlideUtil.DefaultErrorPlaceholder,
            )
        }
    }

    override fun toString(): String = PbContentRender.MEDIA_VIDEO
}
