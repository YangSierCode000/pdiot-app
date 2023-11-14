package com.specknet.pdiotapp.user

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.specknet.pdiotapp.R
import android.widget.Button
import android.widget.DatePicker
import android.widget.Toast
import java.util.*
import android.content.Intent


class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val datePickerFrom: DatePicker = findViewById(R.id.datePickerFrom)
        val datePickerTo: DatePicker = findViewById(R.id.datePickerTo)

        val btnConfirm: Button = findViewById(R.id.btnConfirm)
        btnConfirm.setOnClickListener {
            val fromDate = getDateFromDatePicker(datePickerFrom)
            val toDate = getDateFromDatePicker(datePickerTo)

            if (fromDate <= toDate) {
                Toast.makeText(this, "Proceeding...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ViewActivity::class.java)
                intent.putExtra("FROM_DATE", fromDate)
                intent.putExtra("TO_DATE", toDate)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            } else {
                // Dates are invalid, show a toast
                Toast.makeText(this, "Invalid date combination.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDateFromDatePicker(datePicker: DatePicker): Long {
        val day = datePicker.dayOfMonth
        val month = datePicker.month
        val year = datePicker.year

        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)

        return calendar.timeInMillis
    }
}