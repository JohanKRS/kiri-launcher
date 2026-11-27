package io.github.johankrs.kiri.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.data.MediaPictures
import io.github.johankrs.kiri.data.TvPicture
import io.github.johankrs.kiri.data.TvPictureSource
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlinx.coroutines.launch

/**
 * Choosing the picture behind the TV's home screen. Many TVs have no gallery and no screen to
 * pick a file from, so Kiri shows the pictures on the TV and its USB drives itself, once it is
 * allowed to see them; a gallery or file manager on the TV is offered too, when there is one.
 * [onPicked] comes once the picture is kept.
 */
@Composable
fun TvPictureScreen(
    onPicked: () -> Unit,
    onBack: () -> Unit,
    source: TvPictureSource? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pictures = source ?: remember(context) { MediaPictures(context) }
    val kept = remember(context) { TvPicture.of(context) }
    var failed by remember { mutableStateOf(false) }
    // Goes up when the permission comes back, to read the pictures again.
    var asked by remember { mutableIntStateOf(0) }

    val take: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                if (kept.keep(uri)) onPicked() else failed = true
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), take)
    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), take)
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }

    // Android's own photo picker where it has one; otherwise whatever app opens pictures.
    val otherApp: (() -> Unit)? = remember(context) {
        when {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context) -> {
                { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").resolveActivity(context.packageManager) != null -> {
                { runCatching { contentPicker.launch("image/*") } }
            }
            else -> null
        }
    }
    val allowed by produceState(pictures.allowed(), asked) { value = pictures.allowed() }
    val found by produceState<List<Uri>?>(null, allowed, asked) { value = pictures.list() }

    SettingsScaffold(title = stringResource(R.string.tv_picker_title), onBack = onBack, focusFirst = true) {
        val colors = KiriTheme.colors
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val canAsk = !allowed && pictures.canAsk
            if (otherApp != null || canAsk) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsGroup {
                        if (canAsk) {
                            SettingsLinkRow(
                                title = stringResource(R.string.tv_picker_allow),
                                summary = stringResource(R.string.tv_picker_allow_summary),
                                onClick = { runCatching { askPermission.launch(pictures.permission) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (otherApp != null) {
                            SettingsLinkRow(
                                title = stringResource(R.string.tv_picker_other_app),
                                summary = stringResource(R.string.tv_picker_other_app_summary),
                                onClick = otherApp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            if (failed) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BasicText(
                        stringResource(R.string.tv_picker_failed),
                        style = KiriType.body.copy(color = colors.onSurface),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            val list = found
            if (allowed && list != null && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BasicText(
                        stringResource(R.string.tv_picker_none),
                        style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.toString() }) { uri ->
                PictureTile(uri, pictures) {
                    failed = false
                    take(uri)
                }
            }
        }
    }
}

/** One picture, small, 16:9 like the screen it would cover. */
@Composable
private fun PictureTile(uri: Uri, pictures: TvPictureSource, onPick: () -> Unit) {
    val colors = KiriTheme.colors
    val thumbnail by produceState<ImageBitmap?>(null, uri) { value = pictures.thumbnail(uri, 320, 180) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.05f else 1f, label = "pictureGrow")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(colors.onSurface.copy(alpha = 0.08f))
            .then(if (focused) Modifier.border(3.dp, colors.onSurface, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Image, onClick = onPick),
    ) {
        thumbnail?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
