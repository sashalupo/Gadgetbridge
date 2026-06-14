package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

class MiBandWorkoutPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val gbDevice: GBDevice
) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) {
            MiBandWorkoutSessionFragment()
        } else {
            MiBandWorkoutHistoryFragment.newInstance(gbDevice)
        }
    }
}
