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

    override fun onResume() { super.onResume(); draw() }

    private fun t(s: String, sz: Float = 14f, c: Int = Color.WHITE, b: Boolean = false) = TextView(this).apply {
        text = s; textSize = sz; setTextColor(c); setPadding(0, dp(3), 0, dp(3))
        if (b) setTypeface(typeface, Typeface.BOLD)
    }

    private fun btn(s: String, f: () -> Unit) = Button(this).apply { text = s; setAllCaps(false); setOnClickListener { f() } }

    private fun sw(s: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = s; setTextColor(Color.WHITE); isChecked = on; setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun row(s: String, ok: Boolean, b: String = "Grant", f: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(t(s, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(if (ok) t("✓ on", 13f, Color.parseColor("#4ADE80"), true) else btn(b, f))
    }

    private fun card(title: String, vararg v: View) {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL; l.setPadding(dp(16), dp(12), dp(16), dp(12))
        val bg = GradientDrawable(); bg.setColor(Color.parseColor("#171C24")); bg.cornerRadius = dp(16).toFloat(); l.background = bg
        l.addView(t(title, 13f, Color.parseColor("#7DD3FC"), true))
        v.forEach { l.addView(it) }
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
        card("BATTERY",
            t("$lvl%" + (if (Opt.charging(bi)) " ⚡" else ""), 40f, Color.WHITE, true),
            t("${temp}°C · $health · ${bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)} mV · $ma mA", 13f, Color.LTGRAY),
            t(if (rt != null) "Avg drain %.1f%%/h · about %.1f h left".format(rt, lvl / rt) else "Drain estimate appears after about an hour of use", 13f, Color.LTGRAY))

        val w = Opt.canWrite(this); val u = Opt.canUsage(this); val dpm = Opt.canDeep(this)
        card("POWER-UPS",
            row("System settings control", w) { go(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) },
            row("Usage access (top drainers)", u) { go(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            row("Deep mode", dpm, "Copy command") { copyCmd() },
            t(if (dpm) "Deep mode on: Battery Saver, animation and scan controls unlocked below." else
                "Deep mode needs one grant from a computer: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 11f, Color.GRAY))

        val pr = LinearLayout(this)
        Opt.profs.forEach { f -> pr.addView(btn(f.name) { toast(Opt.apply(this, f)); draw() }, LinearLayout.LayoutParams(0, -2, 1f)) }
        card("PROFILES", t("Saver and Extreme lower brightness, shorten screen timeout, stop sync and clean background apps. Restore puts everything back.", 12f, Color.GRAY), pr)

        card("AUTO-OPTIMIZE",
            sw("Run automatically (about every 15 min)", p.getBoolean("auto", false)) { p.edit().putBoolean("auto", it).apply() },
            t("Screen off: background apps are cleaned. At or below ${p.getInt("th", 20)}%: Extreme profile, restored when charging.", 12f, Color.GRAY),
            btn("Low battery threshold: ${p.getInt("th", 20)}%") {
                val n = listOf(10, 15, 20, 30); p.edit().putInt("th", n[(n.indexOf(p.getInt("th", 20)) + 1) % 4]).apply(); draw()
            },
            btn("Protected apps") { keepDialog() },
            btn("Clean background apps now") { toast("Cleaned ${Opt.clean(this)} apps") })

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
            sg.add(t(title, 14f, Color.WHITE, true)); sg.add(t(body, 12f, Color.GRAY)); if (label != null) sg.add(btn(label, f))
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

        val tp = Opt.top(this)
        card("TOP APPS TODAY", *(if (!u) listOf(t("Grant usage access above to see which apps use the screen most.", 12f, Color.GRAY))
            else if (tp.isEmpty()) listOf(t("Nothing yet.", 12f, Color.GRAY))
            else tp.map { t("${lab(it.first)}  ·  ${it.second / 60000} min", 13f, Color.LTGRAY) }).toTypedArray())
    }
}
