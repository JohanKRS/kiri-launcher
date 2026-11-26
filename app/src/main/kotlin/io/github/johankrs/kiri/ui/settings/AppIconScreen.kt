package io.github.johankrs.kiri.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.data.AppIconColor
import io.github.johankrs.kiri.data.AppIcons
import io.github.johankrs.kiri.ui.theme.KiriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long the color has to stay put before Android is asked to show it. */
private const val APPLY_DELAY_MS = 700L

/**
 * The color of Kiri's own icon, as Android and other launchers show it. The picture follows
 * every change at once; Android is told only when the arrows rest (or on leaving the screen),
 * so the lists of apps are not redrawn at every step.
 */
@Composable
fun AppIconScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appIcons = remember(context) { AppIcons(context) }
    var chosen by remember { mutableStateOf(appIcons.current()) }
    val latest by rememberUpdatedState(chosen)

    LaunchedEffect(chosen) {
        delay(APPLY_DELAY_MS)
        withContext(Dispatchers.IO) { appIcons.choose(chosen) }
    }
    // Leaving before the arrows rested still keeps the color last shown.
    DisposableEffect(appIcons) {
        onDispose { appIcons.choose(latest) }
    }

    SettingsScaffold(title = stringResource(R.string.settings_app_icon), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            IconGallery(chosen = chosen, onPick = { chosen = it })
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                StepperCard(
                    label = stringResource(R.string.app_icon_color),
                    valueText = stringResource(chosen.label),
                    canDecrease = true,
                    canIncrease = true,
                    onDecrease = { chosen = chosen.previous() },
                    onIncrease = { chosen = chosen.next() },
                    modifier = Modifier.fillMaxWidth(),
                    values = AppIconColor.entries.map { stringResource(it.label) },
                    named = true,
                )
                InfoCard(
                    title = stringResource(R.string.app_icon_note_title),
                    text = stringResource(R.string.app_icon_note),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The chosen icon, large, over all of them small. A small icon can be tapped too; the one in use
 * has a short line under it. Only a picture for the eyes: the arrows below say the same aloud.
 */
@Composable
private fun IconGallery(chosen: AppIconColor, onPick: (AppIconColor) -> Unit) {
    val colors = KiriTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(top = 26.dp, bottom = 14.dp, start = 6.dp, end = 6.dp)
            .clearAndSetSemantics { },
    ) {
        Crossfade(targetState = chosen, animationSpec = tween(240), label = "app icon") { color ->
            AppIconImage(color, 96.dp)
        }
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppIconColor.entries.forEach { color ->
                val mark by animateFloatAsState(if (color == chosen) 1f else 0f, tween(220), label = "mark")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(color) }
                        .padding(vertical = 8.dp),
                ) {
                    AppIconImage(color, 38.dp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 2.dp)
                            .graphicsLayer {
                                alpha = mark
                                scaleX = 0.4f + 0.6f * mark
                            }
                            .background(colors.onSurface, CircleShape),
                    )
                }
            }
        }
    }
}

/** The icon of [color] as Android draws it: its layers in the shape of this device's icons. */
@Composable
private fun AppIconImage(color: AppIconColor, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val picture = remember(color, px) {
        runCatching { context.getDrawable(color.icon)?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()
    }
    if (picture != null) {
        Image(bitmap = picture, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}
