package com.example.bpsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.FragmentDashboardBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnMenu.setOnClickListener {
            (activity as? HomeActivity)?.openDrawer()
        }

        binding.btnManageBle.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(BLEFragment(), "BLE Connection")
        }
        binding.tvBpDetails.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(BloodPressureFragment(), "Blood Pressure")
        }
        binding.cardBloodPressure.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(ReadingsFragment(), "Measurements")
        }
        binding.cardPulse.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(PulseRateFragment(), "Pulse Rate")
        }
        binding.cardPpg.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(PPGFragment(), "PPG Signal")
        }
        binding.cardTrends.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(TrendsFragment(), "Trends & Analytics")
        }
        binding.cardHealthStatus.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(HealthStatusFragment(), "Health Status")
        }
        binding.cardBleDevice.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(BLEFragment(), "BLE Connection")
        }

        binding.btnViewMeasurements.setOnClickListener {
            loadDashboard()
        }

        loadDashboard()
    }

    private fun loadDashboard() {
        if (AuthTokenProvider.token == "demo") {
            showDemoData()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getDashboardSummary()
                if (res.isSuccessful && res.body()?.success == true) {
                    val s = res.body()!!.summary
                    if (s != null) {
                        val sys = s.latestSystolic?.toString() ?: "--"
                        val dia = s.latestDiastolic?.toString() ?: "--"
                        val hr  = s.latestHeartRate?.toString() ?: "--"

                        binding.tvLatestSystolic.text  = sys
                        binding.tvLatestDiastolic.text = dia
                        binding.tvLatestHeartRate.text = "$hr bpm"
                        binding.tvHeartRate.text        = "$hr bpm"
                        binding.tvBloodPressure.text    = "View all data"
                        binding.tvSpO2.text             = "98%"

                        val status = s.healthStatus ?: s.category ?: "Normal"
                        binding.tvHealthStatus.text = status

                        if (!s.lastUpdated.isNullOrBlank()) {
                            binding.tvLastUpdated.text = s.lastUpdated
                        }
                    } else {
                        showDemoData()
                    }
                } else {
                    showDemoData()
                }
            } catch (_: Exception) {
                showDemoData()
            }
        }
    }

    private fun showDemoData() {
        binding.tvLatestSystolic.text  = "120"
        binding.tvLatestDiastolic.text = "80"
        binding.tvLatestHeartRate.text = "72 bpm"
        binding.tvHeartRate.text        = "72 bpm"
        binding.tvBloodPressure.text    = "View all data"
        binding.tvSpO2.text             = "98%"
        binding.tvHealthStatus.text     = "Normal"
        binding.tvLastUpdated.text      = "2 hours ago"
        binding.tvBleStatus.text        = "Connected"
        binding.tvBleBattery.text       = "85%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
