package com.gathin.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors

/** Uploads stay running, with a notification, after the screen closes. */
class UploadService : Service() {
    private val pool = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val note = note(getString(R.string.app_name), 0, true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(7, note)
        }
        pool.execute {
            val jobs = JobStore.load(this)
            for (job in jobs) {
                var current = job
                while (true) {
                    try {
                        send(current)
                        JobStore.remove(this, current.id)
                        break
                    } catch (rejected: PinRejected) {
                        Books.factory(this).forgetPin(current.host, current.port)
                        JobStore.remove(this, current.id)
                        break
                    } catch (failed: Exception) {
                        val next = Queue.nextAttempt(current.attempts)
                        if (next == null) {
                            JobStore.remove(this, current.id)
                            break
                        }
                        current = current.copy(attempts = next)
                        JobStore.replace(this, current)
                        try {
                            Thread.sleep(400L * next)
                        } catch (ignored: InterruptedException) {
                            break
                        }
                    }
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun send(job: Job) {
        val client = TvClient(job.host, job.port, job.pin)
        if (job.text != null) {
            update(job.name, 0, false)
            client.note(job.text)
            update(job.name, 100, false)
            return
        }
        val uri = job.uri ?: return
        val parsed = android.net.Uri.parse(uri)
        val known = contentResolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: -1L
        if (known >= 0) {
            contentResolver.openInputStream(parsed)?.use { input ->
                client.upload(leaf(job.name), known, input) { sent, total ->
                    update(job.name, if (total <= 0) 0 else ((sent * 100) / total).toInt(), false)
                }
            }
        } else {
            val tmp = java.io.File.createTempFile("send", ".bin", cacheDir)
            contentResolver.openInputStream(parsed)?.use { input -> tmp.outputStream().use { input.copyTo(it, 8192) } }
            tmp.inputStream().use { input ->
                client.upload(leaf(job.name), tmp.length(), input) { sent, total ->
                    update(job.name, if (total <= 0) 0 else ((sent * 100) / total).toInt(), false)
                }
            }
            tmp.delete()
        }
    }

    private fun leaf(name: String): String {
        val slash = name.replace('\\', '/').lastIndexOf('/')
        return if (slash >= 0) name.substring(slash + 1) else name
    }

    private fun update(name: String, pct: Int, indeterminate: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(7, note(getString(R.string.sending, name), pct, indeterminate))
    }

    private fun note(text: String, pct: Int, indeterminate: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("transfer", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "transfer")
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, pct, indeterminate)
            .build()
    }

    companion object {
        fun enqueue(ctx: Context, job: Job) {
            JobStore.add(ctx, job)
            val intent = Intent(ctx, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
        }
    }
}
