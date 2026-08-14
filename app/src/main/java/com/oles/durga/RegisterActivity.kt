package com.oles.durga

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this)
        setContentView(R.layout.activity_register)

        val user = findViewById<EditText>(R.id.rUsername)
        val pass = findViewById<EditText>(R.id.rPassword)
        val name = findViewById<EditText>(R.id.rName)
        val gender = findViewById<Spinner>(R.id.rGender)
        val email = findViewById<EditText>(R.id.rEmail)
        val phone = findViewById<EditText>(R.id.rPhone)
        val c1n = findViewById<EditText>(R.id.rC1Name)
        val c1p = findViewById<EditText>(R.id.rC1Phone)
        val btn = findViewById<Button>(R.id.registerBtn)

        gender.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Select…", "Female", "Male", "Other", "Prefer not to say")
        )

        btn.setOnClickListener {
            val g = gender.selectedItem?.toString() ?: ""
            val body = JSONObject()
                .put("username", user.text.toString().trim())
                .put("password", pass.text.toString())
                .put("name", name.text.toString().trim())
                .put("gender", if (g == "Select…") "" else g)
                .put("email", email.text.toString().trim())
                .put("phone", phone.text.toString().trim())
                .put("contact1", c1n.text.toString().trim())
                .put("contact1_phone", c1p.text.toString().trim())
            btn.isEnabled = false; btn.text = "Creating…"
            Thread {
                val r = Api.post("api/app_register.php", body)
                runOnUiThread {
                    btn.isEnabled = true; btn.text = "Create account"
                    if (r.optBoolean("ok", false)) {
                        getSharedPreferences("durga", Context.MODE_PRIVATE).edit()
                            .putBoolean("logged_in", true)
                            .putInt("user_id", r.optInt("user_id", 0))
                            .putString("name", r.optString("name", ""))
                            .apply()
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finishAffinity()
                    } else Toast.makeText(this, r.optString("error", "Failed"), Toast.LENGTH_LONG).show()
                }
            }.start()
        }
    }
}
