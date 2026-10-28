package app.voltforge

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

class Prof(val name: String, val bright: Int, val timeout: Int, val sync: Boolean, val btOff: Boolean, val lvl: Int)

object Opt {
    val profs = listOf(
        Prof("Restore", 0, 0, true, false, -1),
        Prof("Saver", 30, 30000, false, false, 1),
        Prof("Extreme", 12, 15000, false, true, 2)
    )
    private val sys = listOf(
        Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS, Settings.System.SCREEN_OFF_TIMEOUT,
        Settings.System.HAPTIC_FEEDBACK_ENABLED, Settings.System.SOUND_EFFECTS_ENABLED
    )
    private val glb = listOf(
        "low_power", "window_animation_scale", "transition_animation_scale", "animator_duration_scale",
        "wifi_scan_always_enabled", "ble_scan_always_enabled"
    )

    fun prefs(c: Context) = c.getSharedPreferences("vf", Context.MODE_PRIVATE)
    fun canWrite(c: Context) = Settings.System.canWrite(c)
    fun canDeep(c: Context) = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    fun canUsage(c: Context) = (c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    fun batt(c: Context): Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    fun charging(b: Intent?): Boolean { val s = b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0; return s == 2 || s == 5 }

    fun snap(c: Context) {
        val p = prefs(c); if (p.contains("snap")) return
        val r = c.contentResolver
        val e = p.edit().putBoolean("snap", true).putBoolean("o_sync", ContentResolver.getMasterSyncAutomatically())
        sys.forEach { e.putString("s_$it", Settings.System.getString(r, it)) }
        glb.forEach { e.putString("g_$it", Settings.Global.getString(r, it)) }
        e.apply()
    }

    fun restore(c: Context) {
        val p = prefs(c); if (!p.contains("snap")) return
        val r = c.contentResolver
        ContentResolver.setMasterSyncAutomatically(p.getBoolean("o_sync", true))
        if (canWrite(c)) sys.forEach { k -> p.getString("s_$k", null)?.let { Settings.System.putString(r, k, it) } }
        if (canDeep(c)) glb.forEach { k -> p.getString("g_$k", null)?.let { try { Settings.Global.putString(r, k, it) } catch (e: Exception) {} } }
        p.edit().remove("snap").apply()
    }

    fun setSys(c: Context, k: String, v: Int): Boolean {
        if (!canWrite(c)) return false
        snap(c); Settings.System.putInt(c.contentResolver, k, v); return true
    }

    fun g(c: Context, k: String, v: String) {
        if (!canDeep(c)) return
        snap(c); try { Settings.Global.putString(c.contentResolver, k, v) } catch (e: Exception) {}
    }

    fun anim(c: Context, v: String) = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { g(c, it, v) }

    @Suppress("DEPRECATION")
    fun bt(on: Boolean) {
        try { val a = BluetoothAdapter.getDefaultAdapter() ?: return; if (on) a.enable() else a.disable() } catch (e: Exception) {}
    }

    fun clean(c: Context, lg: (String) -> Unit = {}): Int {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val keep = prefs(c).getStringSet("keep", emptySet())!! + c.packageName
        var n = 0
        for (a in c.packageManager.getInstalledApplications(0)) {
            if ((a.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || a.packageName in keep || a.packageName.startsWith("com.termux")) continue
            am.killBackgroundProcesses(a.packageName); n++; lg("  stop " + c.packageManager.getApplicationLabel(a))
        }
        return n
    }

    fun seedKeep(c: Context) {
        val p = prefs(c); if (p.contains("keep")) return
        val rx = Regex("whatsapp|telegram|signal|messeng|messag|sms|dialer|phone|launcher|keyboard|inputmethod|clock|alarm|authenticator|bank|wallet|pay|maps|spotify|music")
        p.edit().putStringSet("keep", c.packageManager.getInstalledApplications(0).map { it.packageName }.filter { rx.containsMatchIn(it) }.toSet()).apply()
    }

    fun apply(c: Context, p: Prof, lg: (String) -> Unit = {}): String {
        prefs(c).edit().putString("prof", p.name).apply()
        if (p.lvl < 0) { lg("> restoring saved settings"); restore(c); lg("[ok] original settings restored"); return "Original settings restored" }
        snap(c); lg("> snapshot saved, undo is available")
        val w = canWrite(c)
        if (w) {
            lg("> display: brightness ${p.bright}%, screen timeout ${p.timeout / 1000}s")
            setSys(c, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            setSys(c, Settings.System.SCREEN_BRIGHTNESS, maxOf(1, p.bright * 255 / 100))
            setSys(c, Settings.System.SCREEN_OFF_TIMEOUT, p.timeout)
            lg("> haptics and touch sounds: off")
            setSys(c, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
            setSys(c, Settings.System.SOUND_EFFECTS_ENABLED, 0)
        } else lg("[skip] display tweaks need System settings control")
        ContentResolver.setMasterSyncAutomatically(p.sync); lg("> auto-sync: " + (if (p.sync) "on" else "off"))
        if (p.btOff && prefs(c).getBoolean("btoff", true)) { bt(false); lg("> bluetooth: off") }
        lg("> asking background apps to stop (idle ones ignore this)")
        val n = clean(c, lg)
        if (canDeep(c)) {
            lg("> deep mode: scan limits and animations")
            if (p.lvl == 2) { g(c, "low_power", "1"); lg("> system Battery Saver: on") }
            anim(c, if (p.lvl == 2) "0.3" else "0.5")
            g(c, "wifi_scan_always_enabled", "0"); g(c, "ble_scan_always_enabled", "0")
        } else lg("[skip] deep mode not granted (needs one adb grant)")
        lg("[ok] ${p.name} applied, stop request sent to $n apps")
        return (if (w) "" else "Grant system settings control for display tweaks. ") + "${p.name} applied, $n apps signalled"
    }

    fun log(c: Context) {
        val b = batt(c) ?: return
        val p = prefs(c); val now = System.currentTimeMillis()
        val old = p.getString("log", "")!!.split(";").filter { it.isNotEmpty() }.takeLast(95)
        val last = old.lastOrNull()?.split(",")?.get(0)?.toLong() ?: 0L
        if (now - last < 300000L) return
        val l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        p.edit().putString("log", (old + "$now,$l,${if (charging(b)) 1 else 0}").joinToString(";")).apply()
    }

    fun rate(c: Context): Double? {
        val e = prefs(c).getString("log", "")!!.split(";").filter { it.isNotEmpty() }.map { r -> r.split(",").map { it.toLong() } }
        var dl = 0.0; var dt = 0.0
        for (i in 1 until e.size) {
            val a = e[i - 1]; val b = e[i]
            if (a[2] == 0L && b[2] == 0L && b[1] <= a[1] && b[0] - a[0] < 7200000L) { dl += (a[1] - b[1]); dt += (b[0] - a[0]) / 3600000.0 }
        }
        return if (dt > 0.4 && dl > 0) dl / dt else null
    }

    fun top(c: Context): List<Pair<String, Long>> {
        if (!canUsage(c)) return emptyList()
        val u = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val m = u.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86400000L, now) ?: return emptyList()
        return m.filter { it.totalTimeInForeground > 60000L && it.packageName != c.packageName }
            .sortedByDescending { it.totalTimeInForeground }.take(8).map { it.packageName to it.totalTimeInForeground }
    }

    fun schedule(c: Context) {
        val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (js.allPendingJobs.none { it.id == 7 })
            js.schedule(JobInfo.Builder(7, ComponentName(c, OptJob::class.java)).setPeriodic(900000L).setPersisted(true).build())
    }
}

class OptJob : JobService() {
    override fun onStartJob(p: JobParameters?): Boolean {
        val c = applicationContext; val pr = Opt.prefs(c)
        Opt.log(c)
        if (pr.getBoolean("auto", false)) {
            val b = Opt.batt(c)
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val chg = Opt.charging(b)
            var note = ""
            if (pr.getBoolean("cleanoff", true) && !(c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) note = "Stop request sent to ${Opt.clean(c)} apps"
            val th = pr.getInt("th", 20) + (if (pr.getInt("hp", 100) < 80) 10 else 0)
            val want = if (chg) 0 else if (lvl <= th) 2 else if (lvl <= th + 15) 1 else 0
            if (want != pr.getInt("stage", 0)) { Opt.apply(c, Opt.profs[want]); pr.edit().putInt("stage", want).apply(); note = Opt.profs[want].name + " at $lvl%" }
            if (note.isNotEmpty()) pr.edit().putString("last", note).putLong("lastT", System.currentTimeMillis()).apply()
        }
        return false
    }
    override fun onStopJob(p: JobParameters?) = false
}
