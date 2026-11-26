package io.github.johankrs.kiri.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.AppNames
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconGround
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.IconPack
import io.github.johankrs.kiri.ui.AppCell
import io.github.johankrs.kiri.ui.rememberAppIcon
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.backdrop
import kotlinx.coroutines.delay

private val TileSize = 64.dp
private val TileShape = RoundedCornerShape(16.dp)
private val SearchShape = RoundedCornerShape(14.dp)
private val PreviewShape = RoundedCornerShape(16.dp)

/** The icons of one pack shown together, and the pack they come from. */
private typealias PackIcons = Pair<IconPack, List<String>>

/**
 * Edits how [app] is shown: its name, how large the drawing of its icon is, what the icon sits
 * on, and which icon it is, from the installed icon packs or its own.
 *
 * All of it belongs to the app: [onRename], [onChoose], [onArtChange] and [onGroundChange] are
 * told once, and every place that shows the app follows. At the top the app is shown as on the
 * home screen ([homeSpec], names in [labelStyle]), with the name being typed, so it is seen
 * whether the new name fits.
 *
 * Everything is on one screen: first the icons every pack made for this very app, then each
 * pack, closed until it is opened, and above them a search by the name of an app through all
 * the packs at once. [apps] are the installed apps, which let a search by name find the icons
 * a pack made for them.
 *
 * [artPercent] is the zoom chosen for this app's drawing, or null while it is as large as
 * Kiri made it; stepping away from that starts at the size it is shown at now, or at
 * [sharedArtPercent] before the icon is there. [ground] is what was
 * chosen for this app's icon to sit on, or null while it follows all the others.
 */
