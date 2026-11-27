package io.github.johankrs.kiri.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.backup.BackupFile
import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.backup.BackupSummary
import io.github.johankrs.kiri.core.backup.SecretLock
import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.data.Backups
import io.github.johankrs.kiri.data.FoundBackup
import io.github.johankrs.kiri.data.LastBackup
import io.github.johankrs.kiri.data.OpenedBackup
import io.github.johankrs.kiri.data.RestoreReport
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen is busy with, while it is. */
private enum class Busy { SAVING, OPENING, UNDOING }

/** What went wrong last, said once at the top of the screen. */
private enum class Problem(val text: Int) {
    SAVE_FAILED(R.string.backup_save_failed),
    PASSWORD_LOST(R.string.backup_password_lost),
    NOT_A_BACKUP(R.string.backup_not_a_backup),
    UNDO_FAILED(R.string.backup_undo_failed),
}

/**
 * Saves all of Kiri to one file and brings it back. Saving and opening go through Android's
 * file picker, which offers Google Drive too; where there is no picker (some TVs), backups go
 * to and are found in Download/Kiri.
 *
 * Above, what goes in the file, counted. The market keys go only when asked, locked with a
 * password typed right there. Below, opening a backup leads to [onOpened]'s screen, where
 * what comes back is chosen; after a restore, it can be undone from here.
 */
