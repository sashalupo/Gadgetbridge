package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MiBandWorkoutSessionFragment : Fragment(R.layout.fragment_miband_workout_session) {
    private lateinit var gbDevice: GBDevice

    private var actionButton: Button? = null
    private var statusValue: TextView? = null
    private var metricsContainer: View? = null
    private var heartRateValue: TextView? = null
    private var durationValue: TextView? = null
    private var stepsValue: TextView? = null
    private var distanceValue: TextView? = null

    private var isTracking = false
    private var pulseScheduler: ScheduledExecutorService? = null
    private var sessionStartedAt = 0L
    private var sessionSteps = 0
    private var currentHeartRate = ActivitySample.NOT_MEASURED
    private val sessionSamples = mutableListOf<MiBandWorkoutSampleRecord>()

    private val realtimeSampleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(GBDevice.EXTRA_DEVICE, GBDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
            }
            if (device == null || device != gbDevice || !isTracking) {
                return
            }

            val realtimeSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE, Serializable::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE)
            }

            handleRealtimeSample(realtimeSample)
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

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            realtimeSampleReceiver,
            IntentFilter(DeviceService.ACTION_REALTIME_SAMPLES)
        )

        actionButton?.setOnClickListener {
            isTracking = !isTracking

            if (isTracking) {
                resetSession()
                startRealtimeTracking()
            } else {
                saveCompletedWorkout()
                stopRealtimeTracking()
            }

            updateUiState()
        }

        updateUiState()
        renderMetrics()
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            startRealtimeTracking()
        }
    }

    override fun onPause() {
        if (isTracking) {
            stopRealtimeTracking()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        stopActivityPulse()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(realtimeSampleReceiver)
        actionButton = null
        statusValue = null
        metricsContainer = null
        heartRateValue = null
        stepsValue = null
        distanceValue = null
        super.onDestroyView()
    }

    private fun updateUiState() {
        statusValue?.setText(
            if (isTracking) R.string.miband_workout_status_running
            else R.string.miband_workout_status_idle
        )
        actionButton?.setText(
            if (isTracking) R.string.miband_workout_stop_activity
            else R.string.miband_workout_add_activity
        )
        metricsContainer?.isVisible = isTracking
        if (!isTracking) {
            renderMetrics()
        }
    }

    private fun resetSession() {
        sessionStartedAt = System.currentTimeMillis()
        sessionSteps = 0
        currentHeartRate = ActivitySample.NOT_MEASURED
        sessionSamples.clear()
        renderMetrics()
    }

    private fun handleRealtimeSample(serializedSample: Serializable?) {
        var sampleTimestamp = System.currentTimeMillis()

        when (serializedSample) {
            is ActivitySample -> {
                sampleTimestamp = serializedSample.timestamp.toLong() * 1000L
                if (serializedSample.steps > 0) {
                    sessionSteps += serializedSample.steps
                }
                if (serializedSample.heartRate > 0) {
                    currentHeartRate = serializedSample.heartRate
                }
            }
            is HeartRateSample -> {
                sampleTimestamp = serializedSample.timestamp
                if (serializedSample.heartRate > 0) {
                    currentHeartRate = serializedSample.heartRate
                }
            }
            else -> return
        }

        appendSessionSample(sampleTimestamp)
        renderMetrics()
    }

    private fun appendSessionSample(timestamp: Long) {
        if (sessionStartedAt == 0L || sessionSamples.isEmpty() && timestamp < sessionStartedAt) {
            sessionStartedAt = timestamp
        }

        if (sessionSteps == 0 && currentHeartRate <= 0) {
            return
        }

        val sample = MiBandWorkoutSampleRecord(
            timestamp = timestamp,
            steps = sessionSteps,
            heartRate = currentHeartRate
        )

        val lastSample = sessionSamples.lastOrNull()
        if (lastSample != null && kotlin.math.abs(lastSample.timestamp - timestamp) < 1_000L) {
            sessionSamples[sessionSamples.lastIndex] = sample
        } else {
            sessionSamples.add(sample)
        }
    }

    private fun saveCompletedWorkout() {
        if (sessionSamples.isEmpty()) {
            return
        }

        val maxHeartRate = sessionSamples.maxOfOrNull { if (it.heartRate > 0) it.heartRate else 0 } ?: 0
        val workout = MiBandWorkoutRecord(
            id = sessionStartedAt,
            startedAt = sessionStartedAt,
            endedAt = sessionSamples.last().timestamp,
            totalSteps = sessionSteps,
            maxHeartRate = maxHeartRate,
            samples = sessionSamples.toList()
        )

        MiBandWorkoutHistoryStore.save(gbDevice, workout)
    }

    private fun renderMetrics() {
        val context = context ?: return
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

    private fun pulse() {
        // Re-send realtime HR enable periodically to keep measurement active on Mi Band 1S.
        GBApplication.deviceService(gbDevice).onEnableRealtimeHeartRateMeasurement(true)
        activity?.runOnUiThread {
            renderMetrics()
        }
    }

    private fun startActivityPulse(intervalMs: Int): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({ pulse() }, 0, intervalMs.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private fun stopActivityPulse() {
        pulseScheduler?.shutdownNow()
        pulseScheduler = null
    }

    private fun enableRealtimeTracking(enable: Boolean) {
        if (enable && pulseScheduler != null) {
            return
        }

        try {
            GBApplication.deviceService(gbDevice).onEnableRealtimeSteps(enable)
            GBApplication.deviceService(gbDevice).onEnableRealtimeHeartRateMeasurement(enable)
        } catch (_: IllegalStateException) {
            GBApplication.deviceService().onEnableRealtimeSteps(enable)
            GBApplication.deviceService().onEnableRealtimeHeartRateMeasurement(enable)
        }

        if (enable) {
            pulseScheduler = startActivityPulse(gbDevice.deviceCoordinator.liveActivityFragmentPulseInterval)
        } else {
            stopActivityPulse()
        }
    }

    private fun startRealtimeTracking() {
        enableRealtimeTracking(true)
    }

    private fun stopRealtimeTracking() {
        enableRealtimeTracking(false)
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
