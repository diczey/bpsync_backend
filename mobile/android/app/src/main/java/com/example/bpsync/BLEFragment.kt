package com.example.bpsync

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.FragmentBleBinding

class BLEFragment : Fragment() {

    private var _binding: FragmentBleBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private enum class BleState { IDLE, SCANNING, DEVICES_FOUND, CONNECTING, CONNECTED }
    private var state = BleState.IDLE
    private var connectedDeviceName = "BP Monitor Pro"
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateUi(BleState.IDLE)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        binding.btnScan.setOnClickListener {
            if (state == BleState.IDLE || state == BleState.DEVICES_FOUND) {
                startScanning()
            }
        }

        binding.btnGoToDashboard.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        binding.btnDisconnect.setOnClickListener {
            updateUi(BleState.IDLE)
        }

        binding.btnRescan.setOnClickListener {
            startScanning()
        }

        binding.layoutDevice1.setOnClickListener { connectToDevice("BP Monitor Pro", 1) }
        binding.layoutDevice2.setOnClickListener { connectToDevice("BP Sync Device", 2) }
        binding.layoutDevice3.setOnClickListener { connectToDevice("Health Band Plus", 3) }
    }

    private fun startScanning() {
        updateUi(BleState.SCANNING)
        handler.postDelayed({
            if (isAdded) updateUi(BleState.DEVICES_FOUND)
        }, 3000)
    }

    private fun connectToDevice(name: String, deviceIndex: Int) {
        if (state != BleState.DEVICES_FOUND) return

        connectedDeviceName = name
        updateUi(BleState.CONNECTING)

        // Show "Connecting..." label on selected device
        binding.tvConnecting1.visibility = if (deviceIndex == 1) View.VISIBLE else View.GONE
        binding.tvConnecting2.visibility = if (deviceIndex == 2) View.VISIBLE else View.GONE
        binding.tvConnecting3.visibility = if (deviceIndex == 3) View.VISIBLE else View.GONE

        // Pulse the selected device icon
        val iconView: View = when (deviceIndex) {
            1 -> binding.frmDevice1Icon
            2 -> binding.frmDevice2Icon
            else -> binding.frmDevice3Icon
        }
        iconView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ble_status_connected)
        startPulseOn(iconView)

        handler.postDelayed({
            if (isAdded) {
                stopPulse(iconView)
                iconView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_ble_blue)
                updateUi(BleState.CONNECTED)
            }
        }, 1500)
    }

    private fun updateUi(newState: BleState) {
        state = newState
        when (newState) {
            BleState.IDLE -> {
                stopPulse(binding.frmBleIcon)
                binding.frmBleIcon.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ble_status_idle)
                binding.ivBleIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_hint)
                binding.frmConnectedDot.visibility = View.GONE
                binding.tvBleStatusTitle.text = "No Device Connected"
                binding.tvBleStatusDesc.text = "Press scan to find devices"
                binding.btnScan.visibility = View.VISIBLE
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Scan for Devices"
                binding.btnGoToDashboard.visibility = View.GONE
                binding.btnDisconnect.visibility = View.GONE
                binding.cardDevices.visibility = View.GONE
                resetDeviceItems()
            }

            BleState.SCANNING -> {
                binding.frmBleIcon.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ble_status_scanning)
                binding.ivBleIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                binding.frmConnectedDot.visibility = View.GONE
                binding.tvBleStatusTitle.text = "Scanning..."
                binding.tvBleStatusDesc.text = "Looking for nearby devices..."
                binding.btnScan.isEnabled = false
                binding.btnScan.text = "Scanning..."
                binding.cardDevices.visibility = View.VISIBLE
                binding.tvDevicesTitle.text = "Searching Devices..."
                binding.btnRescan.visibility = View.GONE
                binding.layoutSkeletons.visibility = View.VISIBLE
                binding.layoutDeviceList.visibility = View.GONE
                startPulseOn(binding.frmBleIcon)
            }

            BleState.DEVICES_FOUND -> {
                stopPulse(binding.frmBleIcon)
                binding.frmBleIcon.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ble_status_idle)
                binding.ivBleIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_hint)
                binding.tvBleStatusTitle.text = "No Device Connected"
                binding.tvBleStatusDesc.text = "Press scan to find devices"
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Scan for Devices"
                binding.cardDevices.visibility = View.VISIBLE
                binding.tvDevicesTitle.text = "Available Devices"
                binding.btnRescan.visibility = View.VISIBLE
                binding.layoutSkeletons.visibility = View.GONE
                binding.layoutDeviceList.visibility = View.VISIBLE
                resetDeviceItems()
            }

            BleState.CONNECTING -> {
                // Device-level UI handled in connectToDevice()
            }

            BleState.CONNECTED -> {
                stopPulse(binding.frmBleIcon)
                binding.frmBleIcon.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ble_status_connected)
                binding.ivBleIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                binding.frmConnectedDot.visibility = View.VISIBLE
                binding.tvBleStatusTitle.text = "Device Connected"
                binding.tvBleStatusDesc.text = "$connectedDeviceName is ready"
                binding.btnScan.visibility = View.GONE
                binding.btnGoToDashboard.visibility = View.VISIBLE
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.cardDevices.visibility = View.GONE
            }
        }
    }

    private fun startPulseOn(target: View) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(target, "alpha", 1f, 0.35f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse(target: View) {
        pulseAnimator?.cancel()
        pulseAnimator = null
        target.alpha = 1f
    }

    private fun resetDeviceItems() {
        listOf(
            binding.tvConnecting1, binding.tvConnecting2, binding.tvConnecting3
        ).forEach { it.visibility = View.GONE }
        listOf(
            binding.frmDevice1Icon, binding.frmDevice2Icon, binding.frmDevice3Icon
        ).forEach {
            it.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_ble_blue)
            it.alpha = 1f
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        pulseAnimator?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
