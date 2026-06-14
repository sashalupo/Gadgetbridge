package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.json.JSONArray
import org.json.JSONObject

data class MiBandWorkoutSampleRecord(
    val timestamp: Long,
    val steps: Int,
    val heartRate: Int
)

data class MiBandWorkoutRecord(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val totalSteps: Int,
    val maxHeartRate: Int,
    val samples: List<MiBandWorkoutSampleRecord>
)

object MiBandWorkoutHistoryStore {
    private const val PREF_KEY = "miband_workout_history_v1"

    fun load(device: GBDevice): List<MiBandWorkoutRecord> {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address) ?: return emptyList()
        val raw = prefs.getString(PREF_KEY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(index)))
                }
            }.sortedByDescending { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(device: GBDevice, workout: MiBandWorkoutRecord) {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address) ?: return
        val workouts = load(device).toMutableList()
        workouts.removeAll { it.id == workout.id }
        workouts.add(0, workout)

        val array = JSONArray()
        workouts.forEach { array.put(toJson(it)) }

        prefs.edit().putString(PREF_KEY, array.toString()).apply()
    }

    private fun toJson(workout: MiBandWorkoutRecord): JSONObject {
        val samplesArray = JSONArray()
        workout.samples.forEach { sample ->
            samplesArray.put(
                JSONObject()
                    .put("timestamp", sample.timestamp)
                    .put("steps", sample.steps)
                    .put("heartRate", sample.heartRate)
            )
        }

        return JSONObject()
            .put("id", workout.id)
            .put("startedAt", workout.startedAt)
            .put("endedAt", workout.endedAt)
            .put("totalSteps", workout.totalSteps)
            .put("maxHeartRate", workout.maxHeartRate)
            .put("samples", samplesArray)
    }

    private fun fromJson(json: JSONObject): MiBandWorkoutRecord {
        val samplesJson = json.optJSONArray("samples") ?: JSONArray()
        val samples = buildList(samplesJson.length()) {
            for (index in 0 until samplesJson.length()) {
                val sampleJson = samplesJson.getJSONObject(index)
                add(
                    MiBandWorkoutSampleRecord(
                        timestamp = sampleJson.optLong("timestamp"),
                        steps = sampleJson.optInt("steps"),
                        heartRate = sampleJson.optInt("heartRate")
                    )
                )
            }
        }

        return MiBandWorkoutRecord(
            id = json.optLong("id"),
            startedAt = json.optLong("startedAt"),
            endedAt = json.optLong("endedAt"),
            totalSteps = json.optInt("totalSteps"),
            maxHeartRate = json.optInt("maxHeartRate"),
            samples = samples
        )
    }
}