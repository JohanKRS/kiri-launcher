package io.github.johankrs.kiri.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.HiddenAppsList
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.ui.AppIcon
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

private val RowIcon = 36.dp
private val RowStart = 14.dp
private val RowGap = 14.dp

/** How much of an app shows while it is hidden: there, but quieter than the others. */
private const val HIDDEN_ALPHA = 0.42f

/**
 * Hides apps and shows them again. Every app of the phone is listed in the order of its name,
 * and above them, apart, the ones hidden now, so they are found without going through all.
 * A tap on a row hides the app or brings it back: its eye closes or opens, and the app appears
 * in, or leaves, the block of the hidden ones. Nothing else moves under the finger.
 *
 * [apps] are every app, hidden or not, by name; [hidden] the keys of the hidden ones.
 */
@Composable
fun HiddenAppsScreen(
    apps: List<AppEntry>,
    hidden: Set<String>,
    repository: AppRepository,
    onHiddenChange: (app: AppEntry, hidden: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    // What the rows show changes at once with a tap, while the saving catches up.
    var draft by remember { mutableStateOf(hidden) }
    val list = remember(apps, draft) { HiddenAppsList.of(apps, draft) }
    val toggle: (AppEntry) -> Unit = { app ->
        val hide = app.key !in draft
        draft = if (hide) draft + app.key else draft - app.key
        onHiddenChange(app, hide)
    }
    val muted = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted)

    SettingsScaffold(title = stringResource(R.string.settings_hidden_apps), onBack = onBack) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "caption") {
                BasicText(
                    text = stringResource(R.string.hidden_apps_caption),
                    style = muted,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 2.dp),
                )
            }
            if (list.hidden.isNotEmpty()) {
                item(key = "title:hidden") {
                    Box(Modifier.animateItem()) { SectionTitle(stringResource(R.string.hidden_apps_section_hidden)) }
                }
                itemsIndexed(list.hidden, key = { _, app -> "hidden:${app.key}" }) { index, app ->
                    HiddenAppRow(
                        app = app,
                        hidden = true,
                        first = index == 0,
                        last = index == list.hidden.lastIndex,
                        repository = repository,
                        onClick = { toggle(app) },
                    )
                }
            }
            item(key = "title:all") {
                Box(Modifier.animateItem()) { SectionTitle(stringResource(R.string.hidden_apps_section_all)) }
            }
            itemsIndexed(list.all, key = { _, app -> "all:${app.key}" }) { index, app ->
                HiddenAppRow(
                    app = app,
                    hidden = app.key in draft,
                    first = index == 0,
                    last = index == list.all.lastIndex,
                    repository = repository,
                    onClick = { toggle(app) },
                )
            }
            item(key = "end") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * One app of the list: its icon, its whole name and an eye, open while the app is shown and
 * struck through while it is hidden. A hidden app is drawn quieter. Both change with a spring,
 * read only while drawing, so a tap never rebuilds the row.
 */
@Composable
private fun LazyItemScope.HiddenAppRow(
    app: AppEntry,
    hidden: Boolean,
    first: Boolean,
    last: Boolean,
    repository: AppRepository,
    onClick: () -> Unit,
) {
    val colors = KiriTheme.colors
    val struck = animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "struck",
    )
    val quiet = Modifier.graphicsLayer { alpha = 1f - (1f - HIDDEN_ALPHA) * struck.value }
    val state = stringResource(if (hidden) R.string.hidden_app_state_hidden else R.string.hidden_app_state_shown)
    val action = stringResource(if (hidden) R.string.hidden_app_show else R.string.hidden_app_hide)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .groupPiece(first = first, last = last, seamInset = RowStart + RowIcon + RowGap)
            .clickable(onClickLabel = action, onClick = onClick)
            .semantics { stateDescription = state }
            .heightIn(min = 58.dp)
            .padding(start = RowStart, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        AppIcon(app, repository, RowIcon, quiet)
        Spacer(Modifier.width(RowGap))
        // The whole name, on as many lines as it takes: names are never cut.
        BasicText(
            text = app.label,
            style = KiriType.body.copy(color = colors.onSurface),
            modifier = quiet.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        EyeGlyph(struck = { struck.value }, color = colors.onSurfaceMuted, modifier = Modifier.size(22.dp))
    }
}
