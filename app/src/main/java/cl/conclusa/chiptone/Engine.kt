package cl.conclusa.chiptone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Apps que Chiptone puede escuchar. Solo WhatsApp en la versión gratis. */
object Apps {
    val ALL = listOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.messaging" to "Mensajes (Google)",
        "com.android.mms" to "Mensajes (Xiaomi)",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger"
    )
    val FREE = setOf("com.whatsapp")
    val DEFAULT = setOf("com.whatsapp", "com.whatsapp.w4b")
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("chiptone", Context.MODE_PRIVATE)

    fun load() = Settings(
        enabled = sp.getBoolean("enabled", true),
        chain = sp.getBoolean("chain", true),
        groove = sp.getString("groove", "r") ?: "r",
        mode = sp.getString("mode", "t") ?: "t",
        key = sp.getString("key", "r") ?: "r",
        harmony = sp.getBoolean("harmony", true),
        arps = sp.getBoolean("arps", true),
        drums = sp.getBoolean("drums", true),
        echo = sp.getBoolean("echo", false),
        tempoRandom = sp.getBoolean("tempoRandom", true),
        bpm = sp.getInt("bpm", 180),
        volume = sp.getInt("volume", 80),
        respectDnd = sp.getBoolean("respectDnd", true)
    )

    fun save(s: Settings) {
        sp.edit()
            .putBoolean("enabled", s.enabled).putBoolean("chain", s.chain)
            .putString("groove", s.groove).putString("mode", s.mode).putString("key", s.key)
            .putBoolean("harmony", s.harmony).putBoolean("arps", s.arps).putBoolean("drums", s.drums)
            .putBoolean("echo", s.echo).putBoolean("tempoRandom", s.tempoRandom)
            .putInt("bpm", s.bpm).putInt("volume", s.volume).putBoolean("respectDnd", s.respectDnd)
            .apply()
    }

    fun keepAlive(): Boolean = sp.getBoolean("keepAlive", true)
    fun setKeepAlive(v: Boolean) = sp.edit().putBoolean("keepAlive", v).apply()

    fun apps(): Set<String> {
        val set = sp.getStringSet("apps", Apps.DEFAULT) ?: Apps.DEFAULT
        return if (BuildConfig.PRO) set else set.intersect(Apps.FREE)
    }

    fun setApp(pkg: String, on: Boolean) {
        val cur = (sp.getStringSet("apps", Apps.DEFAULT) ?: Apps.DEFAULT).toMutableSet()
        if (on) cur.add(pkg) else cur.remove(pkg)
        sp.edit().putStringSet("apps", cur).apply()
    }
}

object Player {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null

    @Synchronized
    fun play(pcm: ShortArray, asNotification: Boolean) {
        stop()
        val attrs = AudioAttributes.Builder()
            .setUsage(if (asNotification) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(Synth.SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
        val ms = pcm.size * 1000L / Synth.SAMPLE_RATE + 300
        main.postDelayed({ release(t) }, ms)
    }

    @Synchronized
    private fun release(t: AudioTrack) {
        try { t.stop() } catch (_: Exception) {}
        t.release()
        if (track === t) track = null
    }

    @Synchronized
    fun stop() {
        track?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        track = null
    }
}

/** Estado compartido entre la pantalla y el servicio de notificaciones. */
object Engine {
    private val gen = Generator()
    private val synth = Synth()
    private var lastAt = 0L
    var lastInfo: String = ""
        private set

    /** Reinicia la frase si pasaron más de 60 s sin mensajes. */
    private const val PHRASE_RESET_MS = 60_000L
    /** Ignora repeticiones de la misma notificación (WhatsApp publica varias veces). */
    private const val DEBOUNCE_MS = 1_200L

    @Synchronized
    fun onMessage(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAt < DEBOUNCE_MS) return
        if (now - lastAt > PHRASE_RESET_MS) gen.reset()
        lastAt = now
        val st = Prefs(ctx).load()
        val m = gen.next(st, BuildConfig.PRO)
        lastInfo = m.describe()
        Player.play(synth.render(listOf(m), st), asNotification = true)
    }

    @Synchronized
    fun test(ctx: Context, phrase: Boolean): String {
        val st = Prefs(ctx).load()
        val list = if (phrase) {
            gen.reset()
            List(4) { gen.next(st.copy(chain = true), BuildConfig.PRO) }
        } else listOf(gen.next(st, BuildConfig.PRO))
        lastInfo = list.last().describe()
        Player.play(synth.render(list, st), asNotification = false)
        return lastInfo
    }

    fun resetPhrase() = synchronized(this) { gen.reset() }
}
