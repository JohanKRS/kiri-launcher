package io.github.johankrs.kiri.ui.settings

import android.app.WallpaperManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.johankrs.kiri.KiriLiveWallpaper
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.LiveColor
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.core.look.LiveMotion
import io.github.johankrs.kiri.core.look.LiveScene
import io.github.johankrs.kiri.ui.live.LiveCanvas
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

private val PreviewHeight = 260.dp

/**
 * Kiri's own live wallpaper on a phone or tablet: one of the six scenes, in one of the six
 * colors of Kiri's icon, moving at the chosen pace. A change is saved at once and shows at once
 * on the wallpaper when it is already the device's; until then, "set as wallpaper" hands it to
 * Android, which shows it and asks to confirm. [screen] is the size of the screen in pixels,
 * for the preview to have its shape.
 */
@Composable
fun LiveWallpaperScreen(
    look: LiveLook,
    screen: IntSize,
    onChange: (LiveLook) -> Unit,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val context = LocalContext.current
    val muted = KiriType.caption.copy(color = colors.onSurfaceMuted)
    var on by remember { mutableStateOf(KiriLiveWallpaper.isOn(context)) }
    var failed by remember { mutableStateOf(false) }
    // Back from Android's screen, it may be the wallpaper now.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) on = KiriLiveWallpaper.isOn(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    SettingsScaffold(title = stringResource(R.string.live_title), onBack = onBack) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            val aspect = screen.width.coerceAtLeast(1) / screen.height.coerceAtLeast(1).toFloat()
            LiveCanvas(
                look = look,
                modifier = Modifier
                    .width(PreviewHeight * aspect)
                    .height(PreviewHeight)
                    .clip(RoundedCornerShape(16.dp)),
            )
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
                Named(
                    label = stringResource(R.string.live_scene),
                    values = LiveScene.entries,
                    value = look.scene,
                    name = { liveSceneName(it) },
                    onPick = { onChange(look.copy(scene = it)) },
                )
                Named(
                    label = stringResource(R.string.live_color),
                    values = LiveColor.entries,
                    value = look.color,
                    name = { liveColorName(it) },
                    onPick = { onChange(look.copy(color = it)) },
                )
                Named(
                    label = stringResource(R.string.live_motion),
                    values = LiveMotion.entries,
                    value = look.motion,
                    name = { liveMotionName(it) },
                    onPick = { onChange(look.copy(motion = it)) },
                )
            }

            if (on) {
                BasicText(
                    text = stringResource(R.string.live_on),
                    style = muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                BasicText(
                    text = stringResource(R.string.live_set),
                    style = KiriType.body.copy(color = colors.onAccent, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.accent)
                        .clickable(role = Role.Button) {
                            val chosen = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, KiriLiveWallpaper.component(context))
                            // Some devices only have the list of every live wallpaper.
                            failed = runCatching { context.startActivity(chosen) }.isFailure &&
                                runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }.isFailure
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                )
                if (failed) {
                    BasicText(
                        text = stringResource(R.string.live_failed),
                        style = muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                BasicText(
                    text = stringResource(R.string.live_note),
                    style = muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun liveSceneName(value: LiveScene): String = stringResource(
    when (value) {
        LiveScene.AURORA -> R.string.live_scene_aurora
        LiveScene.BRUMA -> R.string.live_scene_bruma
        LiveScene.FIOS -> R.string.live_scene_fios
        LiveScene.PASTILHAS -> R.string.live_scene_pastilhas
        LiveScene.SERRA -> R.string.live_scene_serra
        LiveScene.FLORES -> R.string.live_scene_flores
    },
)

/** The six colors are those of Kiri's own icon, and named like them. */
@Composable
internal fun liveColorName(value: LiveColor): String = stringResource(
    when (value) {
        LiveColor.LILAC -> R.string.app_icon_lilac
        LiveColor.MIST -> R.string.app_icon_mist
        LiveColor.SMOKE -> R.string.app_icon_smoke
        LiveColor.NIGHT -> R.string.app_icon_night
        LiveColor.ROSE -> R.string.app_icon_rose
        LiveColor.BLUE -> R.string.app_icon_blue
    },
)

@Composable
internal fun liveMotionName(value: LiveMotion): String = stringResource(
    when (value) {
        LiveMotion.STILL -> R.string.live_motion_still
        LiveMotion.SLOW -> R.string.live_motion_slow
        LiveMotion.NORMAL -> R.string.live_motion_normal
    },
)
