package io.github.johankrs.kiri.ui.settings

import android.app.WallpaperManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.WallpaperFraming
import io.github.johankrs.kiri.core.look.WallpaperSetup
import io.github.johankrs.kiri.core.look.WallpaperTarget
import io.github.johankrs.kiri.data.WallpaperPreview
import io.github.johankrs.kiri.data.WallpaperStudio
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

private val PreviewHeight = 220.dp
private const val SAVE_DEBOUNCE_MS = 250L

/** What happened to the last press of "apply". */
private enum class Applying { IDLE, WORKING, DONE, FAILED }

/**
 * Chooses a picture and makes it the wallpaper of the device: framed by dragging the preview
 * and by the zoom, darkened, blurred, made to travel with the home pages, and put on the home
 * screen, the lock screen or both.
 *
 * [setup] is saved as it changes; nothing reaches the device before "apply" is pressed.
 * [screen] is the size of the screen in pixels. [onApplied] is told once the device has the
 * new wallpaper.
 */
@Composable
fun WallpaperScreen(
    setup: WallpaperSetup,
    studio: WallpaperStudio,
    screen: IntSize,
    onChange: (WallpaperSetup) -> Unit,
    onApplied: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(setup) }
    var saved by remember { mutableStateOf(setup) }
    val latestOnChange by rememberUpdatedState(onChange)
    val change: (WallpaperSetup) -> Unit = { next ->
        if (next != draft) draft = next
    }
    val currentChange by rememberUpdatedState(change)
    val currentDraft by rememberUpdatedState(draft)
    val currentSaved by rememberUpdatedState(saved)
    var applying by remember { mutableStateOf(Applying.IDLE) }

    // Coalesce rapid pan gestures and step taps into one DataStore write instead of writing per pixel.
    LaunchedEffect(draft) {
        if (draft != saved) {
            delay(SAVE_DEBOUNCE_MS)
            latestOnChange(draft)
            saved = draft
        }
    }
    DisposableEffect(studio) {
        onDispose {
            if (currentDraft != currentSaved) latestOnChange(currentDraft)
            studio.releasePreview()
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                // A new picture starts whole and centred.
                if (studio.choose(uri)) currentChange(currentDraft.withZoom(100).withPan(0, 0))
            }
        }
    }

    val previewHeightPx = with(density) { PreviewHeight.roundToPx() }
    var picture by remember { mutableStateOf<WallpaperPreview?>(null) }
    LaunchedEffect(studio, studio.version, screen, previewHeightPx, density.density) {
        snapshotFlow { draft }
            .conflate()
            .collect { target ->
                picture = studio.preview(target, screen, previewHeightPx, density.density)
            }
    }
    val hasPicture = studio.version > 0
    val muted = KiriType.caption.copy(color = colors.onSurfaceMuted)

    SettingsScaffold(title = stringResource(R.string.settings_wallpaper), onBack = onBack) {
        // The two places the wallpaper goes to, side by side. The one left out is dimmed.
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val previewWidth = PreviewHeight * (screen.width.coerceAtLeast(1) / screen.height.coerceAtLeast(1).toFloat())
            for (home in listOf(true, false)) {
                val used = if (home) draft.target.onHome else draft.target.onLock
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(previewWidth)
                            .height(PreviewHeight)
                            .graphicsLayer { alpha = if (used) 1f else 0.35f }
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.control)
                            .pointerInput(screen) {
                                // Dragging the picture moves the part of it that is seen.
                                detectDragGestures { gesture, drag ->
                                    gesture.consume()
                                    val shown = picture ?: return@detectDragGestures
                                    val now = currentDraft
                                    val wallpaperWidth = WallpaperFraming.wallpaperWidth(screen.width, now.scrollPercent)
                                    val across = drag.x / (size.width * wallpaperWidth / screen.width.toFloat())
                                    val down = drag.y / size.height
                                    currentChange(
                                        now.withPan(
                                            WallpaperFraming.panAfterDrag(now.panX, across, shown.shareX),
                                            WallpaperFraming.panAfterDrag(now.panY, down, shown.shareY),
                                        ),
                                    )
                                }
                            },
                    ) {
                        val shown = picture
                        if (shown != null) {
                            Image(
                                bitmap = shown.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (!hasPicture) {
                            BasicText(
                                text = stringResource(R.string.wallpaper_none),
                                style = muted.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = stringResource(if (home) R.string.wallpaper_on_home else R.string.wallpaper_on_lock),
                        style = muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingsLinkRow(
                    title = stringResource(R.string.wallpaper_pick),
                    summary = stringResource(R.string.wallpaper_pick_summary),
                    onClick = {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.wallpaper_live),
                    summary = stringResource(R.string.wallpaper_live_summary),
                    onClick = {
                        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                    },
                )
            }

            if (hasPicture) {
                var targetsOpen by remember { mutableStateOf(false) }
                val targets = WallpaperTarget.entries
                SettingsGroup {
                    StepperCard(
                        label = stringResource(R.string.wallpaper_zoom),
                        valueText = "${draft.zoomPercent}%",
                        canDecrease = draft.zoomPercent > WallpaperSetup.ZOOM_PERCENT.first,
                        canIncrease = draft.zoomPercent < WallpaperSetup.ZOOM_PERCENT.last,
                        onDecrease = { change(draft.withZoom(draft.zoomPercent - WallpaperSetup.ZOOM_STEP)) },
                        onIncrease = { change(draft.withZoom(draft.zoomPercent + WallpaperSetup.ZOOM_STEP)) },
                    )
                    StepperCard(
                        label = stringResource(R.string.wallpaper_scroll),
                        valueText = levelText(draft.scrollPercent, WallpaperSetup.SCROLL_PERCENT, WallpaperSetup.SCROLL_STEP),
                        canDecrease = draft.scrollPercent > WallpaperSetup.SCROLL_PERCENT.first,
                        canIncrease = draft.scrollPercent < WallpaperSetup.SCROLL_PERCENT.last,
                        onDecrease = { change(draft.withScroll(draft.scrollPercent - WallpaperSetup.SCROLL_STEP)) },
                        onIncrease = { change(draft.withScroll(draft.scrollPercent + WallpaperSetup.SCROLL_STEP)) },
                    )
                    StepperCard(
                        label = stringResource(R.string.wallpaper_dim),
                        valueText = levelText(draft.dimPercent, WallpaperSetup.DIM_PERCENT, WallpaperSetup.DIM_STEP),
                        canDecrease = draft.dimPercent > WallpaperSetup.DIM_PERCENT.first,
                        canIncrease = draft.dimPercent < WallpaperSetup.DIM_PERCENT.last,
                        onDecrease = { change(draft.withDim(draft.dimPercent - WallpaperSetup.DIM_STEP)) },
                        onIncrease = { change(draft.withDim(draft.dimPercent + WallpaperSetup.DIM_STEP)) },
                    )
                    StepperCard(
                        label = stringResource(R.string.wallpaper_blur),
                        valueText = levelText(draft.blurDp, WallpaperSetup.BLUR_DP, WallpaperSetup.BLUR_STEP),
                        canDecrease = draft.blurDp > WallpaperSetup.BLUR_DP.first,
                        canIncrease = draft.blurDp < WallpaperSetup.BLUR_DP.last,
                        onDecrease = { change(draft.withBlur(draft.blurDp - WallpaperSetup.BLUR_STEP)) },
                        onIncrease = { change(draft.withBlur(draft.blurDp + WallpaperSetup.BLUR_STEP)) },
                    )
                    ChoiceCard(
                        label = stringResource(R.string.wallpaper_target),
                        valueText = stringResource(draft.target.labelRes()),
                        expanded = targetsOpen,
                        onToggle = { targetsOpen = !targetsOpen },
                        options = targets.map { stringResource(it.labelRes()) },
                        selectedIndex = targets.indexOf(draft.target),
                        onSelect = { index ->
                            change(draft.copy(target = targets[index]))
                            targetsOpen = false
                        },
                    )
                }

                // The one press that changes the device.
                BasicText(
                    text = stringResource(
                        when (applying) {
                            Applying.WORKING -> R.string.wallpaper_applying
                            else -> R.string.wallpaper_apply
                        },
                    ),
                    style = KiriType.body.copy(color = colors.onAccent, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.accent)
                        .clickable(enabled = applying != Applying.WORKING, role = Role.Button) {
                            applying = Applying.WORKING
                            scope.launch {
                                val done = studio.apply(currentDraft, screen, density.density)
                                applying = if (done) Applying.DONE else Applying.FAILED
                                if (done) onApplied()
                            }
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                )
                if (applying == Applying.DONE || applying == Applying.FAILED) {
                    BasicText(
                        text = stringResource(
                            if (applying == Applying.DONE) R.string.wallpaper_applied else R.string.wallpaper_failed,
                        ),
                        style = muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                BasicText(
                    text = stringResource(R.string.wallpaper_note),
                    style = muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun WallpaperTarget.labelRes(): Int = when (this) {
    WallpaperTarget.HOME -> R.string.wallpaper_target_home
    WallpaperTarget.LOCK -> R.string.wallpaper_target_lock
    WallpaperTarget.BOTH -> R.string.wallpaper_target_both
}
