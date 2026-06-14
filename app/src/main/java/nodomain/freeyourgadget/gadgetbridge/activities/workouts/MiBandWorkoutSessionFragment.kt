package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.R

class MiBandWorkoutSessionFragment : Fragment(R.layout.fragment_miband_workout_session) {
    private var isTracking = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionButton = view.findViewById<Button>(R.id.workout_add_button)
        val statusValue = view.findViewById<TextView>(R.id.workout_status_value)
        val metricsContainer = view.findViewById<View>(R.id.workout_metrics_container)

        actionButton.setOnClickListener {
            isTracking = !isTracking
            statusValue.setText(
                if (isTracking) R.string.miband_workout_status_running
                else R.string.miband_workout_status_idle
            )
            actionButton.setText(
                if (isTracking) R.string.miband_workout_stop_activity
                else R.string.miband_workout_add_activity
            )
            metricsContainer.isVisible = isTracking
        }
    }
}
