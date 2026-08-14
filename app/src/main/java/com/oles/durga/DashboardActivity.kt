package com.oles.durga

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contracts.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import kotlin.math.hypot

class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // touch-sensing accumulators
    private val touchDwell = ArrayList<Long>()
    private val touchMove = ArrayList<Float>()
    private var downT = 0L; private var downX = 0f; private var downY = 0f; private var moveAcc = 0f

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        maybeRequestBackgroundLocation()
        startGuardian()
    }
    private val bgLocLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this)
        prefs = getSharedPreferences("durga", Context.MODE_PRIVATE)
        setContentView(R.layout.activity_dashboard)

        findViewById<TextView>(R.id.hello).text = "Hi, " + prefs.getString("name", "Guardian")
        findViewById<Button>(R.id.measureBtn).setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        findViewById<Button>(R.id.sosBtn).setOnClickListener { confirmSos() }
        findViewById<Button>(R.id.logoutBtn).setOnClickListener { logout() }

        requestCorePermissions()
        touchLoop()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status).text =
            if (isGuardianAllowed()) "Guardian active — protecting you in the background" else "Grant permissions to activate the guardian"
    }

    // ---------- permissions ----------
    private fun requestCorePermissions() {
        val need = ArrayList<String>()
        val core = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        for (p in core) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS)

        if (need.isEmpty()) { maybeRequestBackgroundLocation(); startGuardian() }
        else permLauncher.launch(need.toTypedArray())
    }

    private fun maybeRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("Allow background location")
                .setMessage("So the guardian can protect you and share your location during an SOS even when the app is closed, please choose \"Allow all the time\" on the next screen.")
                .setPositiveButton("Continue") { _, _ -> bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    private fun isGuardianAllowed(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startGuardian() {
        if (!isGuardianAllowed()) {
            findViewById<TextView>(R.id.status).text = "Allow location to activate the background guardian"
            return
        }
        prefs.edit().putBoolean("guardian_on", true).apply()
        GuardianService.start(this)
        findViewById<TextView>(R.id.status).text = "Guardian active — protecting you in the background"
    }

    // ---------- SOS ----------
    private fun confirmSos() {
        AlertDialog.Builder(this)
            .setTitle("Send SOS?")
            .setMessage("This will alert your emergency contacts by SMS and email with your location.")
            .setPositiveButton("Send now") { _, _ ->
                Toast.makeText(this, "Sending SOS…", Toast.LENGTH_SHORT).show()
                Thread {
                    val summary = Sos.fire(this, "Manual SOS", 0.0, 0.0, 0)
                    runOnUiThread { Toast.makeText(this, summary, Toast.LENGTH_LONG).show() }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        GuardianService.stop(this)
        prefs.edit().putBoolean("logged_in", false).putBoolean("guardian_on", false).apply()
        Api.clearSession()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    // ---------- automatic touch sensing ----------
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downT = System.currentTimeMillis(); downX = ev.x; downY = ev.y; moveAcc = 0f }
            MotionEvent.ACTION_MOVE -> { moveAcc += hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()).toFloat(); downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_UP -> { touchDwell.add(System.currentTimeMillis() - downT); touchMove.add(moveAcc) }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun touchLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                flushTouch()
                handler.postDelayed(this, 12_000L)
            }
        }, 12_000L)
    }

    private fun flushTouch() {
        if (touchDwell.isEmpty()) return
        val n = touchDwell.size
        val avgDwell = touchDwell.average()
        val avgMove = touchMove.map { it.toDouble() }.average()
        touchDwell.clear(); touchMove.clear()
        val rate = n / 12.0
        var agit = 20 + rate * 22 + minOf(25.0, avgMove * 0.25) + (if (avgDwell < 130) 15 else 0)
        agit = agit.coerceIn(0.0, 100.0)
        val body = JSONObject()
            .put("taps", n).put("avg_dwell", avgDwell.toInt())
            .put("move_jitter", avgMove).put("agitation", agit.toInt())
            .put("screen", "android").put("ts", System.currentTimeMillis())
        Thread { Api.post("api/save_touch.php", body) }.start()
    }
}
