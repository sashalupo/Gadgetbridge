package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

class JournalFragment : Fragment(R.layout.fragment_nested_tabs) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout = view.findViewById<TabLayout>(R.id.nested_tab_layout)
        val viewPager = view.findViewById<ViewPager2>(R.id.nested_view_pager)

        // Find the first device that supports workouts
        val device = GBApplication.app().getDeviceManager().getDevices().firstOrNull()

        if (device != null) {
            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = 1
                override fun createFragment(position: Int): Fragment {
                    return MiBandWorkoutHistoryFragment.newInstance(device)
                }
            }

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = getString(R.string.miband_workout_tab_history)
            }.attach()
        }
    }
}
