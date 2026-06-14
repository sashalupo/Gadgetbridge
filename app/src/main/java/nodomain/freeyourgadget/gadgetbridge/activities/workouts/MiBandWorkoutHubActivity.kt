package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

class MiBandWorkoutHubActivity : AbstractGBActivity() {
    private lateinit var gbDevice: GBDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_miband_workout_hub)

        gbDevice = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
            ?: run {
                finish()
                return
            }

        title = getString(R.string.miband_workout_hub_title)

        val tabLayout = findViewById<TabLayout>(R.id.workout_tab_layout)
        val viewPager = findViewById<ViewPager2>(R.id.workout_view_pager)
        viewPager.adapter = MiBandWorkoutPagerAdapter(this, gbDevice)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) {
                getString(R.string.miband_workout_tab_session)
            } else {
                getString(R.string.miband_workout_tab_history)
            }
        }.attach()
    }
}
