package com.specknet.pdiotapp

import android.content.Context

class LoginSingleton private constructor(context: Context) {

    val loginDB: LoginDB = LoginDB(context.applicationContext)

    companion object {
        @Volatile
        private var instance: LoginSingleton? = null

        fun getInstance(context: Context): LoginSingleton {
            return instance ?: synchronized(this) {
                instance ?: LoginSingleton(context).also { instance = it }
            }
        }
    }
}