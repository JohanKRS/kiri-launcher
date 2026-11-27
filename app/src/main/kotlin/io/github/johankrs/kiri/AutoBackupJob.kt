package io.github.johankrs.kiri

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.johankrs.kiri.data.AutoBackups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Where Android wakes Kiri for the automatic backup (see [AutoBackups]): it makes the backup
 * that is due, then asks to be woken for the next one.
 */
class AutoBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val auto = AutoBackups.of(applicationContext)
            runCatching { auto.runIfDue() }
            jobFinished(params, false)
            auto.reschedule()
        }
        return true
    }

    /** Stopped halfway (the battery ran low, say): Android tries again a little later. */
    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
