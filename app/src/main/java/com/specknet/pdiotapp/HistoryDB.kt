package com.specknet.pdiotapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
class HistoryDB(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "historical_data"
        const val DATABASE_VERSION = 1
    }

    object ActivityEntry : BaseColumns {
        const val TABLE_NAME = "activity_data"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_DATE = "date"
        const val COLUMN_ACTIVITY = "activity"
        const val COLUMN_DURATION = "duration"
    }

    data class ActivityData(
        val username: String,
        val date: Long,
        val activity: String,
        val durationInSeconds: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        // Create the table
        val SQL_CREATE_ENTRIES = """
            CREATE TABLE ${ActivityEntry.TABLE_NAME} (
                ${ActivityEntry.COLUMN_USERNAME} TEXT,
                ${ActivityEntry.COLUMN_DATE} INTEGER,
                ${ActivityEntry.COLUMN_ACTIVITY} TEXT,
                ${ActivityEntry.COLUMN_DURATION} INTEGER,
                PRIMARY KEY (${ActivityEntry.COLUMN_USERNAME}, ${ActivityEntry.COLUMN_DATE})
            )
        """
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${ActivityEntry.TABLE_NAME}")
        onCreate(db)
    }

    fun insertActivityData(data: ActivityData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(ActivityEntry.COLUMN_USERNAME, data.username)
            put(ActivityEntry.COLUMN_DATE, data.date)
            put(ActivityEntry.COLUMN_ACTIVITY, data.activity)
            put(ActivityEntry.COLUMN_DURATION, data.durationInSeconds)
        }

        db?.insertWithOnConflict(
            ActivityEntry.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        db.close()
    }

    fun updateActivityData(username: String, date: Long, activity: String, newDuration: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(ActivityEntry.COLUMN_DURATION, newDuration)
        }

        val selection = "${ActivityEntry.COLUMN_USERNAME} = ? AND ${ActivityEntry.COLUMN_DATE} = ? AND ${ActivityEntry.COLUMN_ACTIVITY} = ?"
        val updates = arrayOf(username, date.toString(), activity)

        db.update(
            ActivityEntry.TABLE_NAME,
            values,
            selection,
            updates
        )

        db.close()
    }

    fun getActivityData(username: String, startDate: Long, endDate: Long): List<ActivityData> {
        val db = readableDatabase
        val projection = arrayOf(
            ActivityEntry.COLUMN_USERNAME,
            ActivityEntry.COLUMN_DATE,
            ActivityEntry.COLUMN_ACTIVITY,
            ActivityEntry.COLUMN_DURATION
        )

        val selection = "${ActivityEntry.COLUMN_USERNAME} = ? AND ${ActivityEntry.COLUMN_DATE} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(username, startDate.toString(), endDate.toString())

        val cursor = db.query(
            ActivityEntry.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        val activityList = mutableListOf<ActivityData>()

        with(cursor) {
            while (moveToNext()) {
                val fetchedUsername = getString(getColumnIndexOrThrow(ActivityEntry.COLUMN_USERNAME))
                val date = getLong(getColumnIndexOrThrow(ActivityEntry.COLUMN_DATE))
                val activity = getString(getColumnIndexOrThrow(ActivityEntry.COLUMN_ACTIVITY))
                val duration = getLong(getColumnIndexOrThrow(ActivityEntry.COLUMN_DURATION))

                val activityData = ActivityData(fetchedUsername, date, activity, duration)
                activityList.add(activityData)
            }
        }

        cursor.close()
        db.close()

        return activityList
    }
}