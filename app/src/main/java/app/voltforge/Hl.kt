package app.voltforge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.View
import java.io.File

class Ring(c: Context) : View(c) {
    var v = 0f; var big = ""; var small = ""; var col = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun set(value: Float, big: String, small: String, col: Int) {
        this.big = big; this.small = small; this.col = col
        val a = ValueAnimator.ofFloat(0f, value.coerceIn(0f, 100f)); a.duration = 700
        a.addUpdateListener { v = it.animatedValue as Float; invalidate() }; a.start()
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val s = 13f * resources.displayMetrics.density; val r = RectF(s, s, w - s, w - s)
        p.style = Paint.Style.STROKE; p.strokeWidth = s; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.parseColor("#232B36"); c.drawArc(r, 135f, 270f, false, p)
        p.color = col; c.drawArc(r, 135f, 270f * v / 100f, false, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.color = Color.WHITE; p.textSize = w * 0.24f; p.typeface = Typeface.DEFAULT_BOLD; c.drawText(big, w / 2, w / 2 + p.textSize * 0.18f, p)
        p.color = Color.parseColor("#9AA4B2"); p.textSize = w * 0.09f; p.typeface = Typeface.DEFAULT; c.drawText(small, w / 2, w / 2 + w * 0.24f, p)
    }
    override fun onMeasure(a: Int, b: Int) { val w = MeasureSpec.getSize(a); setMeasuredDimension(w, w) }
}

object Hl {
    class H(val pct: Int, val full: Int, val cyc: Int, val src: String, val remain: Int, val des: Int, val conf: String)

    private fun rd(n: String): Long? {
        for (d in listOf("battery", "bms", "Battery", "main")) try { return File("/sys/class/power_supply/$d/$n").readText().trim().toLong() } catch (e: Exception) {}
        return null
    }

    private fun mah(v: Long?): Int? {
        if (v == null || v <= 0L) return null
        val m = if (v > 20000L) v / 1000 else v
        return if (m in 500L..20000L) m.toInt() else null
    }

    fun read(c: Context, dsg: Int): H {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = Opt.batt(c)?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val remain = mah(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toLong())
        val cyc = (rd("cycle_count") ?: -1L).toInt()
        val gf = mah(rd("charge_full")); val d = mah(rd("charge_full_design")) ?: dsg
        var src = "fuel gauge"; var full: Int
        if (gf != null) full = gf
        else if (remain != null && lvl in 8..100) { full = remain * 100 / lvl; src = "estimated" }
        else return H(-1, dsg, cyc, "unavailable", dsg * lvl / 100, dsg, "none")
        var pct = (full * 100 / d).coerceIn(0, 100)
        var n = 0
        if (src == "estimated") {
            val p = Opt.prefs(c); val now = System.currentTimeMillis()
            val l = p.getString("hest", "")!!.split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toMutableList()
            if (lvl in 20..95 && now - p.getLong("hts", 0L) > 600000L) {
                l.add(pct); while (l.size > 12) l.removeAt(0)
                p.edit().putString("hest", l.joinToString(",")).putLong("hts", now).apply()
            }
            n = l.size
            if (l.size >= 3) { pct = l.sorted()[l.size / 2]; full = d * pct / 100; src = "estimated, averaged" }
        }
        return H(pct, full, cyc, src, remain ?: (full * lvl / 100), d, if (src == "fuel gauge") "high" else if (n >= 6) "medium" else "low")
    }

    fun verdict(p: Int) = if (p >= 90) "Excellent" else if (p >= 80) "Good" else if (p >= 70) "Worn" else if (p >= 60) "Weak" else "Replace soon"
    fun color(p: Int) = if (p >= 80) "#4ADE80" else if (p >= 70) "#FBBF24" else "#F87171"
    fun fmt(h: Double): String { val m = (h * 60).toInt(); return if (m >= 2880) "${m / 1440}d ${m % 1440 / 60}h" else "${m / 60}h ${m % 60}m" }

    fun scen(h: H, ma: Int, chg: Boolean): List<Pair<String, Double>> {
        val r = h.remain.toDouble()
        val l = arrayListOf("Screen on, mixed use" to r / 380, "Video or social feeds" to r / 480, "Gaming" to r / 800, "Screen off, idle" to r / 22)
        if (!chg && ma in 60..3000) l.add(0, "At your use right now" to r / ma)
        l.add("Full charge, screen on" to h.full / 380.0)
        return l
    }
}
