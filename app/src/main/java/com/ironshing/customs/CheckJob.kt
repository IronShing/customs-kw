package com.ironshing.customs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Periodic background check; posts a notification for new, changed or released declarations. */
class CheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (Store.crs(this).isEmpty()) return false
        Thread {
            val first = Store.load(this).isEmpty()
            val out = Checker.run(this)
            if (!first) notify(this, out.changes)
            jobFinished(params, out.error != null)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB = 1
        private const val CHANNEL = "declarations"

        fun schedule(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java)
            if (!Store.background(c)) {
                js.cancel(JOB)
                return
            }
            if (js.getPendingJob(JOB) != null) return
            js.schedule(
                JobInfo.Builder(JOB, ComponentName(c, CheckJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(3 * 60 * 60 * 1000L, 60 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
            )
        }

        fun notify(c: Context, changes: List<Checker.Change>) {
            if (changes.isEmpty()) return
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Declaration updates", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            for (ch in changes) {
                val d = ch.dec
                val title = when (ch.kind) {
                    Checker.Kind.RELEASED -> "Released: ${d.row.number}"
                    Checker.Kind.NEW -> "New declaration: ${d.row.number}"
                    Checker.Kind.CHANGED -> "Updated: ${d.row.number}"
                }
                val text = Tr.en(d.row.status, d.statusEn) + " · " + Tr.en(d.row.port)
                nm.notify(
                    d.row.number.hashCode(),
                    Notification.Builder(c, CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(Notification.BigTextStyle().bigText(text + "\n" + d.row.status))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }
    }
}
