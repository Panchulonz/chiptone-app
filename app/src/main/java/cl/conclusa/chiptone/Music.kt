package cl.conclusa.chiptone

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Datos musicales: escalas, grooves y modelos de motivo.
// Es un port directo del prototipo web "Leitmotiv 8-bit".
// ---------------------------------------------------------------------------

class Mode(val key: String, val name: String, val s: IntArray, val allow: IntArray? = null)

class Groove(
    val key: String,
    val name: String,
    val bpmMin: Int,
    val bpmMax: Int,
    val w: Int,
    val mel: String,
    val bass: List<String>,
    val swingGrid: Int = 0,
    val swingAmt: Double = 0.0,
    val artic: Double? = null,
    val K: String? = null,
    val S: String? = null,
    val R: String? = null,
    val H: String? = null,
    val P: String? = null,
    val C: String? = null,
    val flamenco: Boolean = false,
    val prog: IntArray? = null,
    val fixedLen: Int? = null,
    val beatSteps: Boolean = false,
    val accents: IntArray? = null,
    val meterLabel: String? = null,
    val free: Boolean = false
)

class Note(val s: Double, val d: Double, val m: Int)
class Hit(val s: Int, val kind: Char, val v: Char)

class Motif(
    val modeKey: String,
    val key: Int,
    val grooveKey: String,
    val bpm: Int,
    val chordDeg: Int,
    val pos: Int,
    val mel: List<Note>,
    val harm: List<Note>?,
    val hiv: Int,
    val arp: String?,
    val bass: List<Note>,
    val len: Int,
    val duty: Double,
    val artic: Double,
    val bassStyle: String,
    val drums: List<Hit>,
    val comp: List<Hit>,
    val chord: IntArray
) {
    val groove: Groove get() = Music.GROOVES.getValue(grooveKey)
    val stepSec: Double get() = if (groove.beatSteps) 60.0 / bpm else 60.0 / bpm / 4.0
    val durSec: Double get() = len * stepSec

    fun describe(): String {
        val g = groove
        val sb = StringBuilder()
        sb.append(Music.NOTE[key]).append(' ').append(Music.MODES.getValue(modeKey).name)
            .append(" · ").append(Music.ROMAN[chordDeg])
        if (pos >= 0) sb.append(" · ").append(pos + 1).append("/4")
        sb.append('\n').append(g.name).append(" · ").append(g.meterLabel ?: "4/4").append(" · ").append(bpm).append(" BPM\n")
        if (arp != null) {
            sb.append("ARPEGIO en ").append(arp).append(" · una sola voz")
        } else {
            sb.append(mel.joinToString(" – ") { Music.NOTE[((it.m % 12) + 12) % 12] })
            sb.append('\n').append(
                if (harm != null) "armonía: " + Music.IV_NAME[kotlin.math.abs(hiv)] + (if (hiv > 0) " arriba" else " abajo")
                else "sin armonía"
            )
        }
        sb.append("\nbajo: ").append(bassStyle)
        return sb.toString()
    }
}

data class Settings(
    val enabled: Boolean = true,
    val chain: Boolean = true,
    val groove: String = "r",
    val mode: String = "t",
    val key: String = "r",
    val harmony: Boolean = true,
    val arps: Boolean = true,
    val drums: Boolean = true,
    val echo: Boolean = false,
    val tempoRandom: Boolean = true,
    val bpm: Int = 180,
    val volume: Int = 80,
    val respectDnd: Boolean = true
)

object Music {
    val NOTE = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")
    val IV_NAME = arrayOf("unís.", "2ª", "3ª", "4ª", "5ª", "6ª", "7ª", "8ª")

