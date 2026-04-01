package com.example.bpsync

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.ActivityHomeBinding
import com.example.bpsync.network.AuthTokenProvider

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var currentNavItemId: Int = R.id.navItemDashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar is hidden; each fragment has its own header.
        // Keep setSupportActionBar for compatibility but toolbar is GONE in XML.
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Handle back press: close drawer first if open, otherwise default behaviour
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Update drawer header with user info
        updateDrawerHeader()

        // Close drawer button
        binding.navDrawer.findViewById<ImageView>(R.id.btnCloseDrawer)?.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Nav item listeners
        setupNavItems()

        // Load initial fragment
        if (savedInstanceState == null) {
            navigateTo(DashboardFragment(), "Dashboard", R.id.navItemDashboard)
        }
    }

    private fun updateDrawerHeader() {
        val userName = AuthTokenProvider.savedUserName ?: "User"
        val userEmail = AuthTokenProvider.savedUserEmail ?: ""
        binding.navDrawer.findViewById<TextView>(R.id.tvNavUserName)?.text = userName
        binding.navDrawer.findViewById<TextView>(R.id.tvNavUserEmail)?.text = userEmail
    }

    private fun setupNavItems() {
        binding.navItemDashboard.setOnClickListener {
            navigateTo(DashboardFragment(), "Dashboard", R.id.navItemDashboard)
            closeDrawer()
        }
        binding.navItemHealthStatus.setOnClickListener {
            navigateTo(HealthStatusFragment(), "Health Status", R.id.navItemHealthStatus)
            closeDrawer()
        }
        binding.navItemTrends.setOnClickListener {
            navigateTo(TrendsFragment(), "Trends & Analytics", R.id.navItemTrends)
            closeDrawer()
        }
        binding.navItemBloodPressure.setOnClickListener {
            navigateTo(BloodPressureFragment(), "Blood Pressure", R.id.navItemBloodPressure)
            closeDrawer()
        }
        binding.navItemPulse.setOnClickListener {
            navigateTo(PulseRateFragment(), "Pulse Rate", R.id.navItemPulse)
            closeDrawer()
        }
        binding.navItemPpg.setOnClickListener {
            navigateTo(PPGFragment(), "PPG Signal", R.id.navItemPpg)
            closeDrawer()
        }
        binding.navItemMeasurements.setOnClickListener {
            navigateTo(ReadingsFragment(), "Measurements", R.id.navItemMeasurements)
            closeDrawer()
        }
        binding.navItemProfile.setOnClickListener {
            navigateTo(ProfileFragment(), "Profile", R.id.navItemProfile)
            closeDrawer()
        }
        binding.navItemBle.setOnClickListener {
            navigateTo(BLEFragment(), "BLE Connection", R.id.navItemBle)
            closeDrawer()
        }
        binding.navItemSettings.setOnClickListener {
            navigateTo(SettingsFragment(), "Settings", R.id.navItemSettings)
            closeDrawer()
        }
        binding.navItemLogout.setOnClickListener {
            closeDrawer()
            doLogout()
        }
    }

    private fun navigateTo(fragment: Fragment, title: String, navItemId: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        binding.tvToolbarTitle.text = title
        updateNavSelection(navItemId)
        currentNavItemId = navItemId
    }

    private fun updateNavSelection(selectedId: Int) {
        val navItems = listOf(
            binding.navItemDashboard,
            binding.navItemHealthStatus,
            binding.navItemTrends,
            binding.navItemBloodPressure,
            binding.navItemPulse,
            binding.navItemPpg,
            binding.navItemMeasurements,
            binding.navItemProfile,
            binding.navItemBle,
            binding.navItemSettings
        )
        val whiteColor = getColor(android.R.color.white)
        val defaultColor = getColor(R.color.drawer_item_text)

        navItems.forEach { item ->
            val isSelected = item.id == selectedId
            item.background = if (isSelected)
                getDrawable(R.drawable.bg_nav_item_selected)
            else
                null

            val icon = item.getChildAt(0) as? ImageView
            val label = item.getChildAt(1) as? TextView

            icon?.setColorFilter(if (isSelected) whiteColor else getColor(R.color.text_secondary))
            label?.setTextColor(if (isSelected) whiteColor else defaultColor)
            label?.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun doLogout() {
        AuthTokenProvider.clearTokenAndPersist(this)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    /** Called from fragments to navigate back to dashboard */
    fun showDashboard() {
        navigateTo(DashboardFragment(), "Dashboard", R.id.navItemDashboard)
    }

    fun showFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        binding.tvToolbarTitle.text = title
    }
}
