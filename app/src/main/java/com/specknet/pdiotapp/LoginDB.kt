package com.specknet.pdiotapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
class LoginDB(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "login_info"
        const val DATABASE_VERSION = 1
    }

    object LoginActivityEntry : BaseColumns {
        const val COLUMN_PASSWORD= "password"
        const val TABLE_NAME = "login_data"
        const val COLUMN_USERNAME = "username"

    }

    data class ActivityData(
        val username: String,
        val password: String,

    )

    override fun onCreate(db: SQLiteDatabase) {
        // Create the table
        val SQL_CREATE_ENTRIES = """
            CREATE TABLE ${LoginActivityEntry.TABLE_NAME} (
                ${LoginActivityEntry.COLUMN_USERNAME} TEXT,
                
                ${LoginActivityEntry.COLUMN_PASSWORD} TEXT,
                
                PRIMARY KEY (${LoginActivityEntry.COLUMN_USERNAME}, ${LoginActivityEntry.COLUMN_PASSWORD})
            )
        """
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${LoginActivityEntry.TABLE_NAME}")
        onCreate(db)
    }

    fun insertActivityData(data: ActivityData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(LoginActivityEntry.COLUMN_USERNAME, data.username)
            put(LoginActivityEntry.COLUMN_PASSWORD, data.password)

        }

        db?.insertWithOnConflict(
            LoginActivityEntry.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        db.close()
    }

    fun updateActivityData(username: String, password: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(LoginActivityEntry.COLUMN_PASSWORD, password)
        }

        val selection = "${LoginActivityEntry.COLUMN_USERNAME} = ? AND ${LoginActivityEntry.COLUMN_PASSWORD} = ?"
        val updates = arrayOf(username, password)

        db.update(
            LoginActivityEntry.TABLE_NAME,
            values,
            selection,
            updates
        )

        db.close()
    }

    fun isLoginValid(username: String, password: String): Boolean {
        val db = readableDatabase
        val projection = arrayOf(
            LoginActivityEntry.COLUMN_USERNAME,
            LoginActivityEntry.COLUMN_PASSWORD
        )

        val selection = "${LoginActivityEntry.COLUMN_USERNAME} = ? AND ${LoginActivityEntry.COLUMN_PASSWORD} = ?"
        val selectionArgs = arrayOf(username, password)

        val cursor = db.query(
            LoginActivityEntry.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        val isValid = cursor.count > 0

        cursor.close()
        db.close()

        return isValid
    }

    fun usernameExist(username: String): Boolean {
        val db = readableDatabase
        val projection = arrayOf(LoginActivityEntry.COLUMN_USERNAME)
        val selection = "${LoginActivityEntry.COLUMN_USERNAME} = ?"
        val selectionArgs = arrayOf(username)

        val cursor = db.query(
            LoginActivityEntry.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        val exists = cursor.count > 0

        cursor.close()
        db.close()

        return exists
    }

}