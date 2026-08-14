package com.oles.durga

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Always-on guardian. Runs as a foreground service so Android keeps it alive in the
 * background: it records location (~1 min), syncs the local queue, and every ~35s asks
 * the server's intelligent check whether the user is in sustained distress — firing the
 * automatic SOS if so. Heart-rate is measured separately (camera) from MeasureActivity.
 */
class GuardianService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var db: LocalDb
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAcc = 0f
    private var lastSosAt = 0L

    private var locationManager: LocationManager? = null
    private val locListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            lastLat = loc.latitude; lastLon = loc.longitude; lastAcc = loc.accuracy
            val p = JSONObject()
                .put("lat", loc.latitude).put("lon", loc.longitude)
                .put("accuracy", loc.accuracy).put("ts", System.currentTimeMillis())
                .put("source", "android-gps")
            db.enqueue("api/save_location.php", p)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        Api.init(this)
        db = LocalDb(this)
        try {
            startForeground(NOTIF_ID, buildNotification("Guardian is protecting you"))
        } catch (e: Exception) {
            // e.g. missing location permission for a location-typed FGS on Android 14 — stop cleanly
            stopSelf(); return
        }
        startLocationUpdates()
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { locationManager?.removeUpdates(locListener) } catch (e: Exception) {}
        // ask the system to restart us
        sendBroadcast(Intent(this, BootReceiver::class.java).setAction("in.co.oles.durga.RESTART"))
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 0f, locListener)
        } catch (e: Exception) {}
        try {
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 0f, locListener)
        } catch (e: Exception) {}
    }

    /** Runs every ~35s on a worker thread: sync queue + intelligent guardian check. */
    private val loop = object : Runnable {
        override fun run() {
            Thread {
                try {
                    flushQueue()
                    val r = Api.post("api/auto_check.php", JSONObject())
                    if (r.optBoolean("ok", false) && r.optBoolean("distress", false)) {
                        val now = System.currentTimeMillis()
                        if (now - lastSosAt > 120_000L) {   // don't spam contacts
                            lastSosAt = now
                            val reasonArr = r.optJSONArray("reasons")
                            val reason = if (reasonArr != null && reasonArr.length() > 0)
                                "Guardian: " + reasonArr.optString(0) else "Guardian risk detected"
                            val summary = Sos.fire(this@GuardianService, reason, lastLat, lastLon, r.optInt("bpm", 0))
                            handler.post { updateNotification("⚠ Distress detected — $summary") }
                        }
                    } else {
                        val lvl = r.optString("level", "calm")
                        handler.post {
                            updateNotification(if (lvl == "calm") "Guardian: all calm" else "Guardian: watching ($lvl)")
                        }
                    }
                } catch (e: Exception) { /* keep running */ }
            }.start()
            handler.postDelayed(this, 35_000L)
        }
    }

    private fun flushQueue() {
        val rows = db.pending(50)
        for (row in rows) {
            val res = Api.post(row.endpoint, JSONObject(row.payload))
            if (res.optBoolean("ok", false)) db.markSynced(row.id)
        }
        db.purgeSyncedOlderThan(24L * 3600 * 1000)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(text: String): Notification {
        val chId = "durga_guardian"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "Durga Guardian", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Keeps Durga protecting you in the background"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, DashboardActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("Durga")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_durga)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIF_ID = 4201
        fun start(ctx: Context) {
            val i = Intent(ctx, GuardianService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, GuardianService::class.java)) }
    }
}