@Composable
fun IconPickerScreen(
    app: AppEntry,
    apps: List<AppEntry>,
    homeSpec: GridSpec,
    labelStyle: TextStyle,
    current: IconChoice?,
    artPercent: Int?,
    sharedArtPercent: Int,
    ground: IconGround?,
    repository: AppRepository,
    onChoose: (IconChoice?) -> Unit,
    onArtChange: (Int?) -> Unit,
    onGroundChange: (IconGround?) -> Unit,
    onRename: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val muted = KiriType.caption.copy(color = colors.onSurfaceMuted)
    val packs = repository.iconPacks
    val tilePx = with(LocalDensity.current) { 48.dp.roundToPx() }

    // Free pack preview thumbnails as soon as the person leaves the picker.
    DisposableEffect(packs) {
        onDispose { packs.clearPreviewCache() }
    }

    // Null while the list of packs is being read.
    val installed by produceState<List<IconPack>?>(null) { value = packs.installed() }
    var query by rememberSaveable { mutableStateOf("") }
    // The packs opened, by package: all start closed.
    var opened by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val forApp by produceState(emptyList<PackIcons>(), installed, app.key) {
        value = installed?.let { packs.suggestionsEverywhere(app, it) }.orEmpty()
    }
    // Null while there is nothing to search or the first search is on its way. While more is
    // typed, what was found stays until the next search has its answer.
    val found by produceState<List<PackIcons>?>(null, query, installed) {
        val known = installed
        if (known == null || query.isBlank()) {
            value = null
        } else {
            // Typing goes on: the search waits for a pause.
            delay(SEARCH_PAUSE_MS)
            value = packs.search(query, apps, known)
        }
    }
    val openIcons by produceState(emptyMap<String, List<String>>(), opened) {
        value = opened.associateWith { packs.allIcons(it) }
    }

    SettingsScaffold(title = stringResource(R.string.icon_picker_title, app.label), onBack = onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileSize),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            wide("adjust") {
                IconAdjustments(
                    app = app,
                    homeSpec = homeSpec,
                    labelStyle = labelStyle,
                    artPercent = artPercent,
                    sharedArtPercent = sharedArtPercent,
                    ground = ground,
                    repository = repository,
                    onArtChange = onArtChange,
                    onGroundChange = onGroundChange,
                    onRename = onRename,
                )
            }
            wide("original") {
                OriginalRow(app, repository, tilePx, selected = current == null, onClick = { onChoose(null) })
            }
            wide("search") {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.icon_picker_search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            val known = installed
            if (query.isNotBlank()) {
                val results = found
                if (results != null && results.isEmpty()) {
                    wide("nothing") { BasicText(text = stringResource(R.string.icon_picker_nothing_found), style = muted) }
                }
                for ((pack, names) in results.orEmpty()) {
                    wide("found-title:${pack.packageName}") { BasicText(text = pack.label, style = muted) }
                    items(names, key = { "found:${pack.packageName}:$it" }) { name ->
                        PackIconTile(repository, IconChoice(pack.packageName, name), tilePx, current, onChoose, Modifier.animateItem())
                    }
                }
            } else {
                if (forApp.isNotEmpty()) {
                    wide("for-app-title") { BasicText(text = stringResource(R.string.icon_picker_suggested), style = muted) }
                    for ((pack, names) in forApp) {
                        // With more than one pack, each says where its icons come from.
                        if (forApp.size > 1) {
                            wide("for-app-pack:${pack.packageName}") { BasicText(text = pack.label, style = muted) }
                        }
                        items(names, key = { "for-app:${pack.packageName}:$it" }) { name ->
                            PackIconTile(repository, IconChoice(pack.packageName, name), tilePx, current, onChoose)
                        }
                    }
                }
                if (known != null && known.isEmpty()) {
                    wide("no-packs") { BasicText(text = stringResource(R.string.icon_picker_no_packs), style = muted) }
                }
                if (!known.isNullOrEmpty()) {
                    wide("packs-title") {
                        BasicText(
                            text = stringResource(R.string.icon_picker_packs),
                            style = muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                for (pack in known.orEmpty()) {
                    val isOpen = pack.packageName in opened
                    wide("pack:${pack.packageName}") {
                        PackHeader(
                            label = pack.label,
                            open = isOpen,
                            onClick = {
                                opened = if (isOpen) opened - pack.packageName else opened + pack.packageName
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (isOpen) {
                        val names = openIcons[pack.packageName]
                        if (names != null && names.isEmpty()) {
                            wide("empty:${pack.packageName}") {
                                BasicText(text = stringResource(R.string.icon_picker_empty_pack), style = muted)
                            }
                        }
                        items(names.orEmpty(), key = { "all:${pack.packageName}:$it" }) { name ->
                            PackIconTile(repository, IconChoice(pack.packageName, name), tilePx, current, onChoose, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

/** A row of the grid as wide as the grid. */
private fun LazyGridScope.wide(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}

/**
 * The app as it looks on the home screen, with the name being typed, and under it what can be
 * changed about it: its name, the zoom of its drawing and what its icon sits on.
 */
@Composable
private fun IconAdjustments(
    app: AppEntry,
    homeSpec: GridSpec,
    labelStyle: TextStyle,
    artPercent: Int?,
    sharedArtPercent: Int,
    ground: IconGround?,
    repository: AppRepository,
    onArtChange: (Int?) -> Unit,
    onGroundChange: (IconGround?) -> Unit,
    onRename: (String?) -> Unit,
) {
    val colors = KiriTheme.colors
    // What is in the field: the name the person gave the app, or nothing while it has its own.
    var typed by rememberSaveable(app.key) { mutableStateOf(if (app.renamed) app.label else "") }
    val shownName = AppNames.clean(typed, app.ownLabel) ?: app.ownLabel
    // The name is saved once typing pauses, and when the screen is left.
    val latestTyped by rememberUpdatedState(typed)
    val latestApp by rememberUpdatedState(app)
    val rename by rememberUpdatedState(onRename)
    val save = {
        val wanted = AppNames.clean(latestTyped, latestApp.ownLabel)
        if (wanted != AppNames.clean(latestApp.label, latestApp.ownLabel)) rename(wanted)
    }
    LaunchedEffect(typed) {
        delay(NAME_PAUSE_MS)
        save()
    }
    DisposableEffect(Unit) {
        onDispose { save() }
    }

    val iconPx = with(LocalDensity.current) { homeSpec.iconSizeDp.dp.roundToPx() }
    val shown by rememberAppIcon(app, repository, iconPx)

    val range = IconLook.APP_ART_PERCENT
    // The first step goes from where the drawing is now, so the icon does not jump.
    val from = artPercent ?: shown?.zoomPercent?.let { it - it % IconLook.ART_STEP } ?: sharedArtPercent
    var groundOpen by remember { mutableStateOf(false) }
    val grounds = listOf(null, IconGround.OWN, IconGround.GLASS)
    val groundLabels = listOf(
        stringResource(R.string.icon_picker_ground_shared),
        stringResource(R.string.icon_picker_ground_own),
        stringResource(R.string.icon_picker_ground_glass),
    )
    Column(Modifier.fillMaxWidth()) {
        HomePreview(app.copy(label = shownName), homeSpec, labelStyle, repository)
        SettingsGroup(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
        ) {
            NameRow(typed = typed, onType = { typed = it.take(AppNames.MAX_LENGTH) })
            StepperCard(
                label = stringResource(R.string.icon_picker_art),
                valueText = artPercent?.let { "$it%" } ?: stringResource(R.string.icon_picker_art_default),
                canDecrease = from > range.first,
                canIncrease = from < range.last,
                onDecrease = { onArtChange((from - IconLook.ART_STEP).coerceIn(range)) },
                onIncrease = { onArtChange((from + IconLook.ART_STEP).coerceIn(range)) },
            )
            if (artPercent != null) {
                BasicText(
                    text = stringResource(R.string.icon_picker_art_reset),
                    style = KiriType.body.copy(color = colors.accent),
                    modifier = Modifier
                        .clickable(role = Role.Button) { onArtChange(null) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
            // What the icon sits on: like all the others, its own background, or glass.
            ChoiceCard(
                label = stringResource(R.string.icon_picker_ground),
                valueText = groundLabels[grounds.indexOf(ground)],
                expanded = groundOpen,
                onToggle = { groundOpen = !groundOpen },
                options = groundLabels,
                selectedIndex = grounds.indexOf(ground),
                onSelect = { index ->
                    onGroundChange(grounds[index])
                    groundOpen = false
                },
            )
        }
    }
}

/**
 * [app] as it is shown on the home screen, over the wallpaper, in a cell as wide as the cells
 * there: the name breaks and shrinks just as it will under the icon.
 */
@Composable
private fun HomePreview(app: AppEntry, spec: GridSpec, labelStyle: TextStyle, repository: AppRepository) {
    val columns = spec.columns.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PreviewShape)
            .background(KiriTheme.colors.previewBackdrop)
            // The blurred wallpaper, so the icon on its glass looks as it does on the home screen.
            .backdrop()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        val middle = (columns - 1) / 2
        repeat(columns) { slot ->
            if (slot == middle) {
                AppCell(
                    app = app,
                    spec = spec,
                    labelStyle = labelStyle,
                    labelHeight = 0.dp,
                    repository = repository,
                    interactive = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Where a name of the person's own is typed for the app, as a row like the others of its block:
 * "Name" and under it the name itself, which is written right there. Empty, the app keeps the
 * name it came with, and a hint says a new one can be typed. A tap anywhere on the row starts
 * typing.
 */
@Composable
private fun NameRow(typed: String, onType: (String) -> Unit) {
    val colors = KiriTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    val label = stringResource(R.string.app_name_label)
    val hint = stringResource(R.string.app_name_hint)
    // What is written here is as large as the label above it, with room around it to type.
    val valueStyle = KiriType.body.copy(color = colors.onSurface, lineHeight = 26.sp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                field.requestFocus()
                keyboard?.show()
            }
            .heightIn(min = 72.dp)
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        BasicText(text = label, style = KiriType.body.copy(color = colors.onSurface))
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = typed,
            onValueChange = onType,
            singleLine = true,
            textStyle = valueStyle,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    focus.clearFocus()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(field)
                .semantics { contentDescription = label },
            decorationBox = { text ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (typed.isEmpty()) {
                        BasicText(text = hint, style = valueStyle.copy(color = colors.onSurfaceMuted.copy(alpha = 0.7f)))
                    }
                    text()
                }
            },
        )
    }
}

/** How long typing has to pause before the name is saved. */
private const val NAME_PAUSE_MS = 500L

/** The icon the app came with, to go back to. */
@Composable
private fun OriginalRow(app: AppEntry, repository: AppRepository, tilePx: Int, selected: Boolean, onClick: () -> Unit) {
    val original = stringResource(R.string.icon_picker_original)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val ownIcon by produceState<ImageBitmap?>(null, app.key) {
            value = repository.loadOwnIcon(app, tilePx)
        }
        IconTile(
            bitmap = ownIcon,
            description = original,
            selected = selected,
            onClick = onClick,
            modifier = Modifier.width(TileSize),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(text = original, style = KiriType.body.copy(color = KiriTheme.colors.onSurface))
    }
}

/** The name of a pack, which opens it in place, and closes it again. */
@Composable
private fun PackHeader(label: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KiriTheme.colors
    val turn = animateFloatAsState(if (open) 90f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "pack")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(TileShape)
            .background(colors.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BasicText(
            text = label,
            style = KiriType.body.copy(color = colors.onSurface),
            modifier = Modifier.weight(1f),
        )
        GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(18.dp).graphicsLayer { rotationZ = turn.value })
    }
}

/** Where the name of an app is typed to find its icons in every pack. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = KiriTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val clear = stringResource(R.string.icon_picker_search_clear)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = KiriType.body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(SearchShape)
                    .background(colors.control)
                    .padding(start = 14.dp),
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        BasicText(text = placeholder, style = KiriType.body.copy(color = colors.onSurfaceMuted))
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button) { onValueChange("") }
                            .semantics { contentDescription = clear },
                    ) {
                        GlyphIcon(Glyph.CLOSE, colors.onSurfaceMuted, Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(14.dp))
                }
            }
        },
    )
}

/** How long typing has to pause before the packs are searched. */
private const val SEARCH_PAUSE_MS = 180L

@Composable
private fun PackIconTile(
    repository: AppRepository,
    choice: IconChoice,
    sizePx: Int,
    current: IconChoice?,
    onChoose: (IconChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cached = remember(choice, sizePx) { repository.iconPacks.cachedBitmap(choice, sizePx) }
    val bitmap = if (cached != null) {
        cached
    } else {
        produceState<ImageBitmap?>(null, choice, sizePx) {
            value = repository.iconPacks.bitmap(choice, sizePx)
        }.value
    }
    IconTile(
        bitmap = bitmap,
        description = choice.drawable.replace('_', ' '),
        selected = choice == current,
        onClick = { onChoose(choice) },
        modifier = modifier,
    )
}

/** One icon to pick. The one in use is outlined. */
@Composable
private fun IconTile(
    bitmap: ImageBitmap?,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(colors.card)
            .then(if (selected) Modifier.border(2.dp, colors.accent, TileShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(8.dp),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
