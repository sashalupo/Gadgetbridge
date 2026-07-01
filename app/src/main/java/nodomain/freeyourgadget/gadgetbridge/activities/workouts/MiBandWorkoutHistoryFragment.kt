package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiBandWorkoutHistoryFragment : Fragment(R.layout.fragment_miband_workout_history) {
    private lateinit var gbDevice: GBDevice
    private lateinit var historyAdapter: MiBandWorkoutHistoryAdapter

    private var emptyView: TextView? = null

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

        historyAdapter = MiBandWorkoutHistoryAdapter { workout ->
            val intent = Intent(requireContext(), MiBandWorkoutDetailActivity::class.java)
            intent.putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
            intent.putExtra(MiBandWorkoutDetailActivity.EXTRA_WORKOUT_ID, workout.id)
            startActivity(intent)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.workout_history_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
    }

    private fun reloadHistory() {
        val workouts = MiBandWorkoutHistoryStore.load(gbDevice)
        emptyView?.visibility = if (workouts.isEmpty()) View.VISIBLE else View.GONE
        historyAdapter.submit(workouts)
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

        fun submit(newWorkouts: List<MiBandWorkoutRecord>) {
            workouts.clear()
            workouts.addAll(newWorkouts)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = workouts[position]
            holder.bind(workout)
            holder.itemView.setOnClickListener { onWorkoutSelected(workout) }
        }

        override fun getItemCount(): Int = workouts.size

        private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.workout_title)
            private val detailsView: TextView = itemView.findViewById(R.id.workout_date)
            private val durationView: TextView = itemView.findViewById(R.id.workout_duration)
            private val iconView: ImageView = itemView.findViewById(R.id.workout_icon)
            private val titleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            fun bind(workout: MiBandWorkoutRecord) {
                titleView.text = itemView.context.getString(workout.type.nameRes)
                detailsView.text = titleFormat.format(Date(workout.startedAt))
                durationView.text = DateUtils.formatElapsedTime((workout.endedAt - workout.startedAt) / 1000L)
                
                iconView.setImageResource(workout.type.iconRes)
                
                // Set icon color based on type
                val color = when(workout.type) {
                    MiBandWorkoutType.WALKING -> Color.parseColor("#00A9E0")
                    MiBandWorkoutType.CLIMBING -> Color.parseColor("#FF8200")
                    MiBandWorkoutType.GRAVEL -> Color.parseColor("#59b22c")
                    MiBandWorkoutType.ANIMAL_FLOW -> Color.parseColor("#6C20B3")
                }
                iconView.backgroundTintList = ColorStateList.valueOf(color)
            }
        }
    }
}

