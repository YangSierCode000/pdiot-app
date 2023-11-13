package com.specknet.pdiotapp.user

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.specknet.pdiotapp.R
import android.content.Intent
import android.widget.TextView
import com.specknet.pdiotapp.MainActivity
import com.specknet.pdiotapp.utils.Constants

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername2 = findViewById<EditText>(R.id.etUsername2)
        val etPassword2 = findViewById<EditText>(R.id.etPassword2)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener {
            val username = etUsername2.text.toString()
            val password = etPassword2.text.toString()

            if (isValidCredentials(username, password)) {
                val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putBoolean("isLoggedIn", true)
                editor.apply()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                showToast("Invalid credentials")
            }
        }

        // Set a click listener for the sign-up text
        tvSignUp.setOnClickListener {
            // Navigate to the SignUpActivity
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun isValidCredentials(username: String, password: String): Boolean {
        //return username.isNotEmpty() && password.isNotEmpty()
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}