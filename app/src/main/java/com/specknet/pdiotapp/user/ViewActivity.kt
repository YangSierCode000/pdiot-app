package com.specknet.pdiotapp.user

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import com.specknet.pdiotapp.HistoryDB
import com.specknet.pdiotapp.HistorySingleton
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import android.widget.TableLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewActivity : AppCompatActivity() {

    private lateinit var activityTable: TableLayout
    private val historySingleton: HistorySingleton by lazy { HistorySingleton.getInstance(applicationContext) }
    private val historyDB: HistoryDB by lazy { historySingleton.historyDB }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view)

        val fromDate = intent.getLongExtra("FROM_DATE", 0L)
        val toDate = intent.getLongExtra("TO_DATE", 0L)

        val tvTitle: TextView = findViewById(R.id.tvTitle)
        if (fromDate == toDate) {
            tvTitle.text = "On ${formatDate(fromDate)}..."
        } else {
            tvTitle.text = "From ${formatDate(fromDate)} to ${formatDate(toDate)}..."
        }

        activityTable = findViewById(R.id.activityTable)

        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
        val username = sharedPreferences.getString("username", "").toString()

        val activityDataList = historyDB.getActivityData(username, fromDate, toDate)
        displayActivityTable(activityDataList)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun displayActivityTable(activityDataList: List<HistoryDB.ActivityData>) {
        val activityMap = mutableMapOf<String, Long>()

        for (activityData in activityDataList) {
            val activity = activityData.activity
            val duration = activityData.durationInSeconds

            // If the activity is already in the map, add the duration
            if (activityMap.containsKey(activity)) {
                activityMap[activity] = activityMap[activity]!! + duration
            } else {
                // If the activity is not in the map, add it with the current duration
                activityMap[activity] = duration
            }
        }

        for ((activity, duration) in activityMap) {
            val row = TableRow(this)

            val activityTextView = TextView(this)
            activityTextView.text = "$activity: "

            val durationTextView = TextView(this)
            val durationText = duration.toString()
            durationTextView.text = "$durationText seconds"
            // should display "running: 20 seconds"

            row.addView(activityTextView)
            row.addView(durationTextView)

            activityTable.addView(row)
        }
    }

    private fun formatDate(dateInMillis: Long): String {
        // Add your preferred date formatting logic here
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return dateFormat.format(Date(dateInMillis))
    }

}