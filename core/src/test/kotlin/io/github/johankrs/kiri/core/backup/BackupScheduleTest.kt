package io.github.johankrs.kiri.core.backup

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupScheduleTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // Friday, 27 November 2026, 08:52.
    private val now = at(2026, 11, 27, 8, 52)

    @Test
    fun `every day, the first one is at the next hour of the schedule`() {
        val daily = BackupSchedule(on = true, hour = 3)
        assertEquals(at(2026, 11, 28, 3), daily.nextAfter(last = null, since = now, zone = zone))
        assertEquals(at(2026, 11, 27, 10), daily.copy(hour = 10).nextAfter(last = null, since = now, zone = zone))
    }

    @Test
    fun `every day, the next one is the day after the last`() {
        val daily = BackupSchedule(on = true, hour = 3)
        assertEquals(at(2026, 11, 28, 3), daily.nextAfter(last = at(2026, 11, 27, 3, 4), since = 0, zone = zone))
        // Made late, in the evening: the next is still the next morning.
        assertEquals(at(2026, 11, 28, 3), daily.nextAfter(last = at(2026, 11, 27, 23, 10), since = 0, zone = zone))
    }

    @Test
    fun `a backup missed is due at once`() {
        val daily = BackupSchedule(on = true, hour = 3)
        val due = daily.nextAfter(last = at(2026, 11, 24, 3, 2), since = 0, zone = zone)
        assertEquals(at(2026, 11, 25, 3), due)
        assertTrue(due < now)
    }

    @Test
    fun `every few days counts from the day of the last one`() {
        val schedule = BackupSchedule(on = true, repeat = BackupRepeat.EVERY_DAYS, everyDays = 3, hour = 3)
        assertEquals(at(2026, 11, 30, 3), schedule.nextAfter(last = at(2026, 11, 27, 3, 4), since = 0, zone = zone))
        assertEquals(at(2026, 11, 30, 3), schedule.nextAfter(last = at(2026, 11, 27, 21, 0), since = 0, zone = zone))
        // Before the first, it is the next hour of the schedule, as every day.
        assertEquals(at(2026, 11, 28, 3), schedule.nextAfter(last = null, since = now, zone = zone))
    }

    @Test
    fun `every week is on its day`() {
        val sundays = BackupSchedule(on = true, repeat = BackupRepeat.WEEKLY, weekday = DayOfWeek.SUNDAY, hour = 3)
        assertEquals(at(2026, 11, 29, 3), sundays.nextAfter(last = null, since = now, zone = zone))
        assertEquals(at(2026, 12, 6, 3), sundays.nextAfter(last = at(2026, 11, 29, 3, 20), since = 0, zone = zone))
        val thursdays = sundays.copy(weekday = DayOfWeek.THURSDAY)
        assertEquals(at(2026, 12, 3, 3), thursdays.nextAfter(last = null, since = now, zone = zone))
        val fridays = sundays.copy(weekday = DayOfWeek.FRIDAY)
        assertEquals(at(2026, 11, 27, 22), fridays.copy(hour = 22).nextAfter(last = null, since = now, zone = zone))
        assertEquals(at(2026, 12, 4, 3), fridays.nextAfter(last = null, since = now, zone = zone))
    }

    @Test
    fun `every month is on its day, even across a year`() {
        val monthly = BackupSchedule(on = true, repeat = BackupRepeat.MONTHLY, monthDay = 1, hour = 3)
        assertEquals(at(2026, 12, 1, 3), monthly.nextAfter(last = null, since = now, zone = zone))
        assertEquals(at(2027, 1, 1, 3), monthly.nextAfter(last = at(2026, 12, 1, 3, 5), since = 0, zone = zone))
        assertEquals(at(2026, 11, 27, 10), monthly.copy(monthDay = 27, hour = 10).nextAfter(last = null, since = now, zone = zone))
        assertEquals(at(2026, 12, 27, 3), monthly.copy(monthDay = 27).nextAfter(last = null, since = now, zone = zone))
    }

    @Test
    fun `numbers out of range are brought back in`() {
        val wild = BackupSchedule(everyDays = 0, monthDay = 31, hour = 30, keep = 0).normalized()
        assertEquals(2, wild.everyDays)
        assertEquals(28, wild.monthDay)
        assertEquals(23, wild.hour)
        assertEquals(1, wild.keep)
    }

    @Test
    fun `an hour skipped by a clock change still has a backup after it`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 29 March 2026: clocks in Berlin go from 02:00 to 03:00.
        val before = LocalDateTime.of(2026, 3, 28, 12, 0).atZone(berlin).toInstant().toEpochMilli()
        val next = BackupSchedule(on = true, hour = 2).nextAfter(last = before, since = 0, zone = berlin)
        assertTrue(next > before)
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), berlin))
    }

    @Test
    fun `only the newest files stay`() {
        val made = (1..10).toList()
        assertEquals(listOf(1, 2, 3), AutoBackupFiles.toForget(made, keep = 7))
        assertEquals(emptyList(), AutoBackupFiles.toForget(made, keep = 30))
        assertEquals((1..9).toList(), AutoBackupFiles.toForget(made, keep = 0))
    }

    @Test
    fun `an automatic backup is named apart from one saved by hand`() {
        assertEquals("Kiri auto 27-11-2026 08h52.kiri", AutoBackupFiles.name(now, zone))
    }

    @Test
    fun `a key prepared from a password locks what the password opens`() {
        val secrets = mapOf("key.brapi" to StoredValue.Text("abc123"))
        val prepared = SecretLock.prepare("senha segura".toCharArray(), rounds = 10_000)
        val locked = Backup.lock(secrets, prepared)
        val backup = Backup(
            info = BackupInfo(createdAt = 1L, device = "x", formFactor = io.github.johankrs.kiri.core.FormFactor.PHONE, appVersion = "1"),
            settings = emptyMap(),
            market = emptyMap(),
            lockedSecrets = locked,
        )
        val read = BackupFile.read(java.io.ByteArrayOutputStream().also { BackupFile.write(backup, it) }.toByteArray().inputStream())!!
        assertEquals(secrets, read.openSecrets("senha segura".toCharArray()))
        assertNull(read.openSecrets("outra senha".toCharArray()))
        // Each lock is new, even with the same key.
        assertNotEquals(locked.iv.toList(), Backup.lock(secrets, prepared).iv.toList())
    }

    @Test
    fun `the fingerprint follows what the backup holds and nothing else`() {
        val settings = mapOf("a" to StoredValue.Whole(1), "b" to StoredValue.Texts(setOf("x", "y")))
        val again = linkedMapOf("b" to StoredValue.Texts(linkedSetOf("y", "x")), "a" to StoredValue.Whole(1))
        val one = Backup.fingerprint(settings, emptyMap(), null, byteArrayOf(1, 2))
        assertEquals(one, Backup.fingerprint(again, emptyMap(), null, byteArrayOf(1, 2)))
        assertNotEquals(one, Backup.fingerprint(settings + ("a" to StoredValue.Whole(2)), emptyMap(), null, byteArrayOf(1, 2)))
        assertNotEquals(one, Backup.fingerprint(settings + ("a" to StoredValue.Big(1)), emptyMap(), null, byteArrayOf(1, 2)))
        assertNotEquals(one, Backup.fingerprint(settings, emptyMap(), emptyMap(), byteArrayOf(1, 2)))
        assertNotEquals(one, Backup.fingerprint(settings, emptyMap(), null, byteArrayOf(1, 3)))
        assertNotEquals(one, Backup.fingerprint(settings, emptyMap(), null, null))
        assertNotEquals(one, Backup.fingerprint(emptyMap(), settings, null, byteArrayOf(1, 2)))
    }
}
