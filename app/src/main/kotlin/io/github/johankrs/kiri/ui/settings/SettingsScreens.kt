package io.github.johankrs.kiri.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.GridField
import io.github.johankrs.kiri.core.GridPlace
import io.github.johankrs.kiri.core.GridProfile
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.HomeGridGeometry
import io.github.johankrs.kiri.core.HomeRoom
import io.github.johankrs.kiri.core.Surface
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.gesture.GestureMap
import io.github.johankrs.kiri.core.gesture.LauncherAction
import io.github.johankrs.kiri.core.look.AppOpenStyle
import io.github.johankrs.kiri.core.look.DrawerLook
import io.github.johankrs.kiri.core.look.DrawerStyle
import io.github.johankrs.kiri.core.look.HomeReturnStyle
import io.github.johankrs.kiri.core.look.MotionSettings
import io.github.johankrs.kiri.data.WallpaperReading
import io.github.johankrs.kiri.ui.actionLabel
import io.github.johankrs.kiri.ui.gestureLabel
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlin.math.roundToInt

/** The first settings screen: one row per area of the launcher. */
@Composable
fun SettingsIndexScreen(
    onBack: () -> Unit,
    /** Null where there is no home screen to adjust (a TV, for now). */
    onOpenHomeGrid: (() -> Unit)?,
    /** Null where there is no drawer (a TV: its apps are in "All apps" on its home screen). */
    onOpenDrawerGrid: (() -> Unit)?,
    /** Null where the home screen gestures do not exist (a TV, for now). */
    onOpenGestures: (() -> Unit)?,
    onOpenDrawerLook: (() -> Unit)?,
    onOpenIcons: () -> Unit,
    onOpenMotion: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenTypography: () -> Unit,
    /** Leads to the screen of the widgets. Null where there is no home screen to put them on. */
    onOpenWidgets: (() -> Unit)? = null,
    /** Kept by the caller, so the list is where it was left on the way back from a screen. */
    scroll: ScrollState = rememberScrollState(),
    /** Null where there are no folders (a TV, for now). */
    onOpenFolders: (() -> Unit)? = null,
    /** Leads to the list that hides apps and shows them again. */
    onOpenHiddenApps: (() -> Unit)? = null,
    /** How many apps are hidden now, for the row that leads there. */
    hiddenApps: Int = 0,
    /** Leads to the color of Kiri's own icon. */
    onOpenAppIcon: (() -> Unit)? = null,
    /** Leads to saving everything to a file and bringing it back. */
    onOpenBackup: (() -> Unit)? = null,
    /** When the last backup was saved, for the row that leads there; null with none. */
    lastBackupAt: Long? = null,
    /** Leads to how the TV's home screen looks; null where there is none. */
    onOpenTvScreen: (() -> Unit)? = null,
) {
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack, focusFirst = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            SectionTitle(stringResource(R.string.settings_section_look))
            SettingsGroup {
                SettingsLinkRow(
                    title = stringResource(R.string.settings_wallpaper),
                    summary = stringResource(R.string.settings_wallpaper_summary),
                    onClick = onOpenWallpaper,
                )
                if (onOpenDrawerLook != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_drawer_look),
                        summary = stringResource(R.string.settings_drawer_look_summary),
                        onClick = onOpenDrawerLook,
                    )
                }
                SettingsLinkRow(
                    title = stringResource(R.string.settings_icons),
                    summary = stringResource(R.string.settings_icons_summary),
                    onClick = onOpenIcons,
                )
                if (onOpenAppIcon != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_app_icon),
                        summary = stringResource(R.string.settings_app_icon_summary),
                        onClick = onOpenAppIcon,
                    )
                }
                if (onOpenFolders != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_folders),
                        summary = stringResource(R.string.settings_folders_summary),
                        onClick = onOpenFolders,
                    )
                }
                SettingsLinkRow(
                    title = stringResource(R.string.settings_typography),
                    summary = stringResource(R.string.settings_typography_summary),
                    onClick = onOpenTypography,
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_motion),
                    summary = stringResource(R.string.settings_motion_summary),
                    onClick = onOpenMotion,
                )
            }

            SectionTitle(stringResource(R.string.settings_section_layout))
            SettingsGroup {
                if (onOpenTvScreen != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_tv_screen),
                        summary = stringResource(R.string.settings_tv_screen_summary),
                        onClick = onOpenTvScreen,
                    )
                }
                if (onOpenHomeGrid != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_home),
                        summary = stringResource(R.string.settings_home_summary),
                        onClick = onOpenHomeGrid,
                    )
                }
                if (onOpenDrawerGrid != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_drawer),
                        summary = stringResource(R.string.settings_drawer_summary),
                        onClick = onOpenDrawerGrid,
                    )
                }
                if (onOpenHiddenApps != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_hidden_apps),
                        summary = if (hiddenApps == 0) {
                            stringResource(R.string.settings_hidden_apps_none)
                        } else {
                            pluralStringResource(R.plurals.hidden_apps_count, hiddenApps, hiddenApps)
                        },
                        onClick = onOpenHiddenApps,
                    )
                }
                // Widgets have a screen of their own; this is only one more way to reach it.
                if (onOpenWidgets != null) {
                    SettingsLinkRow(
                        title = stringResource(R.string.widgets_title),
                        summary = stringResource(R.string.settings_widgets_summary),
                        onClick = onOpenWidgets,
                    )
                }
            }

            if (onOpenGestures != null) {
                SectionTitle(stringResource(R.string.settings_section_gestures))
                SettingsGroup {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_gestures),
                        summary = stringResource(R.string.settings_gestures_summary),
                        onClick = onOpenGestures,
                    )
                }
            }

            if (onOpenBackup != null) {
                val context = LocalContext.current
                SectionTitle(stringResource(R.string.settings_section_backup))
                SettingsGroup {
                    SettingsLinkRow(
                        title = stringResource(R.string.settings_backup),
                        summary = lastBackupAt?.let { stringResource(R.string.settings_backup_last, whenText(context, it, inline = true)) }
                            ?: stringResource(R.string.settings_backup_none),
                        onClick = onOpenBackup,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Chooses how things move: opening an app, coming back to the launcher, the drawer, and how
 * fast it all goes. A small strip at the top moves at the chosen speed after every change.
 */
@Composable
fun MotionSettingsScreen(
    motion: MotionSettings,
    onChange: (MotionSettings) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf(motion) }
    val change: (MotionSettings) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    // Which list of choices is open, if any: 0 opening apps, 1 coming back, 2 the drawer.
    var open by remember { mutableIntStateOf(-1) }
    val muted = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted)

    SettingsScaffold(title = stringResource(R.string.settings_motion), onBack = onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SpeedPreview(draft)
            SettingsGroup {
            StepperCard(
                label = stringResource(R.string.motion_speed),
                valueText = "${draft.speed}",
                canDecrease = draft.speed > MotionSettings.SPEED.first,
                canIncrease = draft.speed < MotionSettings.SPEED.last,
                onDecrease = { change(draft.copy(speed = draft.speed - 1)) },
                onIncrease = { change(draft.copy(speed = draft.speed + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )

            val drawerStyles = DrawerStyle.entries
            ChoiceCard(
                label = stringResource(R.string.motion_drawer),
                valueText = stringResource(draft.drawer.labelRes()),
                expanded = open == 2,
                onToggle = { open = if (open == 2) -1 else 2 },
                options = drawerStyles.map { stringResource(it.labelRes()) },
                selectedIndex = drawerStyles.indexOf(draft.drawer),
                onSelect = { index ->
                    change(draft.copy(drawer = drawerStyles[index]))
                    open = -1
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val openStyles = AppOpenStyle.entries
            ChoiceCard(
                label = stringResource(R.string.motion_app_open),
                valueText = stringResource(draft.appOpen.labelRes()),
                expanded = open == 0,
                onToggle = { open = if (open == 0) -1 else 0 },
                options = openStyles.map { stringResource(it.labelRes()) },
                selectedIndex = openStyles.indexOf(draft.appOpen),
                onSelect = { index ->
                    change(draft.copy(appOpen = openStyles[index]))
                    open = -1
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val returnStyles = HomeReturnStyle.entries
            ChoiceCard(
                label = stringResource(R.string.motion_home_return),
                valueText = stringResource(draft.homeReturn.labelRes()),
                expanded = open == 1,
                onToggle = { open = if (open == 1) -1 else 1 },
                options = returnStyles.map { stringResource(it.labelRes()) },
                selectedIndex = returnStyles.indexOf(draft.homeReturn),
                onSelect = { index ->
                    change(draft.copy(homeReturn = returnStyles[index]))
                    open = -1
                },
                modifier = Modifier.fillMaxWidth(),
            )
            }
            BasicText(
                text = stringResource(R.string.motion_home_return_note),
                style = muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A dot that crosses a strip every time the speed changes, moving the way the launcher will. */
@Composable
private fun SpeedPreview(motion: MotionSettings) {
    val colors = KiriTheme.colors
    val side = animateFloatAsState(
        targetValue = if (motion.speed % 2 == 0) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * motion.stiffnessScale,
        ),
        label = "speed",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(12.dp)
            .clearAndSetSemantics { },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val travel = maxWidth - 32.dp
            Box(
                Modifier
                    .size(32.dp)
                    .graphicsLayer { translationX = side.value * travel.toPx() }
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

/**
 * Adjusts the tint of the app drawer and the blur behind all glass, while [preview] shows the
 * drawer live. It also says where the blur comes from on this device: from Android
 * ([systemBlur]), from Kiri's own reading of the wallpaper ([reading]), or from neither, and
 * lets the person give Kiri that reading with [onAllowWallpaper].
 */
@Composable
fun DrawerLookScreen(
    look: DrawerLook,
    onChange: (DrawerLook) -> Unit,
    onBack: () -> Unit,
    systemBlur: Boolean,
    reading: WallpaperReading?,
    onAllowWallpaper: () -> Unit,
    preview: @Composable (DrawerLook) -> Unit,
) {
    var draft by remember { mutableStateOf(look) }
    val change: (DrawerLook) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    SettingsScaffold(title = stringResource(R.string.settings_drawer_look), onBack = onBack) {
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                LivePreview(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 16.dp),
                ) { preview(draft) }
                DrawerLookControls(
                    look = draft,
                    onChange = change,
                    systemBlur = systemBlur,
                    reading = reading,
                    onAllowWallpaper = onAllowWallpaper,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            LivePreview(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { preview(draft) }
            Spacer(Modifier.height(12.dp))
            DrawerLookControls(
                look = draft,
                onChange = change,
                systemBlur = systemBlur,
                reading = reading,
                onAllowWallpaper = onAllowWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun DrawerLookControls(
    look: DrawerLook,
    onChange: (DrawerLook) -> Unit,
    systemBlur: Boolean,
    reading: WallpaperReading?,
    onAllowWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var glassOpen by remember { mutableStateOf(false) }
    val styles = GlassStyle.entries

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SettingsGroup {
        ChoiceCard(
            label = stringResource(R.string.drawer_look_tint),
            valueText = stringResource(look.glass.labelRes()),
            expanded = glassOpen,
            onToggle = { glassOpen = !glassOpen },
            options = styles.map { stringResource(it.labelRes()) },
            selectedIndex = styles.indexOf(look.glass),
            onSelect = { index ->
                onChange(look.copy(glass = styles[index]))
                glassOpen = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        StepperCard(
            label = stringResource(R.string.drawer_look_opacity),
            valueText = "${look.opacityPercent}%",
            canDecrease = look.opacityPercent > DrawerLook.OPACITY_PERCENT.first,
            canIncrease = look.opacityPercent < DrawerLook.OPACITY_PERCENT.last,
            onDecrease = { onChange(look.withOpacity(look.opacityPercent - DrawerLook.OPACITY_STEP)) },
            onIncrease = { onChange(look.withOpacity(look.opacityPercent + DrawerLook.OPACITY_STEP)) },
        )
        StepperCard(
            label = stringResource(R.string.drawer_look_blur),
            valueText = levelText(look.blurDp, DrawerLook.BLUR_DP, DrawerLook.BLUR_STEP),
            canDecrease = look.blurDp > DrawerLook.BLUR_DP.first,
            canIncrease = look.blurDp < DrawerLook.BLUR_DP.last,
            onDecrease = { onChange(look.withBlur(look.blurDp - DrawerLook.BLUR_STEP)) },
            onIncrease = { onChange(look.withBlur(look.blurDp + DrawerLook.BLUR_STEP)) },
        )
        }

        SettingsGroup {
        // Where the blur comes from on this device, so that "nothing is blurred" has a reason.
        InfoCard(
            title = stringResource(R.string.blur_system_title),
            text = stringResource(
                when {
                    systemBlur -> R.string.blur_system_on
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> R.string.blur_system_old
                    else -> R.string.blur_system_off
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        InfoCard(
            title = stringResource(R.string.blur_copy_title),
            text = stringResource(
                when (reading) {
                    WallpaperReading.READY -> R.string.blur_copy_ready
                    WallpaperReading.ANIMATED -> R.string.blur_copy_animated
                    WallpaperReading.UNREADABLE -> R.string.blur_copy_unreadable
                    WallpaperReading.NOT_ALLOWED, null -> R.string.blur_copy_not_allowed
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (reading == WallpaperReading.NOT_ALLOWED) {
            SettingsLinkRow(
                title = stringResource(R.string.blur_allow),
                summary = stringResource(R.string.blur_allow_summary),
                onClick = onAllowWallpaper,
            )
        }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun AppOpenStyle.labelRes(): Int = when (this) {
    AppOpenStyle.FROM_ICON -> R.string.motion_app_open_icon
    AppOpenStyle.SLIDE_UP -> R.string.motion_slide_up
    AppOpenStyle.FADE -> R.string.motion_fade
    AppOpenStyle.SYSTEM -> R.string.motion_system
}

private fun HomeReturnStyle.labelRes(): Int = when (this) {
    HomeReturnStyle.ZOOM -> R.string.motion_zoom
    HomeReturnStyle.FADE -> R.string.motion_fade
    HomeReturnStyle.NONE -> R.string.motion_none
}

private fun DrawerStyle.labelRes(): Int = when (this) {
    DrawerStyle.SLIDE -> R.string.motion_slide_up
    DrawerStyle.ZOOM -> R.string.motion_zoom
    DrawerStyle.FADE -> R.string.motion_fade
}

internal fun GlassStyle.labelRes(): Int = when (this) {
    GlassStyle.FUME -> R.string.market_glass_fume
    GlassStyle.CRISTAL -> R.string.market_glass_cristal
    GlassStyle.NEVOA -> R.string.market_glass_nevoa
}

/**
 * Chooses what each home screen gesture does. Tapping a gesture opens its list of actions;
 * the choice is saved as soon as it is made.
 */
@Composable
fun GestureSettingsScreen(
    gestures: GestureMap,
    appsByKey: Map<String, AppEntry>,
    onChange: (Gesture, LauncherAction) -> Unit,
    onBack: () -> Unit,
    lockReady: Boolean = true,
    onTurnOnLock: () -> Unit = {},
) {
    var openGesture by remember { mutableStateOf<Gesture?>(null) }
    val muted = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted)

    SettingsScaffold(title = stringResource(R.string.settings_gestures), onBack = onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = stringResource(R.string.gestures_caption),
                style = muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsGroup {
            for (gesture in Gesture.entries) {
                val chosen = gestures.chosenFor(gesture)
                // An action that needs more than a tap to pick (an app) is listed only while chosen.
                val offered = LauncherAction.simple.let { if (chosen in it) it else it + chosen }
                ChoiceCard(
                    label = gestureLabel(gesture),
                    valueText = actionLabel(chosen, appsByKey),
                    expanded = openGesture == gesture,
                    onToggle = { openGesture = if (openGesture == gesture) null else gesture },
                    options = offered.map { actionLabel(it, appsByKey) },
                    selectedIndex = offered.indexOf(chosen),
                    onSelect = { index ->
                        onChange(gesture, offered[index])
                        openGesture = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Locking the screen needs Kiri turned on in Android's accessibility settings.
            if (gestures.locksScreen && !lockReady) {
                SettingsLinkRow(
                    title = stringResource(R.string.lock_turn_on),
                    summary = stringResource(R.string.lock_turn_on_summary),
                    onClick = onTurnOnLock,
                )
            }
            }
            if (!gestures.settingsReachable) {
                BasicText(
                    text = stringResource(R.string.gestures_settings_fallback),
                    style = muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Adjusts one grid with plus and minus buttons while [preview] shows the result live.
 *
 * The screen keeps its own working copy, so the preview follows every tap immediately;
 * [onSpecChange] saves each change as it happens.
 */
@Composable
fun GridSettingsScreen(
    profile: GridProfile,
    spec: GridSpec,
    onSpecChange: (GridSpec) -> Unit,
    onBack: () -> Unit,
    preview: @Composable (GridSpec) -> Unit,
    room: HomeRoom? = null,
) {
    var draft by remember(profile) { mutableStateOf(spec) }
    val change: (GridSpec) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onSpecChange(next)
        }
    }

    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    SettingsScaffold(title = stringResource(profile.surface.titleRes()), onBack = onBack) {
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                LivePreview(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 16.dp),
                ) { preview(draft) }
                GridControls(
                    profile = profile,
                    spec = draft,
                    onChange = change,
                    room = room,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            LivePreview(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { preview(draft) }
            Spacer(Modifier.height(12.dp))
            GridControls(
                profile = profile,
                spec = draft,
                onChange = change,
                room = room,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * Shows [content] exactly as it would fill the whole screen, shrunk to fit the space given.
 * Because the content is the real composable and not a drawing of it, what is seen here is what
 * the launcher will show.
 */
@Composable
private fun LivePreview(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / screenWidth, maxHeight / screenHeight)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(screenWidth * scale, screenHeight * scale)
                .clip(RoundedCornerShape(16.dp))
                // A picture for sighted adjustment only: nothing in it should be announced.
                .clearAndSetSemantics { },
        ) {
            Box(
                Modifier
                    .requiredSize(screenWidth, screenHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GridControls(
    profile: GridProfile,
    spec: GridSpec,
    onChange: (GridSpec) -> Unit,
    modifier: Modifier = Modifier,
    room: HomeRoom? = null,
) {
    // On the home screen a step that would no longer fit is not offered: the icons must fit
    // side by side, and the rows with the room asked between them. A grid that already did
    // not fit can still be changed every way.
    val home = profile.surface == Surface.HOME
    val fitsNow = room == null || HomeGridGeometry.fits(spec, room)
    val canGrow: (GridSpec) -> Boolean = { next -> !fitsNow || room == null || HomeGridGeometry.fits(next, room) }
    val step: @Composable (GridField) -> Unit = { field ->
        val value = field.get(spec)
        StepperCard(
            label = stringResource(field.labelRes(profile.surface)),
            // Columns and rows are counted. The rest are sizes: a level says more
            // than a measure would.
            valueText = when {
                field.isCount() -> "$value"
                else -> levelText(value, field.range, fromOne = field == GridField.ICON_SIZE)
            },
            canDecrease = value > field.range.first,
            canIncrease = value < field.range.last && (!home || canGrow(field.set(spec, value + 1))),
            onDecrease = { onChange(field.set(spec, value - 1)) },
            onIncrease = { onChange(field.set(spec, value + 1)) },
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = stringResource(
                R.string.grid_profile_caption,
                stringResource(profile.formFactor.nameRes()),
                stringResource(if (profile.landscape) R.string.orientation_landscape else R.string.orientation_portrait),
            ),
            style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SettingsGroup {
            val fields = GridField.forSurface(profile.surface)
            // On the home screen the vertical room and where the rows sit come right after the
            // side margin, before the margins above and below them.
            val sideways = if (home) fields.takeWhile { it != GridField.MARGIN_TOP } else fields
            for (field in sideways) step(field)
            if (home) {
                ToggleCard(
                    label = stringResource(R.string.grid_even_gaps),
                    checked = spec.evenGaps,
                    onCheckedChange = { even ->
                        // The room between rows starts where it was, so nothing jumps.
                        val shown = room?.let { HomeGridGeometry.of(spec, it).shownGapYDp.roundToInt() } ?: spec.rowGapDp
                        onChange(spec.copy(evenGaps = even, rowGapDp = if (even) spec.rowGapDp else shown.coerceIn(GridSpec.GAP_DP)))
                    },
                )
                if (!spec.evenGaps) step(GridField.ROW_GAP)
                val places = GridPlace.entries
                val placeNames = places.map { stringResource(it.nameRes()) }
                val at = places.indexOf(spec.place)
                StepperCard(
                    label = stringResource(R.string.grid_place),
                    valueText = placeNames[at],
                    values = placeNames,
                    named = true,
                    canDecrease = at > 0,
                    canIncrease = at < places.size - 1,
                    onDecrease = { onChange(spec.copy(place = places[at - 1])) },
                    onIncrease = { onChange(spec.copy(place = places[at + 1])) },
                )
                for (field in fields.drop(sideways.size)) step(field)
            }
            ToggleCard(
                label = stringResource(R.string.grid_show_labels),
                checked = spec.showLabels,
                onCheckedChange = { onChange(spec.copy(showLabels = it)) },
            )
            if (profile.surface == Surface.HOME) {
                ToggleCard(
                    label = stringResource(R.string.grid_page_dots),
                    checked = spec.showPageDots,
                    onCheckedChange = { onChange(spec.copy(showPageDots = it)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun Surface.titleRes(): Int = when (this) {
    Surface.HOME -> R.string.settings_home
    Surface.DRAWER -> R.string.settings_drawer
}

private fun FormFactor.nameRes(): Int = when (this) {
    FormFactor.PHONE -> R.string.device_phone
    FormFactor.TABLET -> R.string.device_tablet
    FormFactor.TV -> R.string.device_tv
}

private fun GridField.isCount(): Boolean = this == GridField.COLUMNS || this == GridField.ROWS

/**
 * [value] as a level: how many steps it is above the least it can be. People think of "a bit
 * more" and "a bit less", not of measures, so a size or a margin is shown as its level and
 * never with a unit. [fromOne] is for what cannot be nothing, like a size.
 */
internal fun levelText(value: Int, range: IntRange, step: Int = 1, fromOne: Boolean = false): String =
    "${(value - range.first) / step + if (fromOne) 1 else 0}"

private fun GridPlace.nameRes(): Int = when (this) {
    GridPlace.TOP -> R.string.grid_place_top
    GridPlace.MIDDLE -> R.string.grid_place_middle
    GridPlace.BOTTOM -> R.string.grid_place_bottom
}

private fun GridField.labelRes(surface: Surface): Int = when (this) {
    GridField.COLUMNS -> R.string.grid_columns
    GridField.ROWS -> R.string.grid_rows
    GridField.MARGIN_HORIZONTAL -> R.string.grid_margin_horizontal
    GridField.MARGIN_TOP -> R.string.grid_margin_top
    GridField.MARGIN_BOTTOM -> R.string.grid_margin_bottom
    GridField.GAP -> if (surface == Surface.DRAWER) R.string.grid_gap_rows else R.string.grid_gap
    GridField.ICON_SIZE -> R.string.grid_icon_size
    GridField.ROW_GAP -> R.string.grid_row_gap
}
