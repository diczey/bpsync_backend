package com.example.bpsync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.FragmentHealthStatusBinding

class HealthStatusFragment : Fragment() {

    private var _binding: FragmentHealthStatusBinding? = null
    private val binding get() = _binding!!

    // Starting from 2 days, 23 h, 45 min, 30 sec (in total seconds)
    private var totalSeconds = (2 * 24 * 3600) + (23 * 3600) + (45 * 60) + 30

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || _binding == null) return
            if (totalSeconds > 0) {
                totalSeconds--
                updateCountdownDisplay()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        updateCountdownDisplay()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun updateCountdownDisplay() {
        val d = totalSeconds / 86400
        val h = (totalSeconds % 86400) / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60

        binding.tvDays.text = d.toString().padStart(2, '0')
        binding.tvHours.text = h.toString().padStart(2, '0')
        binding.tvMins.text = m.toString().padStart(2, '0')
        binding.tvSecs.text = s.toString().padStart(2, '0')
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroyView()
        _binding = null
    }
}
