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
import kotlin.math.sqrt


class LiveDataActivity : AppCompatActivity() {

    val inputBufferRes: FloatBuffer = FloatBuffer.allocate(50 * 6)
    val outputBufferRes: FloatBuffer = FloatBuffer.allocate(3) // Adjust the size according to your model's output

    val inputBufferThingy: FloatBuffer = FloatBuffer.allocate(100 * 6)
    val outputBufferThingy: FloatBuffer = FloatBuffer.allocate(11) // Adjust the size according to your model's output


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
    lateinit var textLabelRes: TextView
    lateinit var textLabelThingy: TextView

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)
    private lateinit var tflite_thingy: Interpreter
    private lateinit var tflite_res: Interpreter

    val activitiesRes = mapOf(
            0 to "coughing",
            1 to "hyperventilating",
            2 to "normal"
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
            10 to "sitting/standing"
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

        textLabelRes = findViewById(R.id.currentActRes)
        textLabelThingy = findViewById(R.id.currentActThingy)
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

                    runModelWithResAvailableData()
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

                    runModelWithThingyAvailableData()
                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

        // Load Thingy TFLite model
//        val modelPath_thingy = "model_thingy_accl_gyro_norm_task_51_50.tflite"
        val modelPath_thingy = "model_thingy_accl_gyro_norm_task_51_100.tflite"
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

        val modelPath_res = "model_respeck_accl_gyro_norm_task_52_50.tflite"
        val assetFileDescriptor_res: AssetFileDescriptor = assets.openFd(modelPath_res)
        val fileInputStream_res = assetFileDescriptor_res.createInputStream()
        val fileChannel_res: FileChannel = fileInputStream_res.channel
        val startOffset_res: Long = assetFileDescriptor_res.startOffset
        val declaredLength_res: Long = assetFileDescriptor_res.declaredLength
        val tfliteModel_res: MappedByteBuffer = fileChannel_res.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset_res,
            declaredLength_res
        )

        val options_res = Interpreter.Options()
        tflite_res = Interpreter(tfliteModel_res, options_res)
    }

    @SuppressLint("SetTextI18n")
    fun runModelWithThingyAvailableData() {
        // Make sure you have enough data
        if (dataSet_thingy_accel_x.entryCount < 100 // Thingy use 50
            || dataSet_thingy_accel_x.entryCount % 25 != 0
        ) {
            return
        }

        val entries_thingy_accel_x = dataSet_thingy_accel_x.values
        val entries_thingy_accel_y = dataSet_thingy_accel_y.values
        val entries_thingy_accel_z = dataSet_thingy_accel_z.values

        val entries_thingy_gyro_x = dataSet_thingy_gyro_x.values
        val entries_thingy_gyro_y = dataSet_thingy_gyro_y.values
        val entries_thingy_gyro_z = dataSet_thingy_gyro_z.values


        // Populate the ByteBuffer, where y is the reading and x is the time
        val startIndex = dataSet_thingy_accel_x.entryCount - 100
        for (i in startIndex until startIndex + 100) {
            inputBufferThingy.put(entries_thingy_accel_x[i].y)
            inputBufferThingy.put(entries_thingy_accel_y[i].y)
            inputBufferThingy.put(entries_thingy_accel_z[i].y)

            // Normalize the y-values of the gyro data
            val normalizedGyroX = (entries_thingy_gyro_x[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroY = (entries_thingy_gyro_y[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroZ = (entries_thingy_gyro_z[i].y - minVal) / (maxVal - minVal)

            // Now put these normalized values into the inputBuffer
            inputBufferThingy.put(normalizedGyroX)
            inputBufferThingy.put(normalizedGyroY)
            inputBufferThingy.put(normalizedGyroZ)
        }

        inputBufferThingy.rewind()

        // Run inference using TensorFlow Lite
        tflite_thingy.run(inputBufferThingy, outputBufferThingy)

        // Rewind the buffer to the beginning so we can read from it
        outputBufferThingy.rewind()
        inputBufferThingy.rewind()

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

        textLabelThingy.text = "Current Activity: $currentActivity"


        // TODO thingy history
//        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
//        val isRecording = sharedPreferences.getBoolean("recording", false)
//        runOnUiThread{
//            textLabelThingy.text = "Current Activity: $currentActivity"
//        }
//        if (isRecording) {
//            recordData(currentActivity)
//        } else {
//            // don't do anything
//        }

    }




    fun isStable(floatArray: FloatArray, threshold: Float = 0.1f): Boolean {
        val value = (stdDev(floatArray).maxOrNull() ?: 0f)
        Log.i("StableStd", "$value")
        return value < threshold
    }

    fun classify(floatArray: FloatArray, similarityThreshold: Float = 0.2f): String? {
        val (x, y, z) = separateXYZ(floatArray)

        val avgX = x.average()
        val avgY = y.average()
        val avgZ = z.average()

        Log.i("SimiDis", "x: $avgX, y: $avgY, z: $avgZ")

        val condX = (avgX > avgY) && (avgX > avgZ) && (kotlin.math.abs(avgY - avgZ) < similarityThreshold)
        val condXr = (avgX < avgY) && (avgX < avgZ) && (kotlin.math.abs(avgY - avgZ) < similarityThreshold)

        val condY = (avgY > avgX) && (avgY > avgZ) && (kotlin.math.abs(avgX - avgZ) < similarityThreshold)
        val condYr = (avgY < avgX) && (avgY < avgZ) && (kotlin.math.abs(avgX - avgZ) < similarityThreshold)

        val condZ = (avgZ > avgX) && (avgZ > avgY) && (kotlin.math.abs(avgX - avgY) < similarityThreshold)
        val condZr = (avgZ < avgX) && (avgZ < avgY) && (kotlin.math.abs(avgX - avgY) < similarityThreshold)

        return when {
            condX -> "lying on right"
            condY -> null
            condZ -> "lying on back"
            condXr -> "lying on left"
            condYr -> "sitting/standing"
            condZr -> "lying on stomach"
            else -> null
        }
    }

//    fun bufferToFloatArray(buffer: ByteBuffer): FloatArray {
//        val floats = FloatArray(buffer.remaining() / 4)
//        buffer.asFloatBuffer().get(floats)
//        return floats
//    }

    fun separateXYZ(arr: FloatArray): Triple<FloatArray, FloatArray, FloatArray> {
        val x = FloatArray(arr.size / 6) // TODO
        val y = FloatArray(arr.size / 6)
        val z = FloatArray(arr.size / 6)

        for (i in arr.indices step 6) {
            x[i / 6] = arr[i]
            y[i / 6] = arr[i + 1]
            z[i / 6] = arr[i + 2]
        }

        return Triple(x, y, z)
    }

    fun stdDev(arr: FloatArray): FloatArray {
        val meanX = arr.filterIndexed { index, _ -> index % 3 == 0 }.average()
        val meanY = arr.filterIndexed { index, _ -> index % 3 == 1 }.average()
        val meanZ = arr.filterIndexed { index, _ -> index % 3 == 2 }.average()

        val stdDevX = sqrt(arr.filterIndexed { index, _ -> index % 3 == 0 }.map { (it - meanX) * (it - meanX) }.average())
        val stdDevY = sqrt(arr.filterIndexed { index, _ -> index % 3 == 1 }.map { (it - meanY) * (it - meanY) }.average())
        val stdDevZ = sqrt(arr.filterIndexed { index, _ -> index % 3 == 2 }.map { (it - meanZ) * (it - meanZ) }.average())

        return floatArrayOf(stdDevX.toFloat(), stdDevY.toFloat(), stdDevZ.toFloat())
    }



    @SuppressLint("SetTextI18n")
    fun runModelWithResAvailableData() {
        // Make sure you have enough data
        if (dataSet_res_accel_x.entryCount < 50
            || dataSet_res_accel_x.entryCount % 10 != 0 // TODO update database
        ) {
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
            inputBufferRes.put(entries_res_accel_x[i].y)
            inputBufferRes.put(entries_res_accel_y[i].y)
            inputBufferRes.put(entries_res_accel_z[i].y)

            // Log the accelerometer values
            Log.d("AccelDataX", entries_res_accel_x[i].y.toString())
            Log.d("AccelDataY", entries_res_accel_y[i].y.toString())
            Log.d("AccelDataZ", entries_res_accel_z[i].y.toString())

            // Normalize the y-values of the gyro data
            val normalizedGyroX = (entries_res_gyro_x[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroY = (entries_res_gyro_y[i].y - minVal) / (maxVal - minVal)
            val normalizedGyroZ = (entries_res_gyro_z[i].y - minVal) / (maxVal - minVal)

            // Now put these normalized values into the inputBuffer
            inputBufferRes.put(normalizedGyroX)
            inputBufferRes.put(normalizedGyroY)
            inputBufferRes.put(normalizedGyroZ)

            // Log the normalized gyro values
            Log.d("NormalizedGyroX", normalizedGyroX.toString())
            Log.d("NormalizedGyroY", normalizedGyroY.toString())
            Log.d("NormalizedGyroZ", normalizedGyroZ.toString())
        }

        inputBufferRes.rewind()

//        var currentActivity = classify(floatArray)
//        // Rewind the buffer to the beginning so we can read from it
//        if (! isStable(floatArray) || currentActivity == null) {
            // Allocate a ByteBuffer to hold the model's output

        tflite_res.resetVariableTensors()
        // Run inference using TensorFlow Lite

        tflite_res.run(inputBufferRes, outputBufferRes)

        // Rewind the buffer to the beginning so we can read from it
        outputBufferRes.rewind()
        inputBufferRes.rewind()

        // Initialize variables to keep track of the maximum value and corresponding index
        var outputIndex = 0
        var maxValue = 0f

        // Iterate through the output buffer to find the maximum value and index
        for (i in 0 until outputBufferRes.limit()) {
            val currentValue = outputBufferRes.get(i)
            if (currentValue > maxValue) {
                maxValue = currentValue
                outputIndex = i
            }
            Log.i("ArgmaxCur", "Value: $currentValue")
        }
        Log.i("ArgmaxMax", "Value: $maxValue")

        // Log the result
        Log.i("ModelRes", "Class Index: $outputIndex")

        val currentActivity = activitiesRes[outputIndex].toString()
//        }


//        // Update the queue with the new activity
//        if (pastActivities.size >= 1) { // TODO add for smoothing
//            pastActivities.remove()
//        }
//        pastActivities.add(currentActivity)
//        val mostFrequentActivity = findMostFrequentActivity()

        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
        val isRecording = sharedPreferences.getBoolean("recording", false)
        runOnUiThread{
            textLabelRes.text = "Current Activity: $currentActivity"
        }
        if (isRecording) {
            recordData(currentActivity)
        } else {
            // don't do anything
        }
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
        val classifiedLabel = "ascending stairs normal"
        val username = sharedPreferences.getString("username", "").toString()
        val currentDate = System.currentTimeMillis()
        val durationInSeconds = 4L

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


