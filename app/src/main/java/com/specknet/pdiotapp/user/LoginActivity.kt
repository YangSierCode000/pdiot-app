package com.specknet.pdiotapp.user

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.specknet.pdiotapp.MainActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth
        val etUsername2 = findViewById<EditText>(R.id.etUsername2)
        val etPassword2 = findViewById<EditText>(R.id.etPassword2)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        //val currentUser = auth.currentUser
        //if (currentUser != null) {
        //    val intent = Intent(this, MainActivity::class.java)
        //    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        //    startActivity(intent)
       // }

        btnLogin.setOnClickListener {
            val username = etUsername2.text.toString()
            val password = etPassword2.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()){
                val sharedPreferences =
                    getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putBoolean("isLoggedIn", true)
                editor.putString("username", username)
                editor.apply()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            else{
                Toast.makeText(
                baseContext,
                "Authentication failed.",
                Toast.LENGTH_SHORT,
            ).show()}
        }

        // Set a click listener for the sign-up text
        tvSignUp.setOnClickListener {
            // Navigate to the SignUpActivity
            val intent = Intent(this, SignupActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }
}