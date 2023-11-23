package com.specknet.pdiotapp.live

//import org.apache.commons.math3.complex.Complex
//import org.apache.commons.math3.transform.DftNormalization
//import org.apache.commons.math3.transform.FastFourierTransformer
//import org.apache.commons.math3.transform.TransformType
//import org.apache.commons.math3.util.MathArrays
//import android.app.Activity
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.HistoryDB
import com.specknet.pdiotapp.HistorySingleton
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.LinkedList
import java.util.Queue


class LiveDataActivity : AppCompatActivity() {

    val inputBuffer: FloatBuffer = FloatBuffer.allocate(50 * 6)

    val outputBufferResA: FloatBuffer = FloatBuffer.allocate(6) // Adjust the size according to your model's output
    val outputBufferResS: FloatBuffer = FloatBuffer.allocate(4) // Adjust the size according to your model's output
    val outputBufferThingy: FloatBuffer = FloatBuffer.allocate(12) // Adjust the size according to your model's output


    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_res_gyro_x: LineDataSet
    lateinit var dataSet_res_gyro_y: LineDataSet
    lateinit var dataSet_res_gyro_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    lateinit var dataSet_thingy_gyro_x: LineDataSet
    lateinit var dataSet_thingy_gyro_y: LineDataSet
    lateinit var dataSet_thingy_gyro_z: LineDataSet

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart
    lateinit var textLabel: TextView

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)
    private lateinit var tflite_thingy: Interpreter
    private lateinit var tflite_res_a: Interpreter
    private lateinit var tflite_res_s: Interpreter

    val activitiesRes = mapOf(
        0 to "lying down on back",
        1 to "lying down on left",
        2 to "lying down on right",
        3 to "lying down on stomach",
        4 to "other",
        5 to "sitting/standing"
    )

    val activitiesThingy = mapOf(
        0 to "ascending stairs",
        1 to "descending stairs",
        2 to "lying down on back",
        3 to "lying down on left",
        4 to "lying down on right",
        5 to "lying down on stomach",
        6 to "miscellaneous movements",
        7 to "normal walking",
        8 to "running",
        9 to "shuffle walking",
        10 to "sitting",
        11 to "standing"
    )

    val symptomsRes = mapOf(
        0 to "coughing",
        1 to "hyperventilating",
        2 to "normal",
        3 to "other"
    )




    private val pastActivities: Queue<String> = LinkedList()


    val minVal = -300f
    val maxVal = 300f
    private val historySingleton: HistorySingleton by lazy { HistorySingleton.getInstance(applicationContext) }
    private val historyDB: HistoryDB by lazy { historySingleton.historyDB }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        setupCharts()

        textLabel = findViewById(R.id.currentAct)
        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

                    val gyro_x = liveData.gyro.x
                    val gyro_y = liveData.gyro.y
                    val gyro_z = liveData.gyro.z

                    dataSet_res_gyro_x.addEntry(Entry(time, gyro_x))
                    dataSet_res_gyro_y.addEntry(Entry(time, gyro_y))
                    dataSet_res_gyro_z.addEntry(Entry(time, gyro_z))
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val accl_x = liveData.accelX
                    val accl_y = liveData.accelY
                    val accl_z = liveData.accelZ

                    time += 1
                    updateGraph("thingy", accl_x, accl_y, accl_z)

                    val gyro_x = liveData.gyro.x
                    val gyro_y = liveData.gyro.y
                    val gyro_z = liveData.gyro.z

                    dataSet_thingy_gyro_x.addEntry(Entry(time, gyro_x))
                    dataSet_thingy_gyro_y.addEntry(Entry(time, gyro_y))
                    dataSet_thingy_gyro_z.addEntry(Entry(time, gyro_z))
                }
            }
        }

        // Creating a new thread
        val thread = Thread {
            while (true){
                runModelWithResAvailableData()
            }
        }

        // Start the thread
        thread.start()

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

        // Load Thingy TFLite model
        val modelPath_thingy = "tb50a2e.tflite"
        val assetFileDescriptor_thingy: AssetFileDescriptor = assets.openFd(modelPath_thingy)
        val fileInputStream_thingy = assetFileDescriptor_thingy.createInputStream()
        val fileChannel_thingy: FileChannel = fileInputStream_thingy.channel
        val startOffset_thingy: Long = assetFileDescriptor_thingy.startOffset
        val declaredLength_thingy: Long = assetFileDescriptor_thingy.declaredLength
        val tfliteModel_thingy: MappedByteBuffer = fileChannel_thingy.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset_thingy,
            declaredLength_thingy
        )

        val options_thingy = Interpreter.Options()
        tflite_thingy = Interpreter(tfliteModel_thingy, options_thingy)

        val modelPath_res_a = "rb50a1.tflite"
        val assetFileDescriptor_res_a: AssetFileDescriptor = assets.openFd(modelPath_res_a)
        val fileInputStream_res_a = assetFileDescriptor_res_a.createInputStream()
        val fileChannel_res_a: FileChannel = fileInputStream_res_a.channel
        val startOffset_res_a: Long = assetFileDescriptor_res_a.startOffset
        val declaredLength_res_a: Long = assetFileDescriptor_res_a.declaredLength
        val tfliteModel_res_a: MappedByteBuffer = fileChannel_res_a.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset_res_a,
            declaredLength_res_a
        )

        val options_res_a = Interpreter.Options()
        tflite_res_a = Interpreter(tfliteModel_res_a, options_res_a)

        val modelPath_res_s = "rb50s.tflite"
        val assetFileDescriptor_res_s: AssetFileDescriptor = assets.openFd(modelPath_res_s)
        val fileInputStream_res_s = assetFileDescriptor_res_s.createInputStream()
        val fileChannel_res_s: FileChannel = fileInputStream_res_s.channel
        val startOffset_res_s: Long = assetFileDescriptor_res_s.startOffset
        val declaredLength_res_s: Long = assetFileDescriptor_res_s.declaredLength
        val tfliteModel_res_s: MappedByteBuffer = fileChannel_res_s.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset_res_s,
            declaredLength_res_s
        )

        val options_res_s = Interpreter.Options()
        tflite_res_s = Interpreter(tfliteModel_res_s, options_res_s)
    }

    @SuppressLint("SetTextI18n")
    fun runModelWithThingyAvailableData(): String {
        // Make sure you have enough data
        if (dataSet_thingy_accel_x.entryCount < 50 // Thingy use 50
        ) {
            return "N/A"
        }

        val entries_thingy_accel_x = dataSet_thingy_accel_x.values
        val entries_thingy_accel_y = dataSet_thingy_accel_y.values
        val entries_thingy_accel_z = dataSet_thingy_accel_z.values

        val entries_thingy_gyro_x = dataSet_thingy_gyro_x.values
        val entries_thingy_gyro_y = dataSet_thingy_gyro_y.values
        val entries_thingy_gyro_z = dataSet_thingy_gyro_z.values


        // Populate the ByteBuffer, where y is the reading and x is the time
        val startIndex = dataSet_thingy_accel_x.entryCount - 50
        for (i in startIndex until startIndex + 50) {
            this.inputBuffer.put(entries_thingy_accel_x[i].y)
            this.inputBuffer.put(entries_thingy_accel_y[i].y)
            this.inputBuffer.put(entries_thingy_accel_z[i].y)

            // Normalize the y-values of the gyro data
            val normalizedGyroX = (entries_thingy_gyro_x[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroY = (entries_thingy_gyro_y[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroZ = (entries_thingy_gyro_z[i].y - minVal) / (maxVal - minVal)

            // Now put these normalized values into the inputBuffer
            this.inputBuffer.put(normalizedGyroX)
            this.inputBuffer.put(normalizedGyroY)
            this.inputBuffer.put(normalizedGyroZ)
        }

        this.inputBuffer.rewind()

        // Run inference using TensorFlow Lite
        tflite_thingy.run(this.inputBuffer, outputBufferThingy)

        // Rewind the buffer to the beginning so we can read from it
        outputBufferThingy.rewind()
        inputBuffer.rewind()

        // Initialize variables to keep track of the maximum value and corresponding index
        var outputIndex = 0
        var maxValue = 0f

        // Iterate through the output buffer to find the maximum value and index
        for (i in 0 until outputBufferThingy.limit()) {
            val currentValue = outputBufferThingy.get(i)
            if (currentValue > maxValue) {
                maxValue = currentValue
                outputIndex = i
            }
        }

        Log.i("ModelThingy", "Class Index: $outputIndex")

        val currentActivity = activitiesThingy[outputIndex].toString()
        // Update the TextView

        return currentActivity
    }


    @SuppressLint("SetTextI18n")
    fun runModelWithResAvailableData() {
        // Make sure you have enough data
        if (dataSet_res_accel_x.entryCount < 50) {
            val currentActivity_by_thingy = runModelWithThingyAvailableData()
            runOnUiThread{
                textLabel.text = "Respeck: N/A, Thingy: $currentActivity_by_thingy"
            }
            recordData(currentActivity_by_thingy)
            return
        }

        val entries_res_accel_x = dataSet_res_accel_x.values
        val entries_res_accel_y = dataSet_res_accel_y.values
        val entries_res_accel_z = dataSet_res_accel_z.values

        val entries_res_gyro_x = dataSet_res_gyro_x.values
        val entries_res_gyro_y = dataSet_res_gyro_y.values
        val entries_res_gyro_z = dataSet_res_gyro_z.values


        // Populate the ByteBuffer, where y is the reading and x is the time
        val startIndex = dataSet_res_accel_x.entryCount - 50
        Log.i("DataLength", "${dataSet_res_accel_x.entryCount}")
        for (i in startIndex until startIndex + 50) {
            this.inputBuffer.put(entries_res_accel_x[i].y)
            this.inputBuffer.put(entries_res_accel_y[i].y)
            this.inputBuffer.put(entries_res_accel_z[i].y)

            // Log the accelerometer values
            Log.d("AccelDataX", entries_res_accel_x[i].y.toString())
            Log.d("AccelDataY", entries_res_accel_y[i].y.toString())
            Log.d("AccelDataZ", entries_res_accel_z[i].y.toString())

            // Normalize the y-values of the gyro data
            val normalizedGyroX = (entries_res_gyro_x[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroY = (entries_res_gyro_y[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroZ = (entries_res_gyro_z[i].y - minVal) / (maxVal - minVal)

            // Now put these normalized values into the inputBuffer
            inputBuffer.put(normalizedGyroX)
            inputBuffer.put(normalizedGyroY)
            inputBuffer.put(normalizedGyroZ)

            // Log the normalized gyro values
            Log.d("NormalizedGyroX", normalizedGyroX.toString())
            Log.d("NormalizedGyroY", normalizedGyroY.toString())
            Log.d("NormalizedGyroZ", normalizedGyroZ.toString())
        }

        inputBuffer.rewind()

//        var currentActivity = classify(floatArray)
//        // Rewind the buffer to the beginning so we can read from it
//        if (! isStable(floatArray) || currentActivity == null) {
            // Allocate a ByteBuffer to hold the model's output

        tflite_res_a.resetVariableTensors()
        // Run inference using TensorFlow Lite

        tflite_res_a.run(inputBuffer, outputBufferResA)

        // Rewind the buffer to the beginning so we can read from it
        outputBufferResA.rewind()
        inputBuffer.rewind()

        // Initialize variables to keep track of the maximum value and corresponding index
        var outputIndex = 0
        var maxValue = 0f

        // Iterate through the output buffer to find the maximum value and index
        for (i in 0 until outputBufferResA.limit()) {
            val currentValue = outputBufferResA.get(i)
            if (currentValue > maxValue) {
                maxValue = currentValue
                outputIndex = i
            }
            Log.i("ArgmaxCur", "Value: $currentValue")
        }
        Log.i("ArgmaxMax", "Value: $maxValue")
//        if (maxValue < 0.9) {
//            outputIndex = 4
//        }

        // Log the result
        Log.i("ModelRes", "Class Index: $outputIndex")

        var currentSymptom = ""
        val currentActivity_by_res = activitiesRes[outputIndex].toString()
        var currentActivity_by_thingy = runModelWithThingyAvailableData()
        val resultActivity = currentActivity_by_thingy

        if (currentActivity_by_thingy == "sitting" || currentActivity_by_thingy == "standing") {
            currentActivity_by_thingy = "sitting/standing"
        }

        if (currentActivity_by_thingy == currentActivity_by_res) { // agree stationary
            tflite_res_s.resetVariableTensors()
            // Run inference using TensorFlow Lite

            tflite_res_s.run(inputBuffer, outputBufferResS)

            // Rewind the buffer to the beginning so we can read from it
            outputBufferResS.rewind()
            inputBuffer.rewind()

            // Initialize variables to keep track of the maximum value and corresponding index
            outputIndex = 0
            maxValue = 0f

            // Iterate through the output buffer to find the maximum value and index
            for (i in 0 until outputBufferResS.limit()) {
                val currentValue = outputBufferResS.get(i)
                if (currentValue > maxValue) {
                    maxValue = currentValue
                    outputIndex = i
                }
            }

            currentSymptom = symptomsRes[outputIndex].toString()
        }

        // Update the queue with the new activity
        if (pastActivities.size >= 10) { // TODO add for smoothing
            pastActivities.remove()
        }
        pastActivities.add(resultActivity)
        val mostFrequentActivity = findMostFrequentActivity()

        runOnUiThread{
            textLabel.text = "Current State: $mostFrequentActivity $currentSymptom"
        }

        recordData("$mostFrequentActivity $currentSymptom")
    }

    // Function to find the most frequent activity
    private fun findMostFrequentActivity(): String? {
        val frequencyMap = HashMap<String, Int>()
        for (activity in pastActivities) {
            val count = frequencyMap[activity] ?: 0
            frequencyMap[activity] = count + 1
        }
        return frequencyMap.maxByOrNull { it.value }?.key
    }


    fun recordData(activity: String) {
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
        val classifiedLabel = activity
        val username = sharedPreferences.getString("username", "").toString()
        val currentDate = System.currentTimeMillis()
        val durationInSeconds = 1L

        val existingActivityData = historyDB.getActivityData(username, currentDate, currentDate)

        if (existingActivityData.isNotEmpty()) {
            val existingDuration = existingActivityData.first().durationInSeconds
            val updatedDuration = existingDuration + durationInSeconds

            // Update the existing record in the database
            historyDB.updateActivityData(username, currentDate, classifiedLabel, updatedDuration)
        } else {
            // If no record exists, insert a new record
            val activityData = HistoryDB.ActivityData(username, currentDate, classifiedLabel, durationInSeconds)
            historyDB.insertActivityData(activityData)
        }
    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        val entries_res_gyro_x = ArrayList<Entry>()
        val entries_res_gyro_y = ArrayList<Entry>()
        val entries_res_gyro_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_gyro_x = LineDataSet(entries_res_gyro_x, "Gyro X")
        dataSet_res_gyro_y = LineDataSet(entries_res_gyro_y, "Gyro Y")
        dataSet_res_gyro_z = LineDataSet(entries_res_gyro_z, "Gyro Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_gyro_x.setDrawCircles(false)
        dataSet_res_gyro_y.setDrawCircles(false)
        dataSet_res_gyro_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        val entries_thingy_gyro_x = ArrayList<Entry>()
        val entries_thingy_gyro_y = ArrayList<Entry>()
        val entries_thingy_gyro_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_gyro_x = LineDataSet(entries_thingy_gyro_x, "Gyro X")
        dataSet_thingy_gyro_y = LineDataSet(entries_thingy_gyro_y, "Gyro Y")
        dataSet_thingy_gyro_z = LineDataSet(entries_thingy_gyro_z, "Gyro Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_gyro_x.setDrawCircles(false)
        dataSet_thingy_gyro_y.setDrawCircles(false)
        dataSet_thingy_gyro_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}


