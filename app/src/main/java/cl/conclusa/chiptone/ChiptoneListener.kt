package cl.conclusa.chiptone

import android.app.Notification
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

/**
 * Escucha notificaciones de las apps elegidas y toca un leitmotiv nuevo.
 * Privacidad: NO lee el contenido de los mensajes; solo mira qué app publicó la notificación.
 */
class ChiptoneListener : NotificationListenerService() {

    private val exec = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
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

        val app = applicationContext
        exec.execute {
            try { Engine.onMessage(app) } catch (_: Exception) { }
        }
    }
}
