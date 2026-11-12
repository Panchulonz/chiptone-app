package cl.conclusa.chiptone

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Sintetizador offline estilo NES: pulso + triángulo (16 niveles) + ruido sample&hold.
 * Renderiza uno o varios motivos seguidos a PCM 16-bit mono.
 */
class Synth(private val sr: Int = SAMPLE_RATE, private val rnd: Random = Random.Default) {

    companion object { const val SAMPLE_RATE = 44100 }

    private lateinit var mix: FloatArray
    private lateinit var send: FloatArray

    fun render(motifs: List<Motif>, st: Settings): ShortArray {
        val total = motifs.sumOf { it.durSec }
        val tail = if (st.echo) 1.3 else 0.35
        val n = ((total + tail) * sr).toInt()
        mix = FloatArray(n)
        send = FloatArray(n)

        var t0 = 0.0
        for (m in motifs) { schedule(m, t0, st); t0 += m.durSec }

        if (st.echo) {
            val first = motifs[0]
            val dly = (min(1.4, first.stepSec * (if (first.groove.beatSteps) 1.5 else 3.0)) * sr).toInt().coerceAtLeast(1)
            val d = FloatArray(n)
            for (i in dly until n) {
                d[i] = send[i - dly] + 0.32f * d[i - dly]
                mix[i] += 0.45f * d[i]
            }
        }

        // master 0.7 + limitador suave + volumen de usuario
        var peak = 0f
        for (v in mix) peak = max(peak, abs(v))
        val norm = if (peak * 0.7f > 0.95f) 0.95f / (peak * 0.7f) else 1f
        val vol = st.volume.coerceIn(0, 100) / 100f
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = tanh((mix[i] * 0.7f * norm).toDouble()).toFloat() * vol
            out[i] = (v * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    // ---------------- programación de un motivo ----------------

    private fun timeOf(s: Double, step: Double, g: Groove): Double {
        var t = s * step
        if (g.swingGrid == 16 && s % 2.0 == 1.0) t += g.swingAmt * step
        if (g.swingGrid == 8 && s % 4.0 == 2.0) t += g.swingAmt * 2 * step
        return t
    }

    private fun schedule(m: Motif, t0: Double, st: Settings) {
        val g = m.groove
        val step = m.stepSec
        fun T(s: Double) = t0 + timeOf(s, step, g)
        fun D(s: Double, d: Double) = timeOf(s + d, step, g) - timeOf(s, step, g)

        val melVol = if (m.harm != null) 0.17 else 0.22
        for (n in m.mel) tone(true, m.duty, Music.hz(n.m), T(n.s), D(n.s, n.d) * m.artic, melVol, vib = true, toSend = true)
        m.harm?.forEach { n -> tone(true, m.duty, Music.hz(n.m), T(n.s), D(n.s, n.d) * m.artic, 0.13, vib = true, toSend = true) }
        for (n in m.bass) tone(false, 0.5, Music.hz(n.m), T(n.s), D(n.s, n.d) * 0.82, 0.5, vib = false, toSend = false)

        if (g.flamenco) {
            val notes4 = m.chord.toList() + (m.chord[0] + 12)
            for (h in m.comp) {
                val t = T(h.s.toDouble())
                if (h.v == 'g') { drum('G', h.v, t); continue }
                val ord = if (rnd.nextDouble() < 0.3) notes4.reversed() else notes4
                val vol = if (h.v == 'X') 0.075 else 0.045
                ord.forEachIndexed { i, c -> tone(true, 0.25, Music.hz(c), t + i * 0.014, min(step * 0.8, 0.22), vol, vib = false, toSend = false) }
            }
        } else {
            for (h in m.comp) for (c in m.chord) tone(true, 0.125, Music.hz(c), T(h.s.toDouble()), step * 0.7, 0.05, vib = false, toSend = false)
        }

        if (st.drums) for (h in m.drums) drum(h.kind, h.v, T(h.s.toDouble()))
    }

    // ---------------- voces ----------------

    /** pulse=true → onda de pulso con duty; false → triángulo NES de 16 niveles. */
    private fun tone(pulse: Boolean, duty: Double, f: Double, t: Double, dur: Double, vol: Double, vib: Boolean, toSend: Boolean) {
        if (dur <= 0.0) return
        val start = (t * sr).toInt()
        val end = min(((t + dur + 0.03) * sr).toInt(), mix.size)
        if (start >= end) return
        val dec = min(0.08, dur * 0.5)
        val dc = 2 * duty - 1
        val pulseNorm = 1.0 / max(abs(1 - dc), abs(-1 - dc))
        var ph = 0.0
        for (i in start until end) {
            val tt = (i - start).toDouble() / sr
            val env = when {
                tt < 0.004 -> vol * tt / 0.004
                tt < dec -> vol + (vol * 0.62 - vol) * ((tt - 0.004) / max(1e-6, dec - 0.004))
                tt < dur -> vol * 0.62
                else -> vol * 0.62 * (1 - (tt - dur) / 0.03).coerceAtLeast(0.0)
            }
            var ff = f
            if (vib && dur > 0.25 && tt > 0.14) {
                val depth = f * 0.012 * ((tt - 0.14) / (dur - 0.14)).coerceIn(0.0, 1.0)
                ff += depth * sin(2 * PI * 6 * tt)
            }
            ph += ff / sr
            ph -= floor(ph)
            val v = if (pulse) ((if (ph < duty) 1.0 else -1.0) - dc) * pulseNorm
                    else ((4 * abs(ph - 0.5) - 1) * 7.5).roundToInt() / 7.5
            val s = (v * env).toFloat()
            mix[i] += s
            if (toSend) send[i] += s
        }
    }

    private fun drum(kind: Char, v: Char, t: Double) {
        when (kind) {
            'K' -> kick(t)
            'S' -> {
                val ghost = v == 'g'
                noiseHit(t, if (ghost) 0.05 else 0.13, if (ghost) 0.1 else 0.38, true, 1900.0, 0.8)
                if (!ghost) sweepTri(t, 200.0, 120.0, 0.06, 0.35, 0.08)
            }
            'R' -> noiseHit(t, 0.035, 0.32, true, 2600.0, 6.0)
            'P' -> {
                val loud = v == 'X' || v == 'x'
                for (o in doubleArrayOf(0.0, 0.009, 0.017)) noiseHit(t + o, if (loud) 0.07 else 0.04, if (loud) 0.3 else 0.11, true, 1350.0, 1.4)
            }
            'G' -> noiseHit(t, 0.05, 0.28, true, 900.0, 2.0)
            'H' -> noiseHit(t, if (v == 'o') 0.14 else 0.03, if (v == 'o') 0.11 else 0.09, false, 7000.0, 0.707)
        }
    }

    private fun kick(t: Double) = sweepTri(t, 160.0, 42.0, 0.12, 0.95, 0.18)

    /** Triángulo con barrido exponencial de frecuencia y caída exponencial de volumen. */
    private fun sweepTri(t: Double, f0: Double, f1: Double, sweep: Double, vol: Double, decay: Double) {
        val start = (t * sr).toInt()
        val end = min(((t + decay + 0.02) * sr).toInt(), mix.size)
        var ph = 0.0
        for (i in start until end) {
            val tt = (i - start).toDouble() / sr
            val f = f0 * (f1 / f0).pow(min(tt, sweep) / sweep)
            ph += f / sr; ph -= floor(ph)
            val g = vol * (0.001 / vol).pow(min(tt, decay) / decay)
            mix[i] += ((4 * abs(ph - 0.5) - 1) * g).toFloat()
        }
    }

    /** Ráfaga de ruido sample&hold filtrada (pasa banda o pasa altos, biquad RBJ). */
    private fun noiseHit(t: Double, dur: Double, vol: Double, bandpass: Boolean, freq: Double, q: Double) {
        val start = (t * sr).toInt()
        val end = min(((t + dur + 0.02) * sr).toInt(), mix.size)
        if (start >= end) return
        val w0 = 2 * PI * freq / sr
        val cw = cos(w0); val alpha = sin(w0) / (2 * q)
        val b0: Double; val b1: Double; val b2: Double
        if (bandpass) { b0 = alpha; b1 = 0.0; b2 = -alpha }
        else { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2 }
        val a0 = 1 + alpha; val a1 = -2 * cw; val a2 = 1 - alpha
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        var hold = 0.0
        for (i in start until end) {
            val k = i - start
            if (k % 3 == 0) hold = rnd.nextDouble() * 2 - 1
            val x = hold
            val y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x; y2 = y1; y1 = y
            val tt = k.toDouble() / sr
            val g = vol * (0.001 / vol).pow(min(tt, dur) / dur)
            mix[i] += (y * g).toFloat()
        }
    }
}
