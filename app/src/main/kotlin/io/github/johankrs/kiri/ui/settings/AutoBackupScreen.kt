package io.github.johankrs.kiri.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.backup.AutoBackupFiles
import io.github.johankrs.kiri.core.backup.BackupFile
import io.github.johankrs.kiri.core.backup.BackupRepeat
import io.github.johankrs.kiri.core.backup.BackupSchedule
import io.github.johankrs.kiri.core.backup.BackupTarget
import io.github.johankrs.kiri.core.backup.SecretLock
import io.github.johankrs.kiri.data.AutoBackups
import io.github.johankrs.kiri.data.AutoOutcome
import io.github.johankrs.kiri.data.AutoPlace
import io.github.johankrs.kiri.data.AutoRun
import io.github.johankrs.kiri.data.Backups
import io.github.johankrs.kiri.data.PlaceKind
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The automatic backup: whether it is on and when the next one is, when it repeats, where it
 * goes and how many stay, whether the market keys go along (locked with a password typed once
 * here), and how the last one went, with a button to make one now.
 *
 * Names are gone through with arrows and numbers with minus and plus, as everywhere in Kiri.
 * On a TV the backups always go to Download/Kiri, which has no picker to choose another place.
 */
@Composable
fun AutoBackupScreen(
    auto: AutoBackups,
    backups: Backups,
    formFactor: FormFactor,
    onBack: () -> Unit,
) {
    val colors = KiriTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schedule = auto.schedule
    val tv = formFactor == FormFactor.TV
    val target = if (tv) BackupTarget.FOLDER else schedule.target
    var problem by remember { mutableStateOf<Int?>(null) }
    val update: (BackupSchedule) -> Unit = { next ->
        problem = null
        auto.update(next)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !auto.chooseFolder(uri)) problem = R.string.auto_backup_place_refused
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFile.MIME)) { uri ->
        if (uri != null && !auto.chooseFile(uri)) problem = R.string.auto_backup_place_refused
    }
    val choosePlace: () -> Unit = {
        problem = null
        try {
            if (target == BackupTarget.FILE) pickFile.launch(AutoBackupFiles.SINGLE_NAME) else pickFolder.launch(auto.folder?.uri)
        } catch (_: ActivityNotFoundException) {
            problem = R.string.auto_backup_no_picker
        }
    }

    // Whether there are market keys to carry; the block for them shows only then.
    val hasKeys by produceState(auto.keysOn, formFactor) { value = auto.keysOn || backups.current(formFactor)?.hasKeys == true }

    SettingsScaffold(title = stringResource(R.string.auto_backup_title), onBack = onBack, focusFirst = tv) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Caption(stringResource(R.string.auto_backup_caption), top = 0)
            problem?.let { shown ->
                Spacer(Modifier.height(8.dp))
                InfoCard(title = stringResource(R.string.auto_backup_title), text = stringResource(shown))
            }
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                ToggleCard(
                    label = stringResource(R.string.auto_backup_title),
                    summary = nextText(auto),
                    checked = schedule.on,
                    onCheckedChange = { update(schedule.copy(on = it)) },
                )
            }

            // Ready to be changed while off too, a little faded.
            Column(Modifier.graphicsLayer { alpha = if (schedule.on) 1f else OFF_ALPHA }) {
                SectionTitle(stringResource(R.string.auto_backup_section_when))
                SettingsGroup { WhenRows(schedule, update) }
                Caption(stringResource(R.string.auto_backup_hour_note))

                SectionTitle(stringResource(R.string.auto_backup_section_where))
                SettingsGroup {
                    if (!tv) {
                        val targets = BackupTarget.entries
                        val names = targets.map { stringResource(it.titleRes()) }
                        val index = targets.indexOf(schedule.target)
                        StepperCard(
                            label = stringResource(R.string.auto_backup_target),
                            valueText = names[index],
                            values = names,
                            named = true,
                            canDecrease = index > 0,
                            canIncrease = index < targets.lastIndex,
                            onDecrease = { update(schedule.copy(target = targets[index - 1])) },
                            onIncrease = { update(schedule.copy(target = targets[index + 1])) },
                        )
                    }
                    val place = if (target == BackupTarget.FILE) auto.file else auto.folder
                    if (tv) {
                        InfoCard(stringResource(R.string.auto_backup_folder), placeText(null, BackupTarget.FOLDER))
                    } else {
                        SettingsLinkRow(
                            title = stringResource(if (target == BackupTarget.FILE) R.string.auto_backup_file else R.string.auto_backup_folder),
                            summary = placeText(place, target),
                            onClick = choosePlace,
                        )
                    }
                    if (target == BackupTarget.FOLDER) {
                        StepperCard(
                            label = stringResource(R.string.auto_backup_keep),
                            valueText = schedule.keep.toString(),
                            values = listOf(BackupSchedule.KEEP_RANGE.last.toString()),
                            canDecrease = schedule.keep > BackupSchedule.KEEP_RANGE.first,
                            canIncrease = schedule.keep < BackupSchedule.KEEP_RANGE.last,
                            onDecrease = { update(schedule.copy(keep = schedule.keep - 1)) },
                            onIncrease = { update(schedule.copy(keep = schedule.keep + 1)) },
                        )
                    }
                    ToggleCard(
                        label = stringResource(R.string.auto_backup_only_changed),
                        summary = stringResource(R.string.auto_backup_only_changed_summary),
                        checked = schedule.onlyWhenChanged,
                        onCheckedChange = { update(schedule.copy(onlyWhenChanged = it)) },
                    )
                }
                Caption(stringResource(if (target == BackupTarget.FILE) R.string.auto_backup_file_note else R.string.auto_backup_folder_note))

                if (hasKeys) {
                    SectionTitle(stringResource(R.string.backup_keys))
                    SettingsGroup { KeyRows(auto) }
                    Caption(stringResource(R.string.auto_backup_keys_note))
                }

                SectionTitle(stringResource(R.string.auto_backup_section_last))
                SettingsGroup {
                    LastRunRow(auto.lastRun, schedule.on)
                    ActionRow(
                        glyph = Glyph.SAVE,
                        title = stringResource(R.string.auto_backup_now),
                        summary = stringResource(if (auto.busy) R.string.backup_saving else R.string.auto_backup_now_summary),
                        primary = true,
                        enabled = !auto.busy,
                        onClick = {
                            problem = null
                            scope.launch { auto.runNow() }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** How much of the settings show while the automatic backup is off. */
private const val OFF_ALPHA = 0.55f

/** The rows of when it repeats: how often, the day it needs, and the time. */
@Composable
private fun WhenRows(schedule: BackupSchedule, update: (BackupSchedule) -> Unit) {
    val context = LocalContext.current
    val repeats = BackupRepeat.entries
    val repeatNames = repeats.map { repeatText(it, schedule.everyDays) }
    val repeatIndex = repeats.indexOf(schedule.repeat)
    StepperCard(
        label = stringResource(R.string.auto_backup_repeat),
        valueText = repeatNames[repeatIndex],
        values = repeatNames + repeatText(BackupRepeat.EVERY_DAYS, BackupSchedule.EVERY_DAYS_RANGE.last),
        named = true,
        canDecrease = repeatIndex > 0,
        canIncrease = repeatIndex < repeats.lastIndex,
        onDecrease = { update(schedule.copy(repeat = repeats[repeatIndex - 1])) },
        onIncrease = { update(schedule.copy(repeat = repeats[repeatIndex + 1])) },
    )
    when (schedule.repeat) {
        BackupRepeat.DAILY -> Unit

        BackupRepeat.EVERY_DAYS -> StepperCard(
            label = stringResource(R.string.auto_backup_every),
            valueText = pluralStringResource(R.plurals.auto_backup_days, schedule.everyDays, schedule.everyDays),
            values = listOf(pluralStringResource(R.plurals.auto_backup_days, BackupSchedule.EVERY_DAYS_RANGE.last, BackupSchedule.EVERY_DAYS_RANGE.last)),
            canDecrease = schedule.everyDays > BackupSchedule.EVERY_DAYS_RANGE.first,
            canIncrease = schedule.everyDays < BackupSchedule.EVERY_DAYS_RANGE.last,
            onDecrease = { update(schedule.copy(everyDays = schedule.everyDays - 1)) },
            onIncrease = { update(schedule.copy(everyDays = schedule.everyDays + 1)) },
        )

        BackupRepeat.WEEKLY -> {
            val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            val days = remember(locale) { weekOf(locale) }
            val names = days.map { it.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { first -> first.titlecase(locale) } }
            val index = days.indexOf(schedule.weekday)
            StepperCard(
                label = stringResource(R.string.auto_backup_weekday),
                valueText = names[index],
                values = names,
                named = true,
                canDecrease = index > 0,
                canIncrease = index < days.lastIndex,
                onDecrease = { update(schedule.copy(weekday = days[index - 1])) },
                onIncrease = { update(schedule.copy(weekday = days[index + 1])) },
            )
        }

        BackupRepeat.MONTHLY -> StepperCard(
            label = stringResource(R.string.auto_backup_month_day),
            valueText = schedule.monthDay.toString(),
            values = listOf(BackupSchedule.MONTH_DAY_RANGE.last.toString()),
            canDecrease = schedule.monthDay > BackupSchedule.MONTH_DAY_RANGE.first,
            canIncrease = schedule.monthDay < BackupSchedule.MONTH_DAY_RANGE.last,
            onDecrease = { update(schedule.copy(monthDay = schedule.monthDay - 1)) },
            onIncrease = { update(schedule.copy(monthDay = schedule.monthDay + 1)) },
        )
    }
    val hours = remember(context) { BackupSchedule.HOUR_RANGE.map { hourText(context, it) } }
    StepperCard(
        label = stringResource(R.string.auto_backup_hour),
        valueText = hours[schedule.hour],
        values = hours,
        canDecrease = schedule.hour > BackupSchedule.HOUR_RANGE.first,
        canIncrease = schedule.hour < BackupSchedule.HOUR_RANGE.last,
        onDecrease = { update(schedule.copy(hour = schedule.hour - 1)) },
        onIncrease = { update(schedule.copy(hour = schedule.hour + 1)) },
    )
}

/**
 * The market keys: off, or on with the password typed twice; once both match, the key drawn
 * from it is kept, and the rows of the password go.
 */
@Composable
private fun KeyRows(auto: AutoBackups) {
    val scope = rememberCoroutineScope()
    var typing by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val ready = password.length >= SecretLock.MIN_PASSWORD && password == again

    // Kept once typing pauses on two that match: drawing the key takes a moment on purpose.
    LaunchedEffect(ready, password) {
        if (!ready || !typing) return@LaunchedEffect
        delay(KEY_PAUSE_MS)
        saving = true
        val kept = auto.setKeys(password.toCharArray())
        saving = false
        failed = !kept
        if (kept) {
            typing = false
            password = ""
            again = ""
        }
    }

    ToggleCard(
        label = stringResource(R.string.backup_keys),
        summary = stringResource(
            when {
                auto.keysOn -> R.string.auto_backup_keys_on
                failed -> R.string.auto_backup_keys_failed
                saving -> R.string.auto_backup_keys_saving
                typing -> R.string.auto_backup_keys_typing
                else -> R.string.backup_keys_off
            },
        ),
        checked = auto.keysOn || typing,
        onCheckedChange = { on ->
            failed = false
            password = ""
            again = ""
            typing = on && !auto.keysOn
            if (!on && auto.keysOn) scope.launch { auto.setKeys(null) }
        },
    )
    if (typing) {
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

/** How long typing has to pause on two passwords that match before the key is drawn. */
private const val KEY_PAUSE_MS = 600L

/** The last automatic backup: when, where, and the file; or what went wrong and what to do. */
@Composable
private fun LastRunRow(run: AutoRun?, on: Boolean) {
    val context = LocalContext.current
    if (run == null) {
        ActionRow(
            glyph = Glyph.FILE,
            title = stringResource(R.string.auto_backup_none),
            summary = stringResource(if (on) R.string.auto_backup_none_on else R.string.auto_backup_none_off),
            enabled = true,
            onClick = null,
        )
        return
    }
    val time = whenText(context, run.at)
    val where = run.kind?.let { shortPlace(it) }
    val title = when (run.outcome) {
        AutoOutcome.SAVED -> where?.let { stringResource(R.string.backup_when_where, time, it) } ?: time
        AutoOutcome.UNCHANGED -> stringResource(R.string.auto_backup_unchanged, time)
        else -> stringResource(R.string.auto_backup_failed, time)
    }
    val summary = when (run.outcome) {
        AutoOutcome.SAVED -> run.name.orEmpty().let { name -> if (run.keysLeftOut) stringResource(R.string.auto_backup_keys_left, name) else name }
        AutoOutcome.UNCHANGED -> stringResource(R.string.auto_backup_unchanged_summary, run.name.orEmpty())
        AutoOutcome.NO_ACCESS -> stringResource(R.string.auto_backup_failed_access)
        AutoOutcome.NO_PLACE -> stringResource(R.string.auto_backup_failed_place)
        AutoOutcome.NO_SPACE -> stringResource(R.string.auto_backup_failed_space)
        AutoOutcome.NO_SETTINGS, AutoOutcome.FAILED -> stringResource(R.string.auto_backup_failed_other)
    }
    ActionRow(glyph = Glyph.FILE, title = title, summary = summary, enabled = true, onClick = null)
}

/** A short note under a block, in the muted words of the captions. */
@Composable
private fun Caption(text: String, top: Int = 8) {
    BasicText(
        text = text,
        style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = top.dp),
    )
}

/**
 * What the row of the automatic backup on the Backup screen says: "Off", or when it repeats
 * and where it goes.
 */
@Composable
internal fun autoSummary(auto: AutoBackups, formFactor: FormFactor): String {
    val schedule = auto.schedule
    if (!schedule.on) return stringResource(R.string.auto_backup_off)
    val target = if (formFactor == FormFactor.TV) BackupTarget.FOLDER else schedule.target
    val place = if (target == BackupTarget.FILE) auto.file else auto.folder
    val where = when {
        place != null -> shortPlace(place.kind, place.app)
        target == BackupTarget.FOLDER -> stringResource(R.string.backup_place_downloads)
        else -> null
    }
    val whenText = scheduleText(schedule)
    return if (where != null) stringResource(R.string.auto_backup_place, whenText, where) else whenText
}

/** "Every day, around 3:00 AM", and so on for the other repeats. */
@Composable
private fun scheduleText(schedule: BackupSchedule): String {
    val context = LocalContext.current
    val hour = hourText(context, schedule.hour)
    return when (schedule.repeat) {
        BackupRepeat.DAILY -> stringResource(R.string.auto_backup_when_daily, hour)
        BackupRepeat.EVERY_DAYS -> stringResource(R.string.auto_backup_when_days, schedule.everyDays, hour)
        BackupRepeat.WEEKLY -> {
            val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            stringResource(R.string.auto_backup_when_weekly, schedule.weekday.getDisplayName(TextStyle.FULL_STANDALONE, locale).let { day ->
                // English names its days with a capital; Portuguese does not.
                if (locale.language == "en") day.replaceFirstChar { it.titlecase(locale) } else day.lowercase(locale)
            }, hour)
        }
        BackupRepeat.MONTHLY -> stringResource(R.string.auto_backup_when_monthly, schedule.monthDay.toString(), hour)
    }
}

/** "Next: tomorrow, around 3:00 AM", or "Off". */
@Composable
private fun nextText(auto: AutoBackups): String {
    val context = LocalContext.current
    val due = auto.nextDue() ?: return stringResource(R.string.auto_backup_off)
    if (due <= System.currentTimeMillis()) return stringResource(R.string.auto_backup_next_soon)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = due }
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    fun Calendar.sameDay(other: Calendar) = get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    val day = when {
        then.sameDay(now) -> stringResource(R.string.auto_backup_today)
        then.sameDay(tomorrow) -> stringResource(R.string.auto_backup_tomorrow)
        else -> DateUtils.formatDateTime(
            context,
            due,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
        )
    }
    return stringResource(R.string.auto_backup_next, day, hourText(context, auto.schedule.hour))
}

/** Where the backups go, said in full: "Download/Kiri · on this device". */
@Composable
private fun placeText(place: AutoPlace?, target: BackupTarget): String = when {
    place == null && target == BackupTarget.FILE -> stringResource(R.string.auto_backup_file_none)
    place == null -> stringResource(R.string.auto_backup_place, stringResource(R.string.backup_place_downloads), stringResource(R.string.auto_backup_where_device))
    else -> stringResource(R.string.auto_backup_place, place.name, wherePlace(place.kind, place.app))
}

@Composable
private fun wherePlace(kind: PlaceKind, app: String?): String = when (kind) {
    PlaceKind.DEVICE, PlaceKind.DOWNLOADS -> stringResource(R.string.auto_backup_where_device)
    PlaceKind.CARD -> stringResource(R.string.auto_backup_where_card)
    PlaceKind.DRIVE -> stringResource(R.string.backup_place_drive)
    PlaceKind.OTHER -> app ?: stringResource(R.string.auto_backup_where_other)
}

/** Where a backup went, in a word or two: "Google Drive", "On the device", "Download/Kiri". */
@Composable
private fun shortPlace(kind: PlaceKind, app: String? = null): String = when (kind) {
    PlaceKind.DEVICE -> stringResource(R.string.backup_place_device)
    PlaceKind.DOWNLOADS -> stringResource(R.string.backup_place_downloads)
    PlaceKind.CARD -> stringResource(R.string.auto_backup_where_card).replaceFirstChar { it.uppercaseChar() }
    PlaceKind.DRIVE -> stringResource(R.string.backup_place_drive)
    PlaceKind.OTHER -> app ?: stringResource(R.string.auto_backup_where_other).replaceFirstChar { it.uppercaseChar() }
}

@Composable
private fun repeatText(repeat: BackupRepeat, everyDays: Int): String = when (repeat) {
    BackupRepeat.DAILY -> stringResource(R.string.auto_backup_repeat_daily)
    BackupRepeat.EVERY_DAYS -> stringResource(R.string.auto_backup_repeat_days, everyDays)
    BackupRepeat.WEEKLY -> stringResource(R.string.auto_backup_repeat_weekly)
    BackupRepeat.MONTHLY -> stringResource(R.string.auto_backup_repeat_monthly)
}

private fun BackupTarget.titleRes(): Int = when (this) {
    BackupTarget.FOLDER -> R.string.auto_backup_target_folder
    BackupTarget.FILE -> R.string.auto_backup_target_file
}

/** The hour as the device writes times: "03:00", or "3:00 AM". */
private fun hourText(context: Context, hour: Int): String {
    val at = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
    }
    return DateFormat.getTimeFormat(context).format(at.time)
}

/** The days of the week in the order the [locale] starts its week with. */
private fun weekOf(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}
