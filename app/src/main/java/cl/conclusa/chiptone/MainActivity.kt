package cl.conclusa.chiptone

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0B0F2A")
    private val PANEL = Color.parseColor("#1D2B53")
    private val INK = Color.parseColor("#FFF1E8")
    private val DIM = Color.parseColor("#83769C")
    private val MEL = Color.parseColor("#FFD23F")
    private val HOT = Color.parseColor("#FF004D")
    private val BASS = Color.parseColor("#29E3C4")

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var info: TextView
    private lateinit var slotInfo: TextView
    private val exec = Executors.newSingleThreadExecutor()
    private val pro = BuildConfig.PRO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(40))
        }
        scroll.addView(root)
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------------- UI ----------------

    private fun build() {
        root.addView(text(if (pro) "CHIPTONE · OWNER" else "CHIPTONE", 24f, MEL, bold = true).apply { gravity = Gravity.CENTER })
        root.addView(text("Leitmotivs chiptune aleatorios para tus notificaciones", 12f, DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(16))
        })

        status = text("", 13f, INK)
        root.addView(card(status))
        root.addView(button("Dar acceso a notificaciones", PANEL) {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        val st = prefs.load()
        root.addView(switch("Chiptone activo", st.enabled) { v -> save { it.copy(enabled = v) } })

        root.addView(button("▶  PROBAR", HOT, big = true) { test(false) })
        root.addView(button("♫  FRASE COMPLETA (4 mensajes)", PANEL) { test(true) })
        info = text("Aprieta PROBAR", 13f, INK).apply { setPadding(dp(4), dp(10), dp(4), dp(10)); setLineSpacing(0f, 1.25f) }
        root.addView(info)

        section("APPS")
        val active = prefs.apps()
        for ((pkg, name) in Apps.ALL) {
            val locked = !pro && pkg !in Apps.FREE
            root.addView(switch(name + if (locked) "  (Pro)" else "", pkg in active && !locked, enabled = !locked) { v ->
                prefs.setApp(pkg, v)
            })
        }

        section("MÚSICA")
        val grooves = Music.GROOVES.values.toList()
        val gLabels = listOf("Aleatorio") + grooves.map { it.name + if (!pro && !it.free) " (Pro)" else "" }
        val gKeys = listOf("r") + grooves.map { it.key }
        root.addView(spinner("Groove", gLabels, gKeys.indexOf(st.groove).coerceAtLeast(0)) { i ->
            val k = gKeys[i]
            val g = Music.GROOVES[k]
            save { it.copy(groove = if (g != null && !pro && !g.free) "r" else k) }
        })

        val modes = Music.MODES.values.toList()
        val mLabels = listOf("Por tramo (30 min)", "Aleatoria cada vez") +
            modes.map { it.name + if (!pro && it.key !in Music.FREE_MODES) " (Pro)" else "" }
        val mKeys = listOf("t", "r") + modes.map { it.key }
        root.addView(spinner("Escala", mLabels, mKeys.indexOf(st.mode).coerceAtLeast(0)) { i ->
            save { it.copy(mode = mKeys[i]) }
            refreshStatus()
        })
        slotInfo = text("", 11f, DIM).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        root.addView(slotInfo)

        val kLabels = listOf("Aleatoria") + Music.NOTE.toList()
        val kKeys = listOf("r") + (0..11).map { it.toString() }
        root.addView(spinner("Tonalidad", kLabels, kKeys.indexOf(st.key).coerceAtLeast(0)) { i ->
            save { it.copy(key = kKeys[i]) }
        })

        root.addView(switch("Modo frase (mensajes seguidos)", st.chain) { v -> save { it.copy(chain = v) } })
        root.addView(hint("Mensajes seguidos arman una frase de 4 compases (I → IV/vi → V → I). Se reinicia tras 60 s sin mensajes."))
        root.addView(switch("Armonía (dobla el leitmotiv)", st.harmony) { v -> save { it.copy(harmony = v) } })
        root.addView(switch("Arpegios (25%, tuplets estilo mathcore)", st.arps) { v -> save { it.copy(arps = v) } })
        root.addView(switch("Batería", st.drums) { v -> save { it.copy(drums = v) } })
        root.addView(switch("Eco 16-bit", st.echo) { v -> save { it.copy(echo = v) } })
        root.addView(switch("Tempo aleatorio (según groove)", st.tempoRandom) { v -> save { it.copy(tempoRandom = v) } })
        root.addView(slider("Tempo fijo", 80, 240, st.bpm, " BPM") { v -> save { it.copy(bpm = v) } })
        root.addView(slider("Volumen", 10, 100, st.volume, "%") { v -> save { it.copy(volume = v) } })
        root.addView(switch("Respetar No molestar", st.respectDnd) { v -> save { it.copy(respectDnd = v) } })

        section("CONFIGURACIÓN DEL TELÉFONO")
        root.addView(hint(
            "1. En WhatsApp: Ajustes → Notificaciones → Tono de notificación → Ninguno (deja la vibración).\n" +
            "2. Xiaomi / HyperOS: activa Inicio automático para Chiptone y en Ahorro de batería elige “Sin restricciones”, " +
            "si no, el sistema la cierra.\n" +
            "3. Chiptone no lee el contenido de tus mensajes: solo detecta qué app envió la notificación."
        ))
        root.addView(button("Abrir ajustes de la app (batería / inicio automático)", PANEL) {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        if (!pro) root.addView(hint("Versión gratis: Rock y Chiptone, escalas mayor y menor, solo WhatsApp. Chiptone Pro desbloquea todo (próximamente)."))
        root.addView(text("v" + packageManager.getPackageInfo(packageName, 0).versionName, 10f, DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0)
        })
    }

    private fun test(phrase: Boolean) {
        info.text = "Generando…"
        exec.execute {
            val txt = try { Engine.test(applicationContext, phrase) } catch (e: Exception) { "Error: ${e.message}" }
            runOnUiThread { info.text = (if (phrase) "Frase de 4 · último motivo:\n" else "") + txt }
        }
    }

    private fun refreshStatus() {
        val ok = listenerEnabled()
        status.text = if (ok) "✔  Acceso a notificaciones activo" else "✘  Falta dar acceso a notificaciones (botón de abajo)"
        status.setTextColor(if (ok) BASS else HOT)
        if (::slotInfo.isInitialized) {
            val st = prefs.load()
            slotInfo.text = if (pro && st.mode == "t") {
                val left = Music.SLOT_MS - (System.currentTimeMillis() % Music.SLOT_MS)
                "Escala del tramo actual: ${Music.MODES.getValue(Music.slotMode(Music.currentSlot())).name} · cambia en ${left / 60000} min"
            } else ""
        }
    }

    private fun listenerEnabled(): Boolean {
        val flat = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, ChiptoneListener::class.java).flattenToString()
        return flat.split(":").any { it == me }
    }

    private fun save(f: (Settings) -> Settings) {
        prefs.save(f(prefs.load()))
        Engine.resetPhrase()
    }

    // ---------------- helpers de vista ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
    }

    private fun hint(s: String) = text(s, 11f, DIM).apply { setPadding(dp(4), 0, dp(4), dp(10)); setLineSpacing(0f, 1.2f) }

    private fun section(title: String) {
        root.addView(text(title, 13f, BASS, bold = true).apply { setPadding(0, dp(22), 0, dp(6)) })
    }

    private fun card(v: View) = LinearLayout(this).apply {
        background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(4).toFloat() }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(v)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
    }

    private fun button(label: String, color: Int, big: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; setTextColor(INK); textSize = if (big) 18f else 13f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAllCaps = false
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(4).toFloat() }
        setPadding(dp(12), dp(if (big) 18 else 12), dp(12), dp(if (big) 18 else 12))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label; setTextColor(if (enabled) INK else DIM); textSize = 13f; typeface = Typeface.MONOSPACE
        isChecked = checked; isEnabled = enabled
        setPadding(dp(4), dp(10), dp(4), dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun spinner(label: String, items: List<String>, sel: Int, onSelect: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        box.addView(text(label, 13f, INK).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(sel)
        var first = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                onSelect(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        sp.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.3f)
        box.addView(sp)
        return box
    }

    private fun slider(label: String, lo: Int, hi: Int, value: Int, unit: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        val lbl = text("$label: $value$unit", 13f, INK)
        val range = hi - lo
        val start = (value - lo).coerceIn(0, range)
        val sb = SeekBar(this)
        sb.max = range
        sb.progress = start
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { lbl.text = "$label: ${p + lo}$unit" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { onChange((s?.progress ?: 0) + lo) }
        })
        box.addView(lbl); box.addView(sb)
        return box
    }
}
