package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiBandWorkoutDetailActivity : AbstractGBActivity() {
    private lateinit var gbDevice: GBDevice
    private var workoutId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_miband_workout_detail)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        gbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(GBDevice.EXTRA_DEVICE, GBDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
        } ?: throw IllegalStateException("GBDevice is required")

        workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, 0L)

        val workout = MiBandWorkoutHistoryStore.load(gbDevice).find { it.id == workoutId }
        if (workout == null) {
            finish()
            return
        }

        renderWorkout(workout)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun renderWorkout(workout: MiBandWorkoutRecord) {
        val titleView: TextView = findViewById(R.id.workout_detail_title)
        val dateView: TextView = findViewById(R.id.workout_detail_date)
        val statDurationView: TextView = findViewById(R.id.workout_stat_duration)
        val statStepsView: TextView = findViewById(R.id.workout_stat_steps)
        val statHrView: TextView = findViewById(R.id.workout_stat_hr)
        val chartView: LineChart = findViewById(R.id.workout_detail_chart)

        val titleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        titleView.text = getString(workout.type.nameRes)
        dateView.text = titleFormat.format(Date(workout.startedAt))

        val durationSeconds = (workout.endedAt - workout.startedAt) / 1000L
        statDurationView.text = DateUtils.formatElapsedTime(durationSeconds)
        statStepsView.text = workout.totalSteps.toString()
        statHrView.text = if (workout.maxHeartRate > 0) workout.maxHeartRate.toString() else "--"

        setupChart(chartView)

        val hrEntries = workout.samples
            .filter { it.heartRate > 0 }
            .map { Entry(((it.timestamp - workout.startedAt) / 1000f), it.heartRate.toFloat()) }
        val stepsEntries = workout.samples
            .map { Entry(((it.timestamp - workout.startedAt) / 1000f), it.steps.toFloat()) }

        val heartRateDataSet = LineDataSet(hrEntries, getString(R.string.miband_workout_metric_heart_rate)).apply {
            color = ContextCompat.getColor(this@MiBandWorkoutDetailActivity, R.color.chart_heartrate)
            setCircleColor(ContextCompat.getColor(this@MiBandWorkoutDetailActivity, R.color.chart_heartrate))
            circleRadius = 2f
            lineWidth = 2f
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }
        val stepsDataSet = LineDataSet(stepsEntries, getString(R.string.miband_workout_metric_steps)).apply {
            color = ContextCompat.getColor(this@MiBandWorkoutDetailActivity, R.color.chart_activity_light)
            setCircleColor(ContextCompat.getColor(this@MiBandWorkoutDetailActivity, R.color.chart_activity_light))
            circleRadius = 2f
            lineWidth = 2f
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
        }

        chartView.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return DateUtils.formatElapsedTime(value.toLong())
            }
        }

        val dataSets = ArrayList<ILineDataSet>(2)
        if (hrEntries.isNotEmpty()) {
            dataSets.add(heartRateDataSet)
        }
        if (stepsEntries.isNotEmpty()) {
            dataSets.add(stepsDataSet)
        }

        chartView.data = LineData(dataSets)
        chartView.invalidate()
    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setNoDataText(getString(R.string.miband_workout_history_chart_empty))
        chart.setNoDataTextColor(Color.WHITE)
        chart.legend.isEnabled = true
        chart.legend.textColor = Color.WHITE
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.axisMinimum = 0f
        chart.axisRight.textColor = Color.WHITE
        chart.axisRight.isEnabled = true
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.textColor = Color.WHITE
    }

    companion object {
        const val EXTRA_WORKOUT_ID = "workout_id"
    }
}
