package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

class MiBandWorkoutHistoryFragment : Fragment(R.layout.fragment_miband_workout_history) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gbDevice = arguments?.getParcelable<GBDevice>(GBDevice.EXTRA_DEVICE) ?: return
        view.findViewById<Button>(R.id.workout_open_history_button).setOnClickListener {
            val intent = Intent(requireContext(), WorkoutListActivity::class.java)
            intent.putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
            startActivity(intent)
        }
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
}