@Composable
fun BackupScreen(
    backups: Backups,
    formFactor: FormFactor,
    /** The names of the apps, by `AppEntry.key`, for the file to name the ones a restore misses. */
    appNames: () -> Map<String, String>,
    isInstalled: (appKey: String) -> Boolean,
    /** The screen in pixels, to make the wallpaper again when a restore is undone. */
    screen: IntSize,
    /** Whether Kiri may see files it did not write itself, for the Download/Kiri list. */
    canSeeFiles: Boolean,
    onAllowFiles: () -> Unit,
    canAllowFiles: Boolean = true,
    onOpened: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<Busy?>(null) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var withKeys by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundBackup>?>(null) }
    val report = backups.report
    val current by produceState<Backups.Current?>(null, formFactor, report) { value = backups.current(formFactor) }
    // A report is said while this screen is open, and then it is done.
    DisposableEffect(backups) { onDispose { backups.report = null } }

    val passwordOk = password.length >= SecretLock.MIN_PASSWORD && password == again
    val keysWanted = withKeys && current?.hasKeys == true
    val latestPassword by rememberUpdatedState(password.takeIf { keysWanted && passwordOk })
    val latestKeysWanted by rememberUpdatedState(keysWanted)

    fun afterSave(saved: Boolean) {
        busy = null
        problem = if (saved) null else Problem.SAVE_FAILED
        if (saved) {
            password = ""
            again = ""
            withKeys = false
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFile.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val typed = latestPassword
        if (latestKeysWanted && typed == null) {
            problem = Problem.PASSWORD_LOST
            return@rememberLauncherForActivityResult
        }
        busy = Busy.SAVING
        scope.launch { afterSave(backups.save(uri, appNames(), formFactor, typed?.toCharArray())) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = Busy.OPENING
        scope.launch {
            val read = backups.open(uri, formFactor, isInstalled)
            busy = null
            problem = if (read) null else Problem.NOT_A_BACKUP
            if (read) onOpened()
        }
    }
    val save: () -> Unit = save@{
        if (busy != null || (keysWanted && !passwordOk)) return@save
        problem = null
        try {
            create.launch(backups.suggestedName())
        } catch (_: ActivityNotFoundException) {
            // No file picker here (some TVs): the backup goes to Download/Kiri.
            busy = Busy.SAVING
            val typed = password.takeIf { keysWanted }
            scope.launch {
                afterSave(backups.saveToDownloads(appNames(), formFactor, typed?.toCharArray()))
                found = backups.findInDownloads()
            }
        }
    }
    val choose: () -> Unit = choose@{
        if (busy != null) return@choose
        problem = null
        try {
            open.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            scope.launch { found = backups.findInDownloads() }
        }
    }
    val openFound: (FoundBackup) -> Unit = { file ->
        if (busy == null) {
            busy = Busy.OPENING
            scope.launch {
                val read = backups.open(file.uri, formFactor, isInstalled)
                busy = null
                problem = if (read) null else Problem.NOT_A_BACKUP
                if (read) onOpened()
            }
        }
    }
    val undo: () -> Unit = undo@{
        if (busy != null) return@undo
        problem = null
        busy = Busy.UNDOING
        scope.launch {
            val done = backups.undo(screen, density.density)
            busy = null
            if (done == null) problem = Problem.UNDO_FAILED
        }
    }

    SettingsScaffold(title = stringResource(R.string.backup_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = stringResource(R.string.backup_caption),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
            )
            if (report != null) {
                Spacer(Modifier.height(8.dp))
                InfoCard(
                    title = stringResource(if (report.undone) R.string.backup_undone else R.string.backup_restored),
                    text = reportText(report),
                )
            }
            problem?.let { shown ->
                Spacer(Modifier.height(8.dp))
                InfoCard(title = stringResource(R.string.backup_title), text = stringResource(shown.text))
            }

            SectionTitle(stringResource(R.string.backup_section_last))
            SettingsGroup { LastRow(backups.last) }

            SectionTitle(stringResource(R.string.backup_section_contents))
            SettingsGroup {
                val summary = current?.summary
                InfoCard(stringResource(R.string.backup_part_home), homeText(summary))
                InfoCard(stringResource(R.string.backup_part_icons), iconsText(summary))
                InfoCard(stringResource(R.string.backup_part_hidden), hiddenText(summary))
                InfoCard(stringResource(R.string.backup_part_look), stringResource(R.string.backup_part_look_summary))
                if (current?.hasWallpaper == true) {
                    InfoCard(stringResource(R.string.backup_part_wallpaper), stringResource(R.string.backup_part_wallpaper_save))
                }
                if (current?.hasMarket == true) {
                    InfoCard(stringResource(R.string.backup_part_market), marketText(summary))
                }
                if (current?.hasKeys == true) {
                    ToggleCard(
                        label = stringResource(R.string.backup_keys),
                        summary = stringResource(if (withKeys) R.string.backup_keys_on else R.string.backup_keys_off),
                        checked = withKeys,
                        onCheckedChange = { withKeys = it },
                    )
                    if (withKeys) {
                        PasswordRow(
                            label = stringResource(R.string.backup_password),
                            hint = stringResource(R.string.backup_password_hint),
                            value = password,
                            onValueChange = { password = it },
                            note = stringResource(R.string.backup_password_short)
                                .takeIf { password.isNotEmpty() && password.length < SecretLock.MIN_PASSWORD && again.isNotEmpty() },
                        )
                        PasswordRow(
                            label = stringResource(R.string.backup_password_again),
                            hint = stringResource(R.string.backup_password_again_hint),
                            value = again,
                            onValueChange = { again = it },
                            note = stringResource(R.string.backup_password_differ)
                                .takeIf { again.isNotEmpty() && again != password && again.length >= password.length },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                ActionRow(
                    glyph = Glyph.SAVE,
                    title = stringResource(R.string.backup_save),
                    summary = when {
                        busy == Busy.SAVING -> stringResource(R.string.backup_saving)
                        keysWanted && !passwordOk -> stringResource(R.string.backup_save_needs_password)
                        formFactor == FormFactor.TV -> stringResource(R.string.backup_save_tv)
                        else -> stringResource(R.string.backup_save_summary)
                    },
                    primary = true,
                    enabled = busy == null && (!keysWanted || passwordOk),
                    onClick = save,
                )
            }

            SectionTitle(stringResource(R.string.backup_section_restore))
            SettingsGroup {
                ActionRow(
                    glyph = Glyph.OPEN,
                    title = stringResource(R.string.backup_open),
                    summary = stringResource(if (busy == Busy.OPENING) R.string.backup_opening else R.string.backup_open_summary),
                    enabled = busy == null,
                    leads = true,
                    onClick = choose,
                )
                backups.undoAt?.let { at ->
                    ActionRow(
                        glyph = Glyph.UNDO,
                        title = stringResource(R.string.backup_undo),
                        summary = if (busy == Busy.UNDOING) {
                            stringResource(R.string.backup_undoing)
                        } else {
                            stringResource(R.string.backup_undo_summary, whenText(context, at, inline = true))
                        },
                        enabled = busy == null,
                        onClick = undo,
                    )
                }
            }

            found?.let { files ->
                SectionTitle(stringResource(R.string.backup_section_downloads))
                SettingsGroup {
                    if (files.isEmpty()) {
                        InfoCard(stringResource(R.string.backup_section_downloads), stringResource(R.string.backup_downloads_none))
                    }
                    for (file in files) {
                        ActionRow(
                            glyph = Glyph.FILE,
                            title = file.name,
                            summary = stringResource(R.string.backup_when_where, whenText(context, file.at), Formatter.formatShortFileSize(context, file.size)),
                            enabled = busy == null,
                            leads = true,
                            onClick = { openFound(file) },
                        )
                    }
                    // Not in a build without access to all files (Google Play's): opening one still works.
                    if (!canSeeFiles && canAllowFiles) {
                        ActionRow(
                            glyph = Glyph.INFO,
                            title = stringResource(R.string.backup_allow_files),
                            summary = stringResource(R.string.backup_allow_files_summary),
                            enabled = true,
                            leads = true,
                            onClick = onAllowFiles,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The backup [OpenedBackup] being restored: where it is from, which of its parts come back
 * (all of them, until one is turned off), the password that opens its market keys, and what
 * this device misses of it. [onRestored] is told once it is done.
 */
@Composable
fun RestoreScreen(
    backups: Backups,
    opened: OpenedBackup,
    screen: IntSize,
    onRestored: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var parts by remember(opened) { mutableStateOf(opened.parts.toSet()) }
    var password by remember(opened) { mutableStateOf("") }
    // The keys the password opened; null while it opens nothing (or nothing was typed).
    var secrets by remember(opened) { mutableStateOf<Map<String, StoredValue>?>(null) }
    var checked by remember(opened) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val hasKeys = opened.backup.lockedSecrets != null

    // The password is tried once typing pauses: opening the lock takes a moment on purpose.
    LaunchedEffect(opened, password) {
        secrets = null
        checked = false
        if (password.length < SecretLock.MIN_PASSWORD) return@LaunchedEffect
        delay(PASSWORD_PAUSE_MS)
        secrets = withContext(Dispatchers.Default) { opened.backup.openSecrets(password.toCharArray()) }
        checked = true
    }

    val count = parts.size + if (secrets != null) 1 else 0
    val restore: () -> Unit = restore@{
        if (working || count == 0) return@restore
        working = true
        failed = false
        scope.launch {
            val report = backups.restore(opened, parts, secrets, screen, density.density)
            working = false
            if (report == null) failed = true else onRestored()
        }
    }

    SettingsScaffold(title = stringResource(R.string.restore_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val info = opened.backup.info
            SettingsGroup {
                ActionRow(
                    glyph = Glyph.FILE,
                    title = stringResource(R.string.restore_from, whenText(context, info.createdAt, sentence = false)),
                    summary = listOfNotNull(
                        info.device.takeIf { it.isNotBlank() },
                        info.appVersion.takeIf { it.isNotBlank() }?.let { "Kiri $it" },
                        opened.size?.let { Formatter.formatShortFileSize(context, it) },
                    ).joinToString(" · "),
                    enabled = true,
                    onClick = null,
                )
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                InfoCard(stringResource(R.string.restore_title), stringResource(R.string.restore_failed))
            }

            SectionTitle(stringResource(R.string.restore_section_parts))
            SettingsGroup {
                for (part in opened.parts) {
                    ToggleCard(
                        label = stringResource(part.titleRes()),
                        summary = partText(part, opened.summary),
                        checked = part in parts,
                        onCheckedChange = { on -> parts = if (on) parts + part else parts - part },
                    )
                }
                if (hasKeys) {
                    PasswordRow(
                        label = stringResource(R.string.backup_keys),
                        hint = stringResource(R.string.restore_keys_hint),
                        value = password,
                        onValueChange = { password = it },
                        note = when {
                            secrets != null -> stringResource(R.string.restore_keys_right)
                            checked -> stringResource(R.string.restore_keys_wrong)
                            else -> stringResource(R.string.restore_keys_summary)
                        },
                        noteIsGood = secrets != null,
                    )
                }
            }

            if (opened.missingApps.isNotEmpty() || opened.missingPacks.isNotEmpty()) {
                SectionTitle(stringResource(R.string.restore_section_missing))
                SettingsGroup {
                    if (opened.missingApps.isNotEmpty()) {
                        InfoCard(
                            title = pluralStringResource(R.plurals.restore_missing_apps, opened.missingApps.size, opened.missingApps.size),
                            text = stringResource(R.string.restore_missing_apps_summary, namesText(opened.missingApps)),
                        )
                    }
                    if (opened.missingPacks.isNotEmpty()) {
                        InfoCard(
                            title = pluralStringResource(R.plurals.restore_missing_packs, opened.missingPacks.size, namesText(opened.missingPacks)),
                            text = stringResource(R.string.restore_missing_packs_summary),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            BasicText(
                text = stringResource(R.string.restore_note),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                ActionRow(
                    glyph = Glyph.UNDO,
                    title = stringResource(R.string.restore_action),
                    summary = when {
                        working -> stringResource(R.string.restore_working)
                        count == 0 -> stringResource(R.string.restore_nothing)
                        else -> pluralStringResource(R.plurals.restore_parts, count, count)
                    },
                    primary = true,
                    enabled = !working && count > 0,
                    onClick = restore,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** How long typing has to pause before the password is tried on the keys. */
private const val PASSWORD_PAUSE_MS = 450L

/**
 * A row that does something: a glyph, what it does and a word on it. The [primary] one of a
 * screen has its words in the accent. [leads] adds the arrow of a row that opens something.
 * Without [onClick] it only says something.
 */
@Composable
private fun ActionRow(
    glyph: Glyph,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    primary: Boolean = false,
    leads: Boolean = false,
) {
    val colors = KiriTheme.colors
    val tint = if (primary) colors.accent else colors.onSurface
    val touch = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(touch)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
    ) {
        GlyphIcon(glyph, tint, Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = KiriType.body.copy(color = tint, fontWeight = if (primary) FontWeight.Medium else KiriType.body.fontWeight),
            )
            BasicText(text = summary, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
        }
        if (leads) {
            Spacer(Modifier.width(12.dp))
            GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(18.dp))
        }
    }
}

/** How much of a row shows while it cannot be used. */
private const val DISABLED_ALPHA = 0.45f

/**
 * A password typed right in its block: the name of the field, and under it the dots, which an
 * eye shows as letters. Empty, a [hint] says what goes there; [note] then says what is wrong
 * with it, or, [noteIsGood], that it is right. A tap anywhere on the row starts typing.
 */
@Composable
private fun PasswordRow(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    note: String? = null,
    noteIsGood: Boolean = false,
) {
    val colors = KiriTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    val valueStyle = KiriType.body.copy(color = colors.onSurface)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                field.requestFocus()
                keyboard?.show()
            }
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(text = label, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(MAX_PASSWORD)) },
                singleLine = true,
                textStyle = valueStyle,
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
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
                    .padding(top = 2.dp)
                    .focusRequester(field)
                    .semantics { contentDescription = label },
                decorationBox = { text ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            BasicText(text = hint, style = valueStyle.copy(color = colors.onSurfaceMuted.copy(alpha = 0.6f)))
                        }
                        text()
                    }
                },
            )
            if (note != null) {
                BasicText(
                    text = note,
                    style = KiriType.caption.copy(
                        color = if (noteIsGood) colors.accent else colors.onSurfaceMuted,
                        fontWeight = if (noteIsGood) FontWeight.Medium else KiriType.caption.fontWeight,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        val eyeLabel = stringResource(if (shown) R.string.backup_password_hide else R.string.backup_password_show)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = eyeLabel) { shown = !shown }
                .semantics { contentDescription = eyeLabel },
        ) {
            EyeGlyph(struck = { if (shown) 0f else 1f }, color = colors.onSurfaceMuted, modifier = Modifier.size(22.dp))
        }
    }
}

/** No password is longer than this. */
private const val MAX_PASSWORD = 128

/** The last backup: when and where, and the name of its file. */
@Composable
private fun LastRow(last: LastBackup?) {
    val context = LocalContext.current
    if (last == null) {
        ActionRow(
            glyph = Glyph.FILE,
            title = stringResource(R.string.backup_none_yet),
            summary = stringResource(R.string.backup_none_yet_hint),
            enabled = true,
            onClick = null,
        )
        return
    }
    val place = placeName(last.authority)
    val time = whenText(context, last.at)
    ActionRow(
        glyph = Glyph.FILE,
        title = if (place != null) stringResource(R.string.backup_when_where, time, place) else time,
        summary = last.name,
        enabled = true,
        onClick = null,
    )
}

/** Where a file went, by the authority of the place that took it; null for a place not known. */
@Composable
internal fun placeName(authority: String?): String? = when (authority) {
    null -> null
    Backups.DRIVE_AUTHORITY -> stringResource(R.string.backup_place_drive)
    Backups.DOWNLOADS_AUTHORITY -> stringResource(R.string.backup_place_downloads)
    in Backups.DEVICE_AUTHORITIES -> stringResource(R.string.backup_place_device)
    else -> null
}

/**
 * When something happened, as a person says it: "Today, 10:55", "Yesterday, 22:10", or the
 * date and time. With [sentence] off it is always the date and time; with [inline] today and
 * yesterday start small, to follow a colon.
 */
internal fun whenText(context: Context, at: Long, sentence: Boolean = true, inline: Boolean = false): String {
    val time = DateFormat.getTimeFormat(context).format(at)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.let {
        it.get(Calendar.YEAR) == then.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }
    return when {
        sentence && (sameDay || yesterday) -> context.getString(if (sameDay) R.string.backup_today else R.string.backup_yesterday, time)
            .let { if (inline) it.replaceFirstChar { first -> first.lowercaseChar() } else it }
        else -> {
            var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
            if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) flags = flags or DateUtils.FORMAT_NO_YEAR
            DateUtils.formatDateTime(context, at, flags)
        }
    }
}

@Composable
private fun reportText(report: RestoreReport): String = buildList {
    if (report.widgetsToPlace > 0) {
        add(pluralStringResource(R.plurals.backup_widgets_to_place, report.widgetsToPlace, report.widgetsToPlace))
    }
    if (!report.wallpaperOk) add(stringResource(R.string.backup_wallpaper_failed))
    if (isEmpty()) add(stringResource(R.string.backup_all_in_place))
}.joinToString(" ")

private fun BackupPart.titleRes(): Int = when (this) {
    BackupPart.HOME -> R.string.backup_part_home
    BackupPart.ICONS -> R.string.backup_part_icons
    BackupPart.HIDDEN -> R.string.backup_part_hidden
    BackupPart.LOOK -> R.string.backup_part_look
    BackupPart.WALLPAPER -> R.string.backup_part_wallpaper
    BackupPart.MARKET -> R.string.backup_part_market
}

@Composable
private fun partText(part: BackupPart, summary: BackupSummary): String = when (part) {
    BackupPart.HOME -> homeText(summary)
    BackupPart.ICONS -> iconsText(summary)
    BackupPart.HIDDEN -> hiddenText(summary)
    BackupPart.LOOK -> stringResource(R.string.backup_part_look_summary)
    BackupPart.WALLPAPER -> stringResource(R.string.backup_part_wallpaper_restore)
    BackupPart.MARKET -> marketText(summary)
}

/** "3 pages, 18 apps, 2 folders and 3 widgets", leaving out what there is none of. */
@Composable
private fun homeText(summary: BackupSummary?): String {
    val home = summary?.home ?: return stringResource(R.string.backup_part_home_grids)
    return listing(
        listOfNotNull(
            pluralStringResource(R.plurals.backup_pages, home.pages, home.pages),
            home.apps.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_apps, it, it) },
            home.folders.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_folders, it, it) },
            home.widgets.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_widgets, it, it) },
        ),
    )
}

@Composable
private fun iconsText(summary: BackupSummary?): String {
    val icons = summary?.icons ?: 0
    val names = summary?.names ?: 0
    if (icons == 0 && names == 0) return stringResource(R.string.backup_part_icons_none)
    return listing(
        listOfNotNull(
            icons.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_icons, it, it) },
            names.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_names, it, it) },
        ),
    )
}

@Composable
private fun hiddenText(summary: BackupSummary?): String {
    val hidden = summary?.hidden ?: 0
    return if (hidden == 0) stringResource(R.string.settings_hidden_apps_none) else pluralStringResource(R.plurals.hidden_apps_count, hidden, hidden)
}

@Composable
private fun marketText(summary: BackupSummary?): String {
    val watching = summary?.watching ?: return stringResource(R.string.backup_part_market_summary)
    return pluralStringResource(R.plurals.backup_watching, watching, watching)
}

/** "a, b and c". */
@Composable
private fun listing(items: List<String>): String {
    if (items.size <= 1) return items.firstOrNull().orEmpty()
    val and = stringResource(R.string.backup_and)
    return items.dropLast(1).joinToString(", ") + " $and " + items.last()
}

/** A few names, and how many more when there are many. */
@Composable
private fun namesText(names: List<String>): String {
    val shown = names.take(SHOWN_NAMES)
    val text = listing(shown)
    return if (names.size > SHOWN_NAMES) stringResource(R.string.restore_more, shown.joinToString(", "), names.size - SHOWN_NAMES) else text
}

/** How many missing apps are named before the rest are only counted. */
private const val SHOWN_NAMES = 4
