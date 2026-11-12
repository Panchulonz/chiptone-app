package cl.conclusa.chiptone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

/**
 * Escucha notificaciones de las apps elegidas y toca un leitmotiv nuevo.
 * Privacidad: NO lee el contenido de los mensajes; solo mira qué app publicó la notificación.
 *
 * Para que HyperOS/MIUI no congele el proceso en segundo plano, el servicio se pone en
 * primer plano con una notificación fija silenciosa ("Chiptone escuchando").
 */
class ChiptoneListener : NotificationListenerService() {

    private val exec = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Prefs(this).keepAlive()) goForeground()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try { requestRebind(ComponentName(this, ChiptoneListener::class.java)) } catch (_: Exception) { }
    }

    private fun goForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val ch = NotificationChannel(CHANNEL, "Chiptone activo", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Chiptone escuchando")
                .setContentText("Tus notificaciones suenan con leitmotivs chiptune")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FG_ID, n)
            }
        } catch (_: Exception) {
            // Si el sistema no lo permite, seguimos como servicio normal.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val prefs = Prefs(this)
        val st = prefs.load()
        if (!st.enabled) return
        if (sbn.packageName !in prefs.apps()) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (!sbn.isClearable) return

        if (st.respectDnd && currentInterruptionFilter > NotificationListenerService.INTERRUPTION_FILTER_ALL) return
        val am = getSystemService(AudioManager::class.java)
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        // Mantiene la CPU despierta mientras genera y reproduce el tono
        val wl = (getSystemService(POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chiptone:play")
        try { wl?.acquire(6_000L) } catch (_: Exception) { }

        val app = applicationContext
        exec.execute {
            try { Engine.onMessage(app) } catch (_: Exception) { }
        }
    }

    companion object {
        private const val CHANNEL = "chiptone_status"
        private const val FG_ID = 7
    }
}
