package com.example.bpsync

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.FragmentProfileBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.ProfileUpdateRequest
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api

    private var isEditMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGenderSpinner()

        binding.btnBack.setOnClickListener {
            if (isEditMode) {
                setEditMode(false)
            } else {
                parentFragmentManager.popBackStack()
                (activity as? HomeActivity)?.showDashboard()
            }
        }

        binding.btnEditSave.setOnClickListener {
            if (isEditMode) {
                collectAndSave()
            } else {
                setEditMode(true)
            }
        }

        // Compat — kept for backward compatibility, no-op in new UI
        binding.btnEditProfile.setOnClickListener { setEditMode(true) }
        binding.btnSaveProfile.setOnClickListener { collectAndSave() }

        binding.btnLogout.setOnClickListener { logout() }
        binding.btnDeleteAccount.setOnClickListener { showDeleteConfirm() }

        loadProfile()
    }

    private fun setupGenderSpinner() {
        val genders = listOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genders)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProfileGender.adapter = adapter
    }

    private fun setEditMode(editing: Boolean) {
        isEditMode = editing

        if (editing) {
            // Pre-fill edit fields with current display values
            binding.etProfileName.setText(binding.tvPersonalName.text)
            binding.etProfileEmail.setText(binding.tvProfileEmail.text)
            binding.etProfilePhone.setText(binding.tvProfilePhone.text)
            binding.etProfileAge.setText(binding.tvProfileAge.text)
            binding.etProfileWeight.setText(binding.tvProfileWeight.text)
            binding.etProfileHeight.setText(binding.tvProfileHeight.text)
            val genders = listOf("Male", "Female", "Other")
            val idx = genders.indexOfFirst {
                it.equals(binding.tvProfileGender.text.toString(), ignoreCase = true)
            }.coerceAtLeast(0)
            binding.spinnerProfileGender.setSelection(idx)
        }

        val viewVis = if (editing) View.GONE else View.VISIBLE
        val editVis = if (editing) View.VISIBLE else View.GONE

        // Personal Information toggles
        binding.layoutViewName.visibility = viewVis
        binding.etProfileName.visibility = editVis
        binding.layoutViewEmail.visibility = viewVis
        binding.etProfileEmail.visibility = editVis
        binding.layoutViewPhone.visibility = viewVis
        binding.etProfilePhone.visibility = editVis

        // Health Metrics toggles
        binding.tvProfileAge.visibility = viewVis
        binding.etProfileAge.visibility = editVis
        binding.tvProfileGender.visibility = viewVis
        binding.spinnerProfileGender.visibility = editVis
        binding.tvProfileWeight.visibility = viewVis
        binding.etProfileWeight.visibility = editVis
        binding.tvProfileHeight.visibility = viewVis
        binding.etProfileHeight.visibility = editVis

        // Toggle edit/save button appearance
        if (editing) {
            binding.ivEditSaveIcon.setImageResource(R.drawable.ic_check)
            binding.ivEditSaveIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.btnEditSave.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_save_green)
        } else {
            binding.ivEditSaveIcon.setImageResource(R.drawable.ic_edit)
            binding.ivEditSaveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#6366F1"))
            binding.btnEditSave.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_edit_btn_white)
        }
    }

    private fun collectAndSave() {
        val name = binding.etProfileName.text.toString().trim()
        val email = binding.etProfileEmail.text.toString().trim()
        val phone = binding.etProfilePhone.text.toString().trim()
        val age = binding.etProfileAge.text.toString().trim()
        val gender = binding.spinnerProfileGender.selectedItem?.toString() ?: "Male"
        val weight = binding.etProfileWeight.text.toString().trim()
        val height = binding.etProfileHeight.text.toString().trim()

        // Update all display views
        if (name.isNotBlank()) {
            binding.tvProfileName.text = name
            binding.tvPersonalName.text = name
        }
        if (email.isNotBlank()) binding.tvProfileEmail.text = email
        if (phone.isNotBlank()) binding.tvProfilePhone.text = phone
        if (age.isNotBlank()) binding.tvProfileAge.text = age
        binding.tvProfileGender.text = gender
        if (weight.isNotBlank()) binding.tvProfileWeight.text = weight
        if (height.isNotBlank()) binding.tvProfileHeight.text = height

        // Update BMI (hidden compat view)
        val weightF = weight.toFloatOrNull()
        val heightF = height.toFloatOrNull()
        if (weightF != null && heightF != null && heightF > 0) {
            val bmi = weightF / ((heightF / 100f) * (heightF / 100f))
            binding.tvProfileBmi.text = String.format("%.1f", bmi)
        }

        // Persist to API if not demo
        if (AuthTokenProvider.token != "demo" && name.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val res = api.updateProfile(ProfileUpdateRequest(name = name))
                    if (res.isSuccessful) {
                        res.body()?.user?.let {
                            AuthTokenProvider.saveUserAndPersist(requireContext(), it)
                        }
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setEditMode(false)
    }

    private fun loadProfile() {
        val name = AuthTokenProvider.savedUserName ?: "User"
        val email = AuthTokenProvider.savedUserEmail ?: ""

        binding.tvProfileName.text = name
        binding.tvPersonalName.text = name
        binding.tvProfileEmail.text = email

        if (AuthTokenProvider.token == "demo") {
            binding.tvProfileName.text = "John Doe"
            binding.tvPersonalName.text = "John Doe"
            binding.tvProfileEmail.text = "john@example.com"
            binding.tvProfilePhone.text = "+1 234 567 8900"
            binding.tvProfileAge.text = "28"
            binding.tvProfileGender.text = "Male"
            binding.tvProfileWeight.text = "75"
            binding.tvProfileHeight.text = "178"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getProfile()
                if (res.isSuccessful) {
                    val user = res.body()?.user
                    user?.let { u ->
                        val displayName = u.name.ifBlank { "User" }
                        binding.tvProfileName.text = displayName
                        binding.tvPersonalName.text = displayName
                        binding.tvProfileEmail.text = u.email
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showDeleteConfirm() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Your account will be permanently deleted. Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        api.deleteProfile()
                        logout()
                        Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Delete error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (AuthTokenProvider.hasToken() && AuthTokenProvider.token != "demo") {
                    api.logout()
                }
            } catch (_: Exception) { }
            AuthTokenProvider.clearTokenAndPersist(requireContext())
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