    val MODES: LinkedHashMap<String, Mode> = linkedMapOf(
        "mayor" to Mode("mayor", "Mayor", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
        "menor" to Mode("menor", "Menor", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
        "lidio" to Mode("lidio", "Lidio", intArrayOf(0, 2, 4, 6, 7, 9, 11)),
        "mixo" to Mode("mixo", "Mixolidio", intArrayOf(0, 2, 4, 5, 7, 9, 10)),
        "dorico" to Mode("dorico", "Dórico", intArrayOf(0, 2, 3, 5, 7, 9, 10)),
        "frigio" to Mode("frigio", "Frigio", intArrayOf(0, 1, 3, 5, 7, 8, 10)),
        "locrio" to Mode("locrio", "Locrio", intArrayOf(0, 1, 3, 5, 6, 8, 10)),
        "hira" to Mode("hira", "Hirajoshi", intArrayOf(0, 2, 3, 5, 7, 8, 10), intArrayOf(0, 1, 2, 4, 5)),
        "penta" to Mode("penta", "Pentatónica", intArrayOf(0, 2, 4, 5, 7, 9, 11), intArrayOf(0, 1, 2, 4, 5))
    )
    val FREE_MODES = listOf("mayor", "menor")

    // Escala por tramo de 30 min: pesos internos (efectivo ~26/26/15/11/9/7/2,5/2,5 %)
    private val SLOT_WEIGHTS = listOf(
        "mayor" to 29, "menor" to 29, "lidio" to 14, "mixo" to 10,
        "dorico" to 8, "frigio" to 6, "locrio" to 2, "hira" to 2
    )
    const val SLOT_MS = 30L * 60L * 1000L

    val GROOVES: LinkedHashMap<String, Groove> = linkedMapOf(
        "rock" to Groove("rock", "Rock rápido", 170, 210, 16, "9030603070306030", listOf("octavas", "galope"),
            K = "x.....x.x.x.....", S = "....x.......x...", H = "x.x.x.x.x.x.x.x.", free = true),
        "chip" to Groove("chip", "Chiptune 4x4", 140, 180, 16, "9040604090406040", listOf("galope", "octavas"),
            K = "x...x...x...x...", S = "....x.......x...", H = "..o...o...o...o.", free = true),
        "half" to Groove("half", "Half-time épico", 150, 180, 12, "9030503070305030", listOf("octavas", "negras"),
            K = "x.....x...x.....", S = "........x.......", H = "x.x.x.x.x.x.x.x."),
        "ska" to Groove("ska", "Ska contratiempo", 170, 210, 12, "2090209020902090", listOf("negras"), artic = 0.55,
            K = "x.......x.......", S = "....x.......x...", H = "x.x.x.x.x.x.x.x.", C = "..x...x...x...x."),
        "shuffle" to Groove("shuffle", "Shuffle", 130, 165, 12, "9070907090709070", listOf("walking"),
            swingGrid = 8, swingAmt = 0.33,
            K = "x.......x.x.....", S = "....x.......x...", H = "x.x.x.x.x.x.x.x."),
        "bossa" to Groove("bossa", "Bossa nova", 120, 145, 11, "9229229222922922", listOf("bossa"),
            swingGrid = 16, swingAmt = 0.06,
            K = "x..xx..xx..xx..x", R = "x..x..x...x..x..", H = "xxxxxxxxxxxxxxxx"),
        "reggae" to Groove("reggae", "Reggae one drop", 120, 140, 11, "3090309030903090", listOf("reggae"),
            swingGrid = 16, swingAmt = 0.15, artic = 0.6,
            K = "........x.......", R = "........x.......", H = "x.x.x.x.x.x.x.x.", C = "....x.......x..."),
        "funk" to Groove("funk", "Funk sincopado", 100, 118, 10, "8247284728472847", listOf("funk"),
            swingGrid = 16, swingAmt = 0.12,
            K = "x.....x...x..x..", S = "....x..g.g..x..g", H = "xxxxxxxxxxxxxxxx"),
        "rumba" to Groove("rumba", "Rumba flamenca", 100, 128, 8, "9020703080207030", listOf("rumba"),
            flamenco = true, prog = intArrayOf(3, 2, 1, 0),
            K = "x.....x...x.....", S = "....x.......x...", P = "..p...p...p...p.", C = "X..xg.x.X..xg.x."),
        "buleria" to Groove("buleria", "Bulería", 190, 240, 7, "408208282800", listOf("compas"),
            flamenco = true, prog = intArrayOf(3, 2, 1, 0), fixedLen = 12, beatSteps = true,
            accents = intArrayOf(2, 5, 7, 9, 11), meterLabel = "12 t.",
            K = "..x..x.x.x.x", P = "ppXppXpXpXpX", C = "g.X..X.X.X.X"),
        "solea" to Groove("solea", "Soleá", 110, 150, 6, "608206282600", listOf("compas"),
            flamenco = true, prog = intArrayOf(3, 2, 1, 0), fixedLen = 12, beatSteps = true,
            accents = intArrayOf(2, 5, 7, 9, 11), meterLabel = "12 t.",
            K = "..x..x...x..", S = ".......x...x", P = "..X..X.X.X.X", C = "X.x..X.X.X.X")
    )

    fun mod(a: Int, n: Int) = ((a % n) + n) % n

    // --- PRNG determinista (mulberry32, idéntico al prototipo web) ---
    private class Mulberry(var seed: Int) {
        fun next(): Double {
            seed += 0x6D2B79F5
            var t = seed
            t = (t xor (t ushr 15)) * (1 or t)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
        }
    }

    private fun rawSlotMode(r: Mulberry): String {
        val x = r.next() * 100
        var acc = 0
        for ((k, w) in SLOT_WEIGHTS) { acc += w; if (x < acc) return k }
        return "mayor"
    }

    /** Escala del tramo: determinista según la hora, nunca repite la del tramo anterior (dentro del día). */
    fun slotMode(slot: Long): String {
        val start = slot - Math.floorMod(slot, 48L)
        var prev: String? = null
        var k = "mayor"
        var s = start
        while (s <= slot) {
            val r = Mulberry((s * 2654435761L).toInt())
            k = rawSlotMode(r)
            var n = 0
            while (k == prev && n++ < 12) k = rawSlotMode(r)
            prev = k
            s++
        }
        return k
    }

    fun currentSlot(now: Long = System.currentTimeMillis()): Long = now / SLOT_MS
    fun hz(m: Int): Double = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
}

// ---------------------------------------------------------------------------
// Generador con estado de frase (mensajes seguidos)
// ---------------------------------------------------------------------------

class Generator(private val rnd: Random = Random.Default) {

    private class PhraseCtx(
        val key: Int, val modeKey: String, val gk: String, val bpm: Int,
        val bassStyle: String, val duty: Double, val prog: IntArray, val artic: Double
    )

    private var ctx: PhraseCtx? = null
    private var phraseLen = 0

    fun reset() { ctx = null; phraseLen = 0 }
    val position: Int get() = phraseLen

    private fun ri(a: Int, b: Int) = rnd.nextInt(a, b + 1)
    private fun <T> pick(l: List<T>): T = l[rnd.nextInt(l.size)]
    private fun <T> wpick(pairs: List<Pair<T, Int>>): T {
        val t = pairs.sumOf { it.second }
        var x = rnd.nextInt(t)
        for ((v, w) in pairs) { x -= w; if (x < 0) return v }
        return pairs[0].first
    }
    private fun mod(a: Int, n: Int) = Music.mod(a, n)

    private fun newCtx(st: Settings, pro: Boolean): PhraseCtx {
        val key = if (st.key == "r") ri(0, 11) else st.key.toInt()
        val modeKey = when {
            !pro -> pick(Music.FREE_MODES)
            st.mode == "t" -> Music.slotMode(Music.currentSlot())
            st.mode == "r" -> pick(Music.MODES.keys.toList())
            else -> st.mode
        }
        val pool = Music.GROOVES.values.filter { pro || it.free }
        val gk = if (st.groove == "r" || Music.GROOVES[st.groove]?.let { pro || it.free } != true)
            wpick(pool.map { it.key to it.w }) else st.groove
        val g = Music.GROOVES.getValue(gk)
        val bpm = if (st.tempoRandom) ri(g.bpmMin, g.bpmMax) else st.bpm
        var mk = modeKey
        if (pro && g.flamenco && (st.mode == "t" || st.mode == "r") && rnd.nextDouble() < 0.5) mk = "frigio"
        return PhraseCtx(
            key, mk, gk, bpm,
            pick(g.bass),
            pick(listOf(0.5, 0.5, 0.25, 0.125)),
            g.prog ?: intArrayOf(0, pick(listOf(3, 5)), pick(listOf(4, 4, 1)), 0),
            g.artic ?: pick(listOf(0.7, 0.7, 0.92))
        )
    }

    fun next(st: Settings, pro: Boolean): Motif {
        if (!st.chain || ctx == null || phraseLen >= 4) { ctx = newCtx(st, pro); phraseLen = 0 }
        val c = ctx!!
        val pos = if (st.chain) phraseLen else -1
        val chordDeg = if (st.chain) c.prog[pos] else pick(listOf(0, 0, 0, 5, 3))
        val mode = Music.MODES.getValue(c.modeKey)
        val g = Music.GROOVES.getValue(c.gk)
        val len = g.fixedLen ?: 16

        val melBase = 72 + c.key - (if (c.key > 6) 12 else 0)
        fun toMidi(i: Int) = melBase + 12 * Math.floorDiv(i, 7) + mode.s[mod(i, 7)]

        val isArp = st.arps && rnd.nextDouble() < 0.25
        val rhythm: List<DoubleArray>
        val idx: List<Int>
        var arp: String? = null
        if (isArp) {
            val a = genArp(chordDeg, pos, len, g, mode)
            rhythm = a.first.map { doubleArrayOf(it[0], it[1]) }
            idx = a.first.map { it[2].roundToInt() }
            arp = a.second
        } else {
            rhythm = genRhythm(g, len)
            idx = genPitches(rhythm, chordDeg, pos, mode, g)
        }
        val mel = rhythm.mapIndexed { k, n -> Note(n[0], n[1], toMidi(idx[k])) }

        var harm: List<Note>? = null
        var hiv = 0
        if (st.harmony && !isArp) {
            val deg = wpick(HARM)
            hiv = if (rnd.nextBoolean()) deg else -deg
            val sign = if (hiv > 0) 1 else -1
            fun ok(i: Int) = mode.allow == null || mode.allow.contains(mod(i, 7))
            harm = rhythm.mapIndexed { k, n ->
                var t = idx[k] + hiv
                if (!ok(t)) t += sign * (if (ok(t + sign)) 1 else -1)
                Note(n[0], n[1], toMidi(t))
            }
        }

        val bass = genBass(chordDeg, len, c.bassStyle, c.key, mode)
        val compBase = 60 + c.key - (if (c.key > 6) 12 else 0)
        val chord = IntArray(3) { j ->
            val k = intArrayOf(0, 2, 4)[j]
            compBase + mode.s[(chordDeg + k) % 7] + (if (chordDeg + k >= 7) 12 else 0)
        }

        val m = Motif(
            c.modeKey, c.key, c.gk, c.bpm, chordDeg, pos, mel, harm, hiv, arp, bass, len,
            c.duty, if (arp != null) 1.0 else c.artic, c.bassStyle,
            genDrums(g, len), genComp(g, len), chord
        )
        if (st.chain) phraseLen++
        return m
    }

    // Intervalo de la voz armónica (grados de la escala): 2ª,3ª,4ª,5ª,6ª,7ª,8ª
    private val HARM = listOf(1 to 5, 2 to 22, 3 to 15, 4 to 22, 5 to 9, 6 to 5, 7 to 22)
    private val TUPLETS = listOf(Triple(4, "cuartinas", 30), Triple(3, "tresillos", 25), Triple(6, "sextupletos", 25),
        Triple(5, "quintillos", 10), Triple(7, "septillos", 10))
    private val ARP_SHAPES = listOf("sube" to 30, "baja" to 25, "sube-baja" to 25, "ciclo irregular" to 20)

    private fun genRhythm(g: Groove, len: Int): List<DoubleArray> {
        val n = pick(listOf(2, 3, 3, 4, 4, 4))
        val wts = g.mel.map { it - '0' }
        val maxOn = if (len == 12) 9 else 11
        val pool = (0..maxOn).filter { it < wts.size && wts[it] > 0 }.map { it to wts[it] }
        val set = mutableSetOf<Int>()
        var guard = 0
        while (set.size < n && guard++ < 200) set.add(wpick(pool))
        if (set.min() > 3) {
            val early = pool.filter { it.first <= 3 }
            set.remove(set.min())
            set.add(if (early.isNotEmpty()) wpick(early) else 0)
        }
        val on = set.sorted()
        val notes = on.mapIndexed { i, s ->
            var d = if (i < on.size - 1) on[i + 1] - s else 0
            if (d > 1 && rnd.nextDouble() < 0.25) d = ri(1, d - 1)
            doubleArrayOf(s.toDouble(), d.toDouble())
        }
        val last = notes.last()
        val want = if (g.beatSteps) pick(listOf(1, 2, 2, 3)) else pick(listOf(2, 3, 4, 4, 6, 8))
        last[1] = min(want.toDouble(), len - last[0])
        return notes
    }

    private fun genPitches(notes: List<DoubleArray>, chordDeg: Int, pos: Int, mode: Mode, g: Groove): List<Int> {
        fun isCT(i: Int) = mod(i - chordDeg, 7) in intArrayOf(0, 2, 4)
        fun allowed(i: Int) = mode.allow == null || mode.allow.contains(mod(i, 7))
        fun inRange(i: Int) = i in -2..9
        fun nearest(i: Int, test: (Int) -> Boolean): Int {
            for (r in 0 until 8) {
                val c = listOf(i + r, i - r).filter { test(it) && allowed(it) && inRange(it) }
                if (c.isNotEmpty()) return pick(c)
            }
            return i
        }
        fun strong(s: Double): Boolean {
            val si = s.toInt()
            return if (g.accents != null) g.accents.contains(si) || si == 0 else si % 4 == 0
        }
        var cur = nearest(ri(1, 6)) { isCT(it) }
        val out = mutableListOf(cur)
        for (k in 1 until notes.size) {
            val isLast = k == notes.size - 1
            val r = rnd.nextDouble()
            val mv = when {
                r < .5 -> pick(listOf(-1, 1))
                r < .75 -> pick(listOf(-2, 2))
                r < .9 -> pick(listOf(-4, -3, 3, 4))
                else -> 0
            }
            cur += mv
            if (cur > 9) cur -= if (mv != 0) 2 * kotlin.math.abs(mv) else 2
            if (cur < -2) cur += if (mv != 0) 2 * kotlin.math.abs(mv) else 2
            cur = when {
                isLast && pos == 3 -> nearest(cur) { mod(it, 7) == 0 }
                isLast || strong(notes[k][0]) -> nearest(cur) { isCT(it) }
                else -> nearest(cur) { true }
            }
            out.add(cur)
        }
        if (out.toSet().size == 1 && out.size > 1) {
            val first = out[0]
            out[out.size - 2] = nearest(first + pick(listOf(-1, 1, 2))) { it != first }
        }
        return out
    }

    /** Devuelve notas [s, d, idx] y una etiqueta. */
    private fun genArp(chordDeg: Int, pos: Int, len: Int, g: Groove, mode: Mode): Pair<List<DoubleArray>, String> {
        val beat = if (g.beatSteps) 1 else 4
        val beats = len / beat
        val tp = wpick(TUPLETS.map { it to it.third })
        val n = tp.first
        val sp = beat.toDouble() / n
        fun allowed(i: Int) = mode.allow == null || mode.allow.contains(mod(i, 7))
        val base = mutableListOf(0, 2, 4)
        if (rnd.nextDouble() < 0.4) base.add(6)
        val list = listOf(-14, -7, 0, 7, 14).flatMap { o -> base.map { chordDeg + it + o } }
            .toSet().filter { it in -2..9 && allowed(it) }.sorted()
        val L = min(list.size, ri(3, 5))
        val start = ri(0, max(0, list.size - L))
        val win = list.subList(start, min(list.size, start + L))
        val shape = wpick(ARP_SHAPES)
        val cyc = if (shape == "ciclo irregular") List(pick(listOf(3, 5))) { ri(0, win.size - 1) } else null
        val arpBeats = if (g.beatSteps) min(beats - 2, pick(listOf(3, 4, 6))) else max(1, beats - pick(listOf(1, 1, 2)))
        val count = (arpBeats * n)
        fun idxAt(i: Int): Int {
            val w = win.size
            return when (shape) {
                "sube" -> win[i % w]
                "baja" -> win[w - 1 - (i % w)]
                "sube-baja" -> { val p = max(1, 2 * w - 2); val k = i % p; win[if (k < w) k else p - k] }
                else -> win[cyc!![i % cyc.size]]
            }
        }
        val out = mutableListOf<DoubleArray>()
        for (i in 0 until count) out.add(doubleArrayOf(i * sp, sp * 0.9, idxAt(i).toDouble()))
        val lastI = out.last()[2].toInt()
        val target: (Int) -> Boolean = if (pos == 3) ({ mod(it, 7) == 0 }) else ({ mod(it - chordDeg, 7) in intArrayOf(0, 2, 4) })
        var fin = lastI
        loop@ for (r in 0 until 8) {
            for (x in listOf(lastI + r, lastI - r)) {
                if (target(x) && allowed(x) && x in -2..9) { fin = x; break@loop }
            }
        }
        val fs = (arpBeats * beat).toDouble()
        out.add(doubleArrayOf(fs, min(len - fs, (beat * (if (g.beatSteps) 3 else 2)).toDouble()), fin.toDouble()))
        return out to "${tp.second} · $shape"
    }

    private fun genBass(chordDeg: Int, len: Int, style: String, key: Int, mode: Mode): List<Note> {
        var base = 36 + key
        if (base < 40) base += 12
        var root = base + mode.s[chordDeg]
        if (root > 52) root -= 12
        fun deg(k: Int) = root + mod(mode.s[(chordDeg + k) % 7] - mode.s[chordDeg], 12)
        val third = deg(2); val fifth = deg(4); val sixth = deg(5)
        val out = mutableListOf<Note>()
        fun seq(steps: List<Pair<Int, Int>>, pat: List<Int>) {
            steps.forEachIndexed { i, (s, d) -> if (s < len) out.add(Note(s.toDouble(), min(d, len - s).toDouble(), pat[i])) }
        }
        when (style) {
            "octavas" -> { var s = 0; while (s < len) { out.add(Note(s.toDouble(), 2.0, if ((s / 2) % 2 == 1) root + 12 else root)); s += 2 } }
            "negras" -> { var s = 0; while (s < len) { out.add(Note(s.toDouble(), 4.0, if ((s / 4) % 2 == 1) fifth else root)); s += 4 } }
            "galope" -> { var b = 0; while (b < len) {
                out.add(Note(b.toDouble(), 2.0, root)); out.add(Note(b + 2.0, 1.0, root))
                out.add(Note(b + 3.0, 1.0, if ((b / 4) % 2 == 1) fifth else root + 12)); b += 4 } }
            "walking" -> seq(listOf(0 to 4, 4 to 4, 8 to 4, 12 to 4), listOf(root, third, fifth, sixth))
            "bossa" -> seq(listOf(0 to 6, 6 to 2, 8 to 6, 14 to 2), listOf(root, fifth - 12, root, fifth - 12))
            "reggae" -> seq(listOf(0 to 3, 3 to 1, 6 to 2, 10 to 2, 12 to 3), listOf(root, root, fifth, root + 12, third))
            "rumba" -> seq(listOf(0 to 3, 3 to 3, 6 to 2, 8 to 3, 11 to 3, 14 to 2), listOf(root, root, fifth, root, root, fifth))
            "compas" -> seq(listOf(0 to 2, 2 to 3, 5 to 2, 7 to 2, 9 to 2, 11 to 1), listOf(root, root, fifth, root, fifth, root))
            "funk" -> seq(listOf(0 to 1, 3 to 1, 6 to 1, 7 to 1, 10 to 1, 11 to 1, 14 to 1),
                listOf(root, root + 12, root, fifth, root, root + 12, sixth))
        }
        return out
    }

    private fun genDrums(g: Groove, len: Int): List<Hit> {
        val out = mutableListOf<Hit>()
        fun add(str: String?, kind: Char) {
            str?.forEachIndexed { s, c -> if (s < len && c != '.') out.add(Hit(s, kind, c)) }
        }
        add(g.K, 'K'); add(g.S, 'S'); add(g.R, 'R'); add(g.H, 'H'); add(g.P, 'P')
        if (!g.beatSteps && len >= 12 && rnd.nextDouble() < 0.3) {
            out.add(Hit(len - 2, 'S', 'x')); out.add(Hit(len - 1, 'S', 'x'))
        }
        return out
    }

    private fun genComp(g: Groove, len: Int): List<Hit> {
        val c = g.C ?: return emptyList()
        return c.mapIndexedNotNull { s, ch -> if (ch != '.' && s < len) Hit(s, 'C', ch) else null }
    }
}
