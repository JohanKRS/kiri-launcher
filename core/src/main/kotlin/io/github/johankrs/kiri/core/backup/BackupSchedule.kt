package io.github.johankrs.kiri.core.backup

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** How often an automatic backup is made. */
enum class BackupRepeat(val key: String) {
    DAILY("daily"),

    /** Every few days: how many is [BackupSchedule.everyDays]. */
    EVERY_DAYS("days"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    companion object {
        fun of(key: String?): BackupRepeat = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

/** Where an automatic backup goes. */
enum class BackupTarget(val key: String) {
    /** A new file each time, in a folder, of which the newest [BackupSchedule.keep] stay. */
    FOLDER("folder"),

    /**
     * The same file each time, written over: for a place that lets an app keep one file but not
     * a whole folder, such as Google Drive, which then keeps the earlier versions itself.
     */
    FILE("file"),
    ;

    companion object {
        fun of(key: String?): BackupTarget = entries.firstOrNull { it.key == key } ?: FOLDER
    }
}

/**
 * When Kiri saves a backup by itself, and how: every day, every few days, every week or every
 * month, around an [hour] of the day, into a folder (keeping the newest [keep]) or over one file.
 *
 * Android picks the moment around the hour that costs the least battery, so the time is "around".
 * A backup missed while the device was off is made as soon as it can be.
 */
data class BackupSchedule(
    val on: Boolean = false,
    val repeat: BackupRepeat = BackupRepeat.DAILY,
    val everyDays: Int = 3,
    val weekday: DayOfWeek = DayOfWeek.SUNDAY,
    /** The day of the month, up to the 28th, so every month has it. */
    val monthDay: Int = 1,
    /** The hour of the day, 0 to 23. */
    val hour: Int = 3,
    val target: BackupTarget = BackupTarget.FOLDER,
    /** How many of its own backups stay in the folder; older ones are deleted. */
    val keep: Int = 7,
    /** Whether a backup equal to the last one is left unmade. */
    val onlyWhenChanged: Boolean = true,
) {
    /** The same schedule with every number inside its range. */
    fun normalized(): BackupSchedule = copy(
        everyDays = everyDays.coerceIn(EVERY_DAYS_RANGE),
        monthDay = monthDay.coerceIn(MONTH_DAY_RANGE),
        hour = hour.coerceIn(HOUR_RANGE),
        keep = keep.coerceIn(KEEP_RANGE),
    )

    /**
     * When the next backup is due, in milliseconds since 1970: the first time of the schedule
     * after [last], the last one made or tried, or before the first, after [since], when the
     * schedule began. It may be in the past: then it is due now.
     */
    fun nextAfter(last: Long?, since: Long, zone: ZoneId): Long {
        val schedule = normalized()
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(last ?: since), zone)
        val time = LocalTime.of(schedule.hour, 0)
        fun at(date: LocalDate): ZonedDateTime = ZonedDateTime.of(date, time, zone)
        val next = when (schedule.repeat) {
            BackupRepeat.DAILY -> at(base.toLocalDate()).let { if (it.isAfter(base)) it else at(base.toLocalDate().plusDays(1)) }

            // Counted from the day of the last one, so one made late does not move the ones after.
            BackupRepeat.EVERY_DAYS -> if (last == null) {
                at(base.toLocalDate()).let { if (it.isAfter(base)) it else at(base.toLocalDate().plusDays(1)) }
            } else {
                at(base.toLocalDate().plusDays(schedule.everyDays.toLong()))
            }

            BackupRepeat.WEEKLY -> {
                val day = base.toLocalDate().with(TemporalAdjusters.nextOrSame(schedule.weekday))
                at(day).let { if (it.isAfter(base)) it else at(day.plusWeeks(1)) }
            }

            BackupRepeat.MONTHLY -> {
                val month = base.toLocalDate().withDayOfMonth(1)
                at(month.withDayOfMonth(schedule.monthDay)).let {
                    if (it.isAfter(base)) it else at(month.plusMonths(1).withDayOfMonth(schedule.monthDay))
                }
            }
        }
        return next.toInstant().toEpochMilli()
    }

    companion object {
        val EVERY_DAYS_RANGE = 2..30
        val MONTH_DAY_RANGE = 1..28
        val HOUR_RANGE = 0..23
        val KEEP_RANGE = 1..30
    }
}

/** The files the automatic backup makes, and which of them go once there are too many. */
object AutoBackupFiles {
    /** What every automatic backup's name starts with, so it stands apart from one saved by hand. */
    const val PREFIX = "Kiri auto "

    private val NAME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH'h'mm", Locale.ROOT)

    /** `Kiri auto 27-11-2026 03h04.kiri`: like the name of one saved by hand, marked as automatic. */
    fun name(at: Long, zone: ZoneId): String =
        PREFIX + NAME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone)) + "." + BackupFile.EXTENSION

    /** The name offered for the one file written over each time. */
    const val SINGLE_NAME = "Kiri auto." + BackupFile.EXTENSION

    /**
     * Of [made], the files the automatic backup wrote, oldest first, the ones to delete so that
     * only the newest [keep] stay. Files written by hand are never in [made].
     */
    fun <T> toForget(made: List<T>, keep: Int): List<T> = made.dropLast(keep.coerceAtLeast(1))
}
