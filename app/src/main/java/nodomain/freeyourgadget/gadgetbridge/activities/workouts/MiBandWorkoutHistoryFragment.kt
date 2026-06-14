package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiBandWorkoutHistoryFragment : Fragment(R.layout.fragment_miband_workout_history) {
    private lateinit var gbDevice: GBDevice
    private lateinit var historyAdapter: MiBandWorkoutHistoryAdapter
    private var selectedWorkoutId: Long? = null

    private var emptyView: TextView? = null
    private var chartTitleView: TextView? = null
    private var chartDetailsView: TextView? = null
    private var chartView: LineChart? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(GBDevice.EXTRA_DEVICE, GBDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(GBDevice.EXTRA_DEVICE)
        } ?: throw IllegalStateException("GBDevice is required")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.workout_history_empty)
        chartTitleView = view.findViewById(R.id.workout_history_chart_title)
        chartDetailsView = view.findViewById(R.id.workout_history_chart_details)
        chartView = view.findViewById(R.id.workout_history_chart)

        setupChart(requireView().findViewById(R.id.workout_history_chart))

        historyAdapter = MiBandWorkoutHistoryAdapter { workout ->
            selectedWorkoutId = workout.id
            historyAdapter.setSelectedWorkoutId(workout.id)
            renderWorkout(workout)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.workout_history_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter

        view.findViewById<Button>(R.id.workout_open_history_button).setOnClickListener {
            val intent = Intent(requireContext(), WorkoutListActivity::class.java)
            intent.putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
    }

    private fun reloadHistory() {
        val workouts = MiBandWorkoutHistoryStore.load(gbDevice)
        emptyView?.visibility = if (workouts.isEmpty()) View.VISIBLE else View.GONE
        historyAdapter.submit(workouts, selectedWorkoutId)

        if (workouts.isEmpty()) {
            chartTitleView?.text = getString(R.string.miband_workout_history_chart_empty)
            chartDetailsView?.text = ""
            chartView?.clear()
            return
        }

        val selectedWorkout = workouts.firstOrNull { it.id == selectedWorkoutId } ?: workouts.first()
        selectedWorkoutId = selectedWorkout.id
        historyAdapter.setSelectedWorkoutId(selectedWorkout.id)
        renderWorkout(selectedWorkout)
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

    private fun renderWorkout(workout: MiBandWorkoutRecord) {
        val titleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        chartTitleView?.text = titleFormat.format(Date(workout.startedAt))
        chartDetailsView?.text = getString(
            R.string.miband_workout_history_item_details,
            workout.totalSteps,
            workout.maxHeartRate,
            DateUtils.formatElapsedTime((workout.endedAt - workout.startedAt) / 1000L)
        )

        val hrEntries = workout.samples
            .filter { it.heartRate > 0 }
            .map { Entry(((it.timestamp - workout.startedAt) / 1000f), it.heartRate.toFloat()) }
        val stepsEntries = workout.samples
            .map { Entry(((it.timestamp - workout.startedAt) / 1000f), it.steps.toFloat()) }

        val heartRateDataSet = LineDataSet(hrEntries, getString(R.string.miband_workout_metric_heart_rate)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.chart_heartrate)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.chart_heartrate))
            circleRadius = 2f
            lineWidth = 2f
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }
        val stepsDataSet = LineDataSet(stepsEntries, getString(R.string.miband_workout_metric_steps)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.chart_activity_light)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.chart_activity_light))
            circleRadius = 2f
            lineWidth = 2f
            setDrawValues(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
        }

        chartView?.xAxis?.valueFormatter = object : ValueFormatter() {
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

        chartView?.data = LineData(dataSets)
        chartView?.invalidate()
    }

    companion object {
        fun newInstance(gbDevice: GBDevice): MiBandWorkoutHistoryFragment {
            return MiBandWorkoutHistoryFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(GBDevice.EXTRA_DEVICE, gbDevice)
                }
            }
        }
    }

    private class MiBandWorkoutHistoryAdapter(
        private val onWorkoutSelected: (MiBandWorkoutRecord) -> Unit
    ) : RecyclerView.Adapter<MiBandWorkoutHistoryAdapter.ViewHolder>() {
        private val workouts = mutableListOf<MiBandWorkoutRecord>()
        private var selectedWorkoutId: Long? = null

        fun submit(newWorkouts: List<MiBandWorkoutRecord>, selectedId: Long?) {
            workouts.clear()
            workouts.addAll(newWorkouts)
            selectedWorkoutId = selectedId
            notifyDataSetChanged()
        }

        fun setSelectedWorkoutId(selectedId: Long?) {
            selectedWorkoutId = selectedId
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_activated_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = workouts[position]
            holder.bind(workout, workout.id == selectedWorkoutId)
            holder.itemView.setOnClickListener { onWorkoutSelected(workout) }
        }

        override fun getItemCount(): Int = workouts.size

        private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(android.R.id.text1)
            private val detailsView: TextView = itemView.findViewById(android.R.id.text2)
            private val titleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            fun bind(workout: MiBandWorkoutRecord, selected: Boolean) {
                titleView.text = titleFormat.format(Date(workout.startedAt))
                detailsView.text = itemView.context.getString(
                    R.string.miband_workout_history_item_details,
                    workout.totalSteps,
                    workout.maxHeartRate,
                    DateUtils.formatElapsedTime((workout.endedAt - workout.startedAt) / 1000L)
                )
                itemView.isActivated = selected
                itemView.setBackgroundColor(if (selected) Color.argb(32, 255, 255, 255) else Color.TRANSPARENT)
            }
        }
    }
}
