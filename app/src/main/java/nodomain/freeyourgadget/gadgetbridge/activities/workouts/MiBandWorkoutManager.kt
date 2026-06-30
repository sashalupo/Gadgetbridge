package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample
import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object MiBandWorkoutManager {
    var isTracking = false
        private set
    var sessionStartedAt = 0L
        private set
    var sessionSteps = 0
        private set
    var currentHeartRate = ActivitySample.NOT_MEASURED
        private set
    var sessionType = MiBandWorkoutType.WALKING
        private set

    private val sessionSamples = mutableListOf<MiBandWorkoutSampleRecord>()
    private var gbDevice: GBDevice? = null

    private var pulseScheduler: ScheduledExecutorService? = null

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

    fun startWorkout(device: GBDevice, type: MiBandWorkoutType) {
        if (isTracking) return
        gbDevice = device
        isTracking = true
        sessionType = type
        sessionStartedAt = System.currentTimeMillis()
        sessionSteps = 0
        currentHeartRate = ActivitySample.NOT_MEASURED
        sessionSamples.clear()

        val context = GBApplication.getContext()
        LocalBroadcastManager.getInstance(context).registerReceiver(
            realtimeSampleReceiver,
            IntentFilter(DeviceService.ACTION_REALTIME_SAMPLES)
        )

        enableRealtimeTracking(true)
        updateNotification()
    }

    fun stopWorkout() {
        if (!isTracking) return
        saveCompletedWorkout()
        isTracking = false
        enableRealtimeTracking(false)

        val context = GBApplication.getContext()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(realtimeSampleReceiver)
        NotificationManagerCompat.from(context).cancel(GB.NOTIFICATION_ID_WORKOUT)
        gbDevice = null
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
        updateNotification()
    }

    private fun updateNotification() {
        val device = gbDevice ?: return
        if (!isTracking) return
        val context = GBApplication.getContext()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val duration = (System.currentTimeMillis() - sessionStartedAt) / 1000
        val text = context.getString(
            R.string.miband_workout_history_item_details,
            sessionSteps,
            currentHeartRate,
            DateUtils.formatElapsedTime(duration)
        )
        val notification = GB.createWorkoutNotification(
            "${context.getString(R.string.miband_workout_status_running)}: ${context.getString(sessionType.nameRes)}",
            text,
            device,
            context
        )
        NotificationManagerCompat.from(context).notify(GB.NOTIFICATION_ID_WORKOUT, notification)
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
        val device = gbDevice ?: return
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
            type = sessionType,
            samples = sessionSamples.toList()
        )

        MiBandWorkoutHistoryStore.save(device, workout)
    }

    private fun enableRealtimeTracking(enable: Boolean) {
        val device = gbDevice ?: return
        if (enable && pulseScheduler != null) {
            return
        }

        try {
            GBApplication.deviceService(device).onEnableRealtimeSteps(enable)
            GBApplication.deviceService(device).onEnableRealtimeHeartRateMeasurement(enable)
        } catch (_: IllegalStateException) {
            GBApplication.deviceService().onEnableRealtimeSteps(enable)
            GBApplication.deviceService().onEnableRealtimeHeartRateMeasurement(enable)
        }

        if (enable) {
            pulseScheduler = Executors.newSingleThreadScheduledExecutor().apply {
                scheduleWithFixedDelay({
                    GBApplication.deviceService(device).onEnableRealtimeHeartRateMeasurement(true)
                    updateNotification()
                }, 0, device.deviceCoordinator.liveActivityFragmentPulseInterval.toLong(), TimeUnit.MILLISECONDS)
            }
        } else {
            pulseScheduler?.shutdownNow()
            pulseScheduler = null
        }
    }

    fun getSessionSamples(): List<MiBandWorkoutSampleRecord> = sessionSamples.toList()
    fun getGbDevice(): GBDevice? = gbDevice
}
