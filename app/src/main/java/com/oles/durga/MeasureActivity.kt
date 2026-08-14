package com.oles.durga

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contracts.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Measures heart rate with the rear camera + flash (photoplethysmography): cover the
 * lens and flash with your fingertip. This is real optical pulse capture — the same
 * method as the web app — and it stores readings to the backend every 5 seconds.
 * (A camera is required for heart rate; a touchscreen cannot sense a pulse.)
 */
class MeasureActivity : AppCompatActivity() {

    private val exec = Executors.newSingleThreadExecutor()
    private var camProvider: ProcessCameraProvider? = null

    // PPG state
    private var baseEMA = 0.0; private var acS = 0.0
    private var dbP1 = 0.0; private var dbP2 = 0.0
    private var dispHi = 2.0; private var dispLo = -2.0
    private var lastBeat = 0L
    private val beats = ArrayList<Long>()
    private var lastStore = 0L
    private var lastRed = 0.0

    private lateinit var bpmView: TextView
    private lateinit var statusView: TextView

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else { toast("Camera permission is needed"); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this)
        setContentView(R.layout.activity_measure)
        bpmView = findViewById(R.id.bpm)
        statusView = findViewById(R.id.measStatus)
        findViewById<android.widget.Button>(R.id.stopBtn).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else camPerm.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get(); camProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(exec) { proxy -> analyze(proxy) }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                try { camera.cameraControl.enableTorch(true) } catch (e: Exception) {}
            } catch (e: Exception) {
                toast("Could not start camera: " + e.message)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]
            val buf = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val w = proxy.width; val h = proxy.height
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0
            // sample a coarse grid of pixels
            var y = h / 4
            while (y < h * 3 / 4) {
                var x = w / 4
                while (x < w * 3 / 4) {
                    val idx = y * rowStride + x * pixelStride
                    if (idx + 2 < buf.limit()) {
                        sumR += (buf.get(idx).toInt() and 0xFF)
                        sumG += (buf.get(idx + 1).toInt() and 0xFF)
                        sumB += (buf.get(idx + 2).toInt() and 0xFF)
                        count++
                    }
                    x += 6
                }
                y += 6
            }
            if (count > 0) {
                val red = sumR.toDouble() / count
                val green = sumG.toDouble() / count
                val blue = sumB.toDouble() / count
                process(red, green, blue)
            }
        } catch (e: Exception) {
            // ignore this frame
        } finally {
            proxy.close()
        }
    }

    private fun process(red: Double, green: Double, blue: Double) {
        lastRed = red
        val bright = (red + green + blue) / 3
        val redDom = red >= green - 4 && red >= blue - 4
        val finger = (bright > 38 && redDom) || (red > 80 && red > green * 1.03)
        // use green if the flash clips red
        var sig = red
        if (red >= 250) { if (green < 250) sig = green else if (blue < 250) sig = blue }

        val now = System.currentTimeMillis()
        if (finger) {
            baseEMA = if (baseEMA != 0.0) baseEMA * 0.93 + sig * 0.07 else sig
            val ac = sig - baseEMA
            acS = if (acS != 0.0) acS * 0.55 + ac * 0.45 else ac
            detectBeat(now, acS)
            dispHi = maxOf(acS, dispHi * 0.985); dispLo = minOf(acS, dispLo * 0.985)
            val bpm = curBpm()
            runOnUiThread {
                bpmView.text = if (bpm > 0) bpm.toString() else "…"
                statusView.text = if (bpm > 0) "measuring · $bpm bpm" else "reading… keep still"
            }
            if (bpm > 0 && now - lastStore >= 5000) {
                lastStore = now
                storeVitals(bpm, rmssd(), red)
            }
        } else {
            baseEMA = 0.0; acS = 0.0; dbP1 = 0.0; dbP2 = 0.0
            runOnUiThread {
                bpmView.text = "—"
                statusView.text = "Cover the rear camera + flash with your fingertip"
            }
        }
    }

    private fun detectBeat(now: Long, v: Double) {
        val amp = maxOf(0.5, (dispHi - dispLo) / 2); val thr = amp * 0.28
        if (dbP1 > v && dbP1 >= dbP2 && dbP1 > thr) {
            val iv = if (lastBeat != 0L) now - lastBeat else 0
            if (lastBeat == 0L) lastBeat = now
            else if (iv >= 350) {
                if (iv <= 2000) { beats.add(iv); if (beats.size > 12) beats.removeAt(0) } else { beats.clear() }
                lastBeat = now
            }
        }
        dbP2 = dbP1; dbP1 = v
    }

    private fun curBpm(): Int {
        if (beats.isEmpty()) return 0
        val avg = beats.average()
        return (60000.0 / avg).toInt().coerceIn(30, 220)
    }

    private fun rmssd(): Double {
        if (beats.size < 3) return 0.0
        var s = 0.0
        for (i in 1 until beats.size) { val d = (beats[i] - beats[i - 1]).toDouble(); s += d * d }
        return sqrt(s / (beats.size - 1))
    }

    private fun storeVitals(bpm: Int, hrv: Double, red: Double) {
        val body = JSONObject()
            .put("bpm", bpm).put("hrv", hrv).put("quality", 5).put("red", red)
            .put("ts", System.currentTimeMillis()).put("source", "ppg-android")
        Thread {
            val r = Api.post("api/save_vitals.php", body)
            if (r.optBoolean("distress", false)) {
                runOnUiThread { Toast.makeText(this, "Sustained distress — alerting contacts", Toast.LENGTH_LONG).show() }
                Sos.fire(this, "Sustained anxiety detected", 0.0, 0.0, bpm)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { camProvider?.unbindAll() } catch (e: Exception) {}
        exec.shutdown()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
