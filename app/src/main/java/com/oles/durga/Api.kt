package com.oles.durga

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the Durga PHP/MySQL backend (the same server the web app uses).
 * Session cookies are persisted so the background service stays logged in.
 * Call these on a background thread (they block).
 */
object Api {

    // >>> set this to your server, keep the trailing slash <<<
    var BASE = "https://oles.co.in/durga/"

    private var prefs: android.content.SharedPreferences? = null
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val p = prefs ?: return
            for (c in cookies) {
                p.edit().putString("cookie_" + c.name, c.name + "=" + c.value).apply()
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val p = prefs ?: return emptyList()
            val out = ArrayList<Cookie>()
            for ((k, v) in p.all) {
                if (k.startsWith("cookie_") && v is String) {
                    val eq = v.indexOf('=')
                    if (eq > 0) {
                        Cookie.Builder()
                            .domain(url.host)
                            .path("/")
                            .name(v.substring(0, eq))
                            .value(v.substring(eq + 1))
                            .build().let { out.add(it) }
                    }
                }
            }
            return out
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("durga", Context.MODE_PRIVATE)
        prefs?.getString("base_url", null)?.let { BASE = it }
    }

    fun setBase(ctx: Context, url: String) {
        init(ctx)
        var u = url.trim()
        if (!u.endsWith("/")) u += "/"
        BASE = u
        prefs?.edit()?.putString("base_url", u)?.apply()
    }

    fun clearSession() {
        val p = prefs ?: return
        val e = p.edit()
        for (k in p.all.keys) if (k.startsWith("cookie_")) e.remove(k)
        e.apply()
    }

    /** POST a JSON body to an endpoint (e.g. "api/save_vitals.php"); returns the parsed JSON. */
    fun post(path: String, body: JSONObject): JSONObject {
        return try {
            val req = Request.Builder()
                .url(BASE + path)
                .post(body.toString().toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: "{}"
                try { JSONObject(txt) } catch (e: Exception) {
                    JSONObject().put("ok", false).put("error", "bad response")
                }
            }
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "network error")
        }
    }
}
