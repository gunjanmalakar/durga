package com.oles.durga

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fires a distress alert:
 *  1) the backend (send_alert.php) records it and emails/SMS-gateways the contacts,
 *  2) the phone also sends a real SMS to each contact natively (SmsManager),
 * so the alert goes out even if the server's SMS gateway isn't configured.
 * Call on a background thread.
 */
object Sos {

    fun fire(ctx: Context, reason: String, lat: Double, lon: Double, bpm: Int): String {
        val body = JSONObject()
            .put("reason", reason)
            .put("lat", lat).put("lon", lon)
            .put("bpm", bpm)
        val res = Api.post("api/send_alert.php", body)

        val message = res.optString("message",
            "DURGA SOS: someone may be in distress." +
                (if (lat != 0.0 || lon != 0.0) " Location: https://maps.google.com/?q=$lat,$lon" else ""))

        var nativeSent = 0
        if (hasSmsPermission(ctx)) {
            val contacts: JSONArray = res.optJSONArray("contacts") ?: JSONArray()
            val phones = HashSet<String>()
            for (i in 0 until contacts.length()) {
                val ph = contacts.optJSONObject(i)?.optString("phone", "") ?: ""
                if (ph.isNotBlank()) phones.add(ph)
            }
            for (ph in phones) {
                try {
                    smsManager(ctx).sendTextMessage(ph, null, trimSms(message), null, null)
                    nativeSent++
                } catch (e: Exception) { /* skip this number */ }
            }
        }
        val emailed = res.optBoolean("any_email", false)
        return "Alert sent" + (if (emailed) " · email ✓" else "") + (if (nativeSent > 0) " · SMS to $nativeSent" else "")
    }

    private fun trimSms(m: String): String = if (m.length <= 300) m else m.substring(0, 300)

    fun hasSmsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.getSystemService(SmsManager::class.java)
        else
            SmsManager.getDefault()
}
