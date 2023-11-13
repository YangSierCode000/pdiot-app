package com.specknet.pdiotapp.user

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.specknet.pdiotapp.user.LoginActivity
import com.specknet.pdiotapp.R

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSignup.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            if (isValidCredentials(username, password)) {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            } else {
                showToast("Invalid credentials")
            }
        }

        // Set a click listener for the sign-up text
        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
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