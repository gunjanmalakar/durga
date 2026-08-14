package com.oles.durga

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this)
        val prefs = getSharedPreferences("durga", Context.MODE_PRIVATE)
        if (prefs.getBoolean("logged_in", false)) {
            startActivity(Intent(this, DashboardActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_login)

        val user = findViewById<EditText>(R.id.username)
        val pass = findViewById<EditText>(R.id.password)
        val base = findViewById<EditText>(R.id.baseUrl)
        val btn = findViewById<Button>(R.id.loginBtn)
        val toReg = findViewById<TextView>(R.id.toRegister)
        base.setText(Api.BASE)

        btn.setOnClickListener {
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (u.isEmpty() || p.isEmpty()) { toast("Enter username and password"); return@setOnClickListener }
            Api.setBase(this, base.text.toString().trim())
            btn.isEnabled = false; btn.text = "Signing in…"
            Thread {
                val r = Api.post("api/app_login.php", JSONObject().put("username", u).put("password", p))
                runOnUiThread {
                    btn.isEnabled = true; btn.text = "Log in"
                    if (r.optBoolean("ok", false)) {
                        prefs.edit()
                            .putBoolean("logged_in", true)
                            .putInt("user_id", r.optInt("user_id", 0))
                            .putString("name", r.optString("name", ""))
                            .apply()
                        startActivity(Intent(this, DashboardActivity::class.java)); finish()
                    } else toast(r.optString("error", "Login failed"))
                }
            }.start()
        }
        toReg.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
