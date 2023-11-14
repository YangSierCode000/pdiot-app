package com.specknet.pdiotapp

import android.content.Context

class HistorySingleton private constructor(context: Context) {

    val historyDB: HistoryDB = HistoryDB(context.applicationContext)

    companion object {
        @Volatile
        private var instance: HistorySingleton? = null

        fun getInstance(context: Context): HistorySingleton {
            return instance ?: synchronized(this) {
                instance ?: HistorySingleton(context).also { instance = it }
            }
        }
    }
}