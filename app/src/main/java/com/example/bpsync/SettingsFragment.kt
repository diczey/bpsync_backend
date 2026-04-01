package com.example.bpsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        setupLanguageSpinner()

        // Notification toggles — state is reflected visually by SwitchMaterial
        binding.switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Push notifications enabled" else "Push notifications disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.switchWeeklyReports.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Weekly reports enabled" else "Weekly reports disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Link rows
        binding.btnPrivacyPolicy.setOnClickListener {
            Toast.makeText(requireContext(), "Privacy Policy", Toast.LENGTH_SHORT).show()
        }

        binding.btnTerms.setOnClickListener {
            Toast.makeText(requireContext(), "Terms of Service", Toast.LENGTH_SHORT).show()
        }

        binding.btnHelpSupport.setOnClickListener {
            showAboutDialog()
        }

        // Compat — kept for backward compatibility
        binding.themeLightBtn.setOnClickListener { }
        binding.themeDarkBtn.setOnClickListener { }
        binding.btnManageDevices.setOnClickListener {
            (activity as? HomeActivity)?.showFragment(BLEFragment(), "BLE Connection")
        }
        binding.btnChangePassword.setOnClickListener { }
        binding.btnDataPrivacy.setOnClickListener { }
    }

    private fun setupLanguageSpinner() {
        val languages = listOf("English", "Türkçe")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("About BP Sync")
            .setMessage("BP Sync Version 1.0.0\n\nA smart blood pressure monitoring app that connects to your BLE device and tracks your health metrics.\n\n© 2026 BP Sync. All rights reserved.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
