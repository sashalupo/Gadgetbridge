package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MiBandWorkoutSessionFragment : Fragment(R.layout.fragment_miband_workout_session) {
    private lateinit var gbDevice: GBDevice

    private var actionButton: Button? = null
    private var statusValue: TextView? = null
    private var metricsContainer: View? = null

    private var isTracking = false
    private var pulseScheduler: ScheduledExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gbDevice = requireArguments().getParcelable(ARG_DEVICE)
            ?: throw IllegalStateException("GBDevice is required")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actionButton = view.findViewById(R.id.workout_add_button)
        statusValue = view.findViewById(R.id.workout_status_value)
        metricsContainer = view.findViewById(R.id.workout_metrics_container)

        actionButton?.setOnClickListener {
            isTracking = !isTracking

            if (isTracking) {
                startRealtimeTracking()
            } else {
                stopRealtimeTracking()
            }

            updateUiState()
        }

        updateUiState()
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
        actionButton = null
        statusValue = null
        metricsContainer = null
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
    }

    private fun pulse() {
        // Re-send realtime HR enable periodically to keep measurement active on Mi Band 1S.
        GBApplication.deviceService(gbDevice).onEnableRealtimeHeartRateMeasurement(true)
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
