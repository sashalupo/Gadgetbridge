package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

class MiBandWorkoutSessionFragment : Fragment(R.layout.fragment_miband_workout_session) {
    private lateinit var gbDevice: GBDevice

    private var actionButton: Button? = null
    private var statusValue: TextView? = null
    private var metricsContainer: View? = null
    private var heartRateValue: TextView? = null
    private var durationValue: TextView? = null
    private var stepsValue: TextView? = null
    private var distanceValue: TextView? = null

    private val uiUpdateHandler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            renderMetrics()
            if (MiBandWorkoutManager.isTracking) {
                uiUpdateHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(ARG_DEVICE, GBDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(ARG_DEVICE)
        } ?: throw IllegalStateException("GBDevice is required")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actionButton = view.findViewById(R.id.workout_add_button)
        statusValue = view.findViewById(R.id.workout_status_value)
        metricsContainer = view.findViewById(R.id.workout_metrics_container)
        heartRateValue = view.findViewById(R.id.workout_heart_rate_value)
        durationValue = view.findViewById(R.id.workout_duration_value)
        stepsValue = view.findViewById(R.id.workout_steps_value)
        distanceValue = view.findViewById(R.id.workout_distance_value)

        actionButton?.setOnClickListener {
            if (MiBandWorkoutManager.isTracking) {
                MiBandWorkoutManager.stopWorkout()
            } else {
                MiBandWorkoutManager.startWorkout(gbDevice)
                uiUpdateHandler.post(uiUpdateRunnable)
            }

            updateUiState()
        }

        updateUiState()
        renderMetrics()
    }

    override fun onResume() {
        super.onResume()
        if (MiBandWorkoutManager.isTracking) {
            uiUpdateHandler.post(uiUpdateRunnable)
        }
        updateUiState()
        renderMetrics()
    }

    override fun onPause() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
        super.onPause()
    }

    override fun onDestroyView() {
        actionButton = null
        statusValue = null
        metricsContainer = null
        heartRateValue = null
        durationValue = null
        stepsValue = null
        distanceValue = null
        super.onDestroyView()
    }

    private fun updateUiState() {
        val isTracking = MiBandWorkoutManager.isTracking
        val trackingDevice = MiBandWorkoutManager.getGbDevice()
        val isCurrentDeviceTracking = isTracking && trackingDevice?.address == gbDevice.address

        if (isTracking && !isCurrentDeviceTracking) {
            statusValue?.text = getString(R.string.miband_workout_another_running, trackingDevice?.aliasOrName ?: "unknown device")
            actionButton?.isEnabled = false
            metricsContainer?.isVisible = false
            return
        }

        actionButton?.isEnabled = true
        statusValue?.setText(
            if (isCurrentDeviceTracking) R.string.miband_workout_status_running
            else R.string.miband_workout_status_idle
        )
        actionButton?.setText(
            if (isCurrentDeviceTracking) R.string.miband_workout_stop_activity
            else R.string.miband_workout_add_activity
        )
        metricsContainer?.isVisible = isCurrentDeviceTracking
        if (!isCurrentDeviceTracking) {
            renderMetrics()
        }
    }

    private fun renderMetrics() {
        val context = context ?: return
        val currentHeartRate = MiBandWorkoutManager.currentHeartRate
        val sessionSteps = MiBandWorkoutManager.sessionSteps
        val sessionStartedAt = MiBandWorkoutManager.sessionStartedAt

        val heartRateText = if (currentHeartRate > 0) {
            context.getString(R.string.bpm_value_unit, currentHeartRate)
        } else {
            context.getString(R.string.activity_type_not_measured)
        }
        val stepLengthCm = ActivityUser().stepLengthCm
        val distanceKm = sessionSteps * stepLengthCm / 100000f

        val durationMillis = if (sessionStartedAt > 0) {
            System.currentTimeMillis() - sessionStartedAt
        } else {
            0L
        }

        heartRateValue?.text = heartRateText
        durationValue?.text = formatDuration(durationMillis)
        stepsValue?.text = sessionSteps.toString()
        distanceValue?.text = context.getString(R.string.steps_distance_unit, distanceKm)
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        private const val ARG_DEVICE = "device"

        fun newInstance(gbDevice: GBDevice): MiBandWorkoutSessionFragment {
            val fragment = MiBandWorkoutSessionFragment()
            val args = Bundle()
            args.putParcelable(ARG_DEVICE, gbDevice)
            fragment.arguments = args
            return fragment
        }
    }
}
