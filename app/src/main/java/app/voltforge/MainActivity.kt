package app.voltforge

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var box: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sv = ScrollView(this); sv.setBackgroundColor(Color.parseColor("#0E1116"))
        box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(14), dp(40), dp(14), dp(30))
        sv.addView(box); setContentView(sv)
        Opt.seedKeep(this); Opt.schedule(this)
    }

    override fun onResume() {
        super.onResume(); draw()
        if (System.currentTimeMillis() - Opt.prefs(this).getLong("chk", 0L) > 21600000L) chk(true)
    }

    private fun t(s: String, sz: Float = 14f, c: Int = Color.WHITE, b: Boolean = false) = TextView(this).apply {
        text = s; textSize = sz; setTextColor(c); setPadding(0, dp(3), 0, dp(3))
        if (b) setTypeface(typeface, Typeface.BOLD)
    }

    private fun btn(s: String, on: Boolean = false, f: () -> Unit) = Button(this).apply {
        text = s; setAllCaps(false); textSize = 13f; setTextColor(Color.parseColor(if (on) "#06222B" else "#FFFFFF")); setPadding(dp(14), dp(10), dp(14), dp(10))
        val g = GradientDrawable(); g.setColor(Color.parseColor(if (on) "#7DD3FC" else "#243241")); g.cornerRadius = dp(22).toFloat(); background = g
        setOnClickListener { f() }
    }

    private fun sw(s: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = s; setTextColor(Color.WHITE); isChecked = on; setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun row(s: String, ok: Boolean, b: String = "Grant", f: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(t(s, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(if (ok) t("✓ on", 13f, Color.parseColor("#4ADE80"), true) else btn(b, false, f))
    }

    private fun card(title: String, vararg v: View) {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL; l.setPadding(dp(18), dp(14), dp(18), dp(14))
        val bg = GradientDrawable(); bg.setColor(Color.parseColor("#151A22")); bg.setStroke(dp(1), Color.parseColor("#222B36")); bg.cornerRadius = dp(22).toFloat(); l.background = bg
        l.addView(t(title, 13f, Color.parseColor("#7DD3FC"), true))
        v.forEach { x -> val q = LinearLayout.LayoutParams(-1, -2); if (x is Button) q.setMargins(0, dp(6), 0, 0); l.addView(x, q) }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); box.addView(l, lp)
    }

    private fun go(a: String, pkg: Boolean = false) {
        try { val i = Intent(a); if (pkg) i.data = Uri.parse("package:$packageName"); startActivity(i) } catch (e: Exception) { toast("Not available on this phone") }
    }

    private fun sys(k: String, v: Int) { if (Opt.setSys(this, k, v)) toast("Saved") else toast("Grant system settings control first") }
    private fun lab(p: String) = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }

    private fun copyCmd() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("cmd", "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"))
        toast("Command copied")
    }

    private fun keepDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val keep = Opt.prefs(this).getStringSet("keep", emptySet())!!.toMutableSet()
        AlertDialog.Builder(this).setTitle("Never clean these apps")
            .setMultiChoiceItems(apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray(),
                BooleanArray(apps.size) { apps[it].packageName in keep }) { _, i, on ->
                if (on) keep.add(apps[i].packageName) else keep.remove(apps[i].packageName)
            }
            .setPositiveButton("Save") { _, _ -> Opt.prefs(this).edit().putStringSet("keep", keep).apply() }.show()
    }

    private fun rowBar(l: String, h: Double, mx: Double): View {
        val v = LinearLayout(this); v.orientation = LinearLayout.VERTICAL; v.setPadding(0, dp(6), 0, dp(6))
        val top = LinearLayout(this)
        top.addView(t(l, 13f, Color.LTGRAY), LinearLayout.LayoutParams(0, -2, 1f)); top.addView(t(Hl.fmt(h), 14f, Color.WHITE, true))
        val bar = LinearLayout(this); val f = View(this); val e = View(this)
        f.setBackgroundColor(Color.parseColor("#7DD3FC")); e.setBackgroundColor(Color.parseColor("#232B36"))
        val fr = (h / mx).toFloat().coerceIn(0.03f, 1f)
        bar.addView(f, LinearLayout.LayoutParams(0, dp(5), fr)); bar.addView(e, LinearLayout.LayoutParams(0, dp(5), 1f - fr))
        v.addView(top); v.addView(bar); return v
    }

    private fun hero(lvl: Int, chg: Boolean, temp: Double, ma: Int, rt: Double?) {
        val p = Opt.prefs(this); val dsg = p.getInt("design", 3000)
        val h = Hl.read(this, dsg)
        val a = Ring(this); val b = Ring(this)
        a.set(lvl.toFloat(), "$lvl%", if (chg) "charging" else "charge", Color.parseColor(if (lvl > 50) "#4ADE80" else if (lvl > 20) "#FBBF24" else "#F87171"))
        if (h.pct >= 0) { p.edit().putInt("hp", h.pct).apply(); b.set(h.pct.toFloat(), "${h.pct}%", "health", Color.parseColor(Hl.color(h.pct))) }
        else b.set(0f, "--", "health", Color.GRAY)
        val rg = LinearLayout(this)
        rg.addView(a, LinearLayout.LayoutParams(0, -2, 1f)); rg.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        val v = ArrayList<View>(); v.add(rg)
        if (h.pct >= 0) v.add(t("${Hl.verdict(h.pct)} · about ${h.full} of ${h.des} mAh (${h.src}, ${h.conf} confidence)", 14f, Color.WHITE, true))
        else v.add(t("This phone doesn't report charge data, so estimates use ${h.des} mAh.", 13f, Color.LTGRAY))
        if (h.cyc > 0) v.add(t("Charge cycles: ${h.cyc}", 13f, Color.LTGRAY))
        v.add(t("${temp}°C · $ma mA now" + (if (rt != null) " · average drain %.1f%%/h".format(rt) else ""), 13f, Color.LTGRAY))
        if (h.pct in 0..79) v.add(t("Worn cell: voltage sags at low charge, which can shut the phone off suddenly. Auto Optimize starts saving power earlier. A new battery is the real fix.", 12f, Color.parseColor("#FBBF24")))
        if (h.src != "fuel gauge") {
            v.add(t("Estimated from the charge counter, most accurate between 20% and 80%. Some phones report ideal values.", 11f, Color.GRAY))
            v.add(btn("Design capacity: ${h.des} mAh (tap to change)") { val o = listOf(3000, 3500, 4000, 5000); p.edit().putInt("design", o[(o.indexOf(dsg) + 1) % 4]).putString("hest", "").apply(); draw() })
        }
        card("BATTERY", *v.toTypedArray())
        optCard()
        val sc = Hl.scen(h, ma, chg); val mx = sc.maxOf { it.second }
        card("TIME LEFT ON THIS CHARGE", *(sc.map { rowBar(it.first, it.second, mx) } + t("Estimates use typical power draw for a phone like yours and your measured capacity. Real use varies.", 11f, Color.GRAY)).toTypedArray())
    }

    private var upBusy = false
    private fun canInst() = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()

    private fun chk(silent: Boolean) {
        Upd.check(this) { code, url, err ->
            val e = Opt.prefs(this).edit().putLong("chk", System.currentTimeMillis())
            if (err == null) e.putInt("newv", code).putString("newurl", url)
            e.apply()
            if (!silent) toast(err ?: (if (code > Upd.cur(this)) "Update available" else "You're on the latest version"))
            draw()
        }
    }

    private fun updCard() {
        val p = Opt.prefs(this); val cur = Upd.cur(this); val nv = p.getInt("newv", 0); val url = p.getString("newurl", null)
        val v = ArrayList<View>()
        val tv = t("Installed: version ${Upd.name(this)}" + (if (nv > cur) "\nNew version available (build $nv)" else ""), 13f, Color.parseColor(if (nv > cur) "#7DD3FC" else "#D3D3D3"))
        v.add(tv)
        if (nv > cur && url != null) v.add(btn(if (upBusy) "Updating..." else "Update now", true) {
            if (upBusy) return@btn
            if (!canInst()) { toast("Allow installs from VoltForge, then tap Update now again"); go("android.settings.MANAGE_UNKNOWN_APP_SOURCES", true); return@btn }
            upBusy = true
            Upd.install(this, url, { tv.text = "Downloading $it%" }, { e ->
                upBusy = false
                if (e != null) { toast(e); draw() } else { tv.text = "Downloaded. Confirm the install on the next screen." }
            })
        })
        v.add(btn("Check for updates") { chk(false) })
        card("UPDATES", *v.toTypedArray())
    }

    private fun optCard() {
        val b = Button(this)
        b.text = "⚡  Optimize automatically"; b.setAllCaps(false); b.textSize = 17f; b.setTextColor(Color.parseColor("#04141C"))
        b.setTypeface(b.typeface, Typeface.BOLD); b.setPadding(dp(16), dp(18), dp(16), dp(18))
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#7DD3FC"), Color.parseColor("#4ADE80")))
        g.cornerRadius = dp(28).toFloat(); b.background = g; b.setOnClickListener { termRun() }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); box.addView(b, lp)
    }

    private fun settingsCard() {
        val p = Opt.prefs(this)
        card("SETTINGS",
            sw("Turn Bluetooth off in Extreme", p.getBoolean("btoff", true)) { p.edit().putBoolean("btoff", it).apply() },
            sw("Clean apps while the screen is off", p.getBoolean("cleanoff", true)) { p.edit().putBoolean("cleanoff", it).apply() },
            btn("Design capacity: ${p.getInt("design", 3000)} mAh (tap to change)") {
                val o = listOf(3000, 3500, 4000, 5000); p.edit().putInt("design", o[(o.indexOf(p.getInt("design", 3000)) + 1) % 4]).putString("hest", "").apply(); draw()
            },
            btn("Restore my original settings") { toast(Opt.apply(this, Opt.profs[0])); draw() })
    }

    private fun termRun() {
        val d = android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val tv = TextView(this); tv.typeface = Typeface.MONOSPACE; tv.textSize = 12f
        tv.setTextColor(Color.parseColor("#39FF6A")); tv.setPadding(dp(14), dp(36), dp(14), dp(14))
        val sc = ScrollView(this); sc.setBackgroundColor(Color.BLACK); sc.addView(tv)
        val h = Handler(Looper.getMainLooper()); var busy = false
        val lg: (String) -> Unit = { s ->
            Thread.sleep(if (s.startsWith("  ")) 25L else 140L)
            h.post { tv.append(s + "\n"); sc.post { sc.fullScroll(View.FOCUS_DOWN) } }
        }
        fun runP(p: Prof, why: String) {
            if (busy) return
            busy = true
            Thread {
                lg("VoltForge ${Upd.name(this)}"); lg("$ voltforge --${p.name.lowercase()}"); lg(why)
                lg("[done] " + Opt.apply(this, p, lg)); lg("$ _"); busy = false
            }.start()
        }
        val lvl = Opt.batt(this)?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val hp = Opt.prefs(this).getInt("hp", 100)
        val prof = if (lvl <= 35 || hp < 75) Opt.profs[2] else Opt.profs[1]
        val why = "[scan] battery $lvl% | health $hp% | plan: ${prof.name.uppercase()} (" + (if (lvl <= 35) "charge is low" else if (hp < 75) "worn battery" else "normal conditions") + ")"
        val bar = LinearLayout(this); bar.setBackgroundColor(Color.BLACK); bar.setPadding(dp(10), dp(6), dp(10), dp(14))
        val l1 = LinearLayout.LayoutParams(0, -2, 1f); l1.setMargins(dp(4), 0, dp(4), 0)
        bar.addView(btn("Close") { d.dismiss(); draw() }, l1)
        bar.addView(btn("Undo", true) { runP(Opt.profs[0], "[undo] restoring saved settings") }, LinearLayout.LayoutParams(l1))
        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL; root.setBackgroundColor(Color.BLACK)
        root.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        d.setContentView(root); d.show()
        runP(prof, why)
    }

    private fun draw() {
        box.removeAllViews()
        box.addView(t("VoltForge", 30f, Color.WHITE, true))
        box.addView(t("Real battery controls. No root.", 13f, Color.GRAY))
        Opt.log(this)
        val bi = Opt.batt(this); val r = contentResolver; val p = Opt.prefs(this)
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val hp = bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1
        val health = when (hp) { 2 -> "Good"; 3 -> "Overheat"; 4 -> "Dead"; 5 -> "Over voltage"; 7 -> "Cold"; else -> "Unknown" }
        var ma = Math.abs((getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        if (ma > 20000) ma /= 1000
        val rt = Opt.rate(this)
        hero(lvl, Opt.charging(bi), temp, ma, rt)
        card("AUTO-OPTIMIZE",
            sw("Auto Optimize", p.getBoolean("auto", false)) { p.edit().putBoolean("auto", it).apply() },
            t("Runs about every 15 min. Screen off: cleans background apps. At ${p.getInt("th", 20) + 15}% or less: Saver. At ${p.getInt("th", 20)}% or less: Extreme. Back to normal when charging." + (if (p.getInt("hp", 100) < 80) " Your battery is worn, so these levels are raised by 10%." else "") + (p.getString("last", null)?.let { "\nLast action: $it" } ?: ""), 12f, Color.GRAY),
            btn("Extreme at ${p.getInt("th", 20)}% (tap to change)") {
                val n = listOf(10, 15, 20, 30); p.edit().putInt("th", n[(n.indexOf(p.getInt("th", 20)) + 1) % 4]).apply(); draw()
            },
            btn("Protected apps") { keepDialog() },
            btn("Clean background apps now") { toast("Stop request sent to ${Opt.clean(this)} apps") })

        settingsCard()
        val w = Opt.canWrite(this); val u = Opt.canUsage(this); val dpm = Opt.canDeep(this)
        card("POWER-UPS",
            row("System settings control", w) { go(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) },
            row("Usage access (top drainers)", u) { go(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            row("Deep mode", dpm, "Copy command") { copyCmd() },
            t(if (dpm) "Deep mode on: Battery Saver, animation and scan controls unlocked below." else
                "Deep mode needs one grant from a computer: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 11f, Color.GRAY))

        val pr = LinearLayout(this)
        Opt.profs.forEach { f -> val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(dp(3), dp(6), dp(3), 0); pr.addView(btn(f.name, p.getString("prof", "") == f.name) { toast(Opt.apply(this, f)); draw() }, lp) }
        card("PROFILES", t("Saver and Extreme lower brightness, shorten screen timeout, stop sync and clean background apps. Restore puts everything back.", 12f, Color.GRAY), pr)

        val br = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128)
        val sb = SeekBar(this)
        sb.max = 100; sb.progress = br * 100 / 255
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                Opt.setSys(this@MainActivity, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                sys(Settings.System.SCREEN_BRIGHTNESS, maxOf(1, (s?.progress ?: 30) * 255 / 100))
            }
        })
        val tm = LinearLayout(this)
        listOf(15 to "15s", 30 to "30s", 60 to "1m", 120 to "2m").forEach { (sec, l) ->
            tm.addView(btn(l) { sys(Settings.System.SCREEN_OFF_TIMEOUT, sec * 1000) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card("MANUAL CONTROLS",
            t("Brightness", 13f, Color.LTGRAY), sb, t("Screen timeout", 13f, Color.LTGRAY), tm,
            sw("Auto-sync accounts", ContentResolver.getMasterSyncAutomatically()) { Opt.snap(this); ContentResolver.setMasterSyncAutomatically(it) },
            sw("Haptic feedback", Settings.System.getInt(r, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1) { sys(Settings.System.HAPTIC_FEEDBACK_ENABLED, if (it) 1 else 0) },
            sw("Touch sounds", Settings.System.getInt(r, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1) { sys(Settings.System.SOUND_EFFECTS_ENABLED, if (it) 1 else 0) },
            sw("Bluetooth", BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) { Opt.bt(it) })

        if (dpm) card("DEEP MODE",
            sw("Battery Saver (system)", Settings.Global.getString(r, "low_power") == "1") { Opt.g(this, "low_power", if (it) "1" else "0") },
            sw("Faster animations (0.5x)", Settings.Global.getString(r, "animator_duration_scale") == "0.5") { Opt.anim(this, if (it) "0.5" else "1.0") },
            sw("Stop background Wi-Fi and Bluetooth scanning", Settings.Global.getString(r, "wifi_scan_always_enabled") == "0") {
                Opt.g(this, "wifi_scan_always_enabled", if (it) "0" else "1"); Opt.g(this, "ble_scan_always_enabled", if (it) "0" else "1") })

        val sg = ArrayList<View>()
        fun tip(title: String, body: String, label: String? = null, f: () -> Unit = {}) {
            sg.add(t(title, 14f, Color.WHITE, true)); sg.add(t(body, 12f, Color.GRAY)); if (label != null) sg.add(btn(label, false, f))
        }
        val manual = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 0
        if (br > 150 && manual) tip("Screen is very bright", "The display is usually the biggest drain. 35% is readable indoors.", "Set 35%") {
            Opt.setSys(this, Settings.System.SCREEN_BRIGHTNESS_MODE, 0); sys(Settings.System.SCREEN_BRIGHTNESS, 90); draw() }
        if (Settings.System.getInt(r, Settings.System.SCREEN_OFF_TIMEOUT, 30000) > 60000) tip("Screen stays on too long", "Set the timeout to 30 seconds.", "Set 30s") { sys(Settings.System.SCREEN_OFF_TIMEOUT, 30000); draw() }
        if (ContentResolver.getMasterSyncAutomatically()) tip("Auto-sync is on", "Accounts wake the phone to sync all day. Turn off and sync by hand.", "Turn off") { Opt.snap(this); ContentResolver.setMasterSyncAutomatically(false); draw() }
        if (Settings.Secure.getInt(r, "location_mode", 0) == 3) tip("High-accuracy location is on", "GPS plus network scanning costs a lot. Use battery saving mode.", "Open location settings") { go(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
        if (hp != 2) tip("Battery health: $health", "Software can't repair a worn cell. Expect shorter runtime.")
        if (temp >= 40) tip("Phone is running hot", "Heat drains faster. Remove the case and close heavy apps.")
        if (Build.VERSION.SDK_INT >= 29) tip("Dark theme", "On OLED screens, dark theme saves real power.", "Open display settings") { go(Settings.ACTION_DISPLAY_SETTINGS) }
        tip("Find background hogs", "Open battery usage and look for apps with high background time.", "Open battery usage") { go(Intent.ACTION_POWER_USAGE_SUMMARY) }
        card("SUGGESTIONS", *sg.toTypedArray())

        updCard()
        val tp = Opt.top(this)
        card("TOP APPS TODAY", *(if (!u) listOf(t("Grant usage access above to see which apps use the screen most.", 12f, Color.GRAY))
            else if (tp.isEmpty()) listOf(t("Nothing yet.", 12f, Color.GRAY))
            else tp.map { t("${lab(it.first)}  ·  ${it.second / 60000} min", 13f, Color.LTGRAY) }).toTypedArray())
    }
}
