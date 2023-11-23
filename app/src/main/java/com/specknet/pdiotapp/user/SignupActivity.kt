package com.specknet.pdiotapp.user

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.LoginDB
import com.specknet.pdiotapp.LoginSingleton

class SignupActivity : AppCompatActivity() {
    private val loginSingleton: LoginSingleton by lazy { LoginSingleton.getInstance(applicationContext) }
    private val loginDB: LoginDB by lazy { loginSingleton.loginDB }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val etUsername = findViewById<EditText>(R.id.etUsername3)
        val etPassword = findViewById<EditText>(R.id.etPassword3)
        val confirmPassword = findViewById<EditText>(R.id.etPassword4)
        val btnSignup = findViewById<Button>(R.id.btnSignUp)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSignup.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            val confirmPassword = confirmPassword.text.toString()

            if (password == confirmPassword){
                if(loginDB.usernameExist(username)){
                    showToast("Username exists, try login?")
                }
                else{
                    val newData = LoginDB.ActivityData(username, password)
                    loginDB.insertActivityData(newData)
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    showToast("Registration successful, redirecting to Login!")
                }
            }
            else{
                showToast("Mismatched password.")
            }

        }

        // Set a click listener for the sign-up text
        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}