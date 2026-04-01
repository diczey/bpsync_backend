package com.example.bpsync

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.ActivityMainBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val api get() = RetrofitClient.api

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenProvider.loadFromPrefs(this)
        if (AuthTokenProvider.hasToken()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGenderSpinner()

        // Login screen actions
        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnDemo.setOnClickListener { doDemoLogin() }

        binding.tvGoRegister.setOnClickListener {
            binding.viewLogin.visibility = View.GONE
            binding.viewRegister.visibility = View.VISIBLE
        }

        // Register screen actions
        binding.btnRegister.setOnClickListener { doRegister() }

        binding.tvGoLogin.setOnClickListener {
            binding.viewRegister.visibility = View.GONE
            binding.viewLogin.visibility = View.VISIBLE
        }

        // Date of Birth picker
        binding.layoutRegBirthDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val formatted = "%02d/%02d/%04d".format(day, month + 1, year)
                binding.tvRegBirthDate.text = formatted
                binding.tvRegBirthDate.setTextColor(getColor(R.color.text_primary))
                // Store age derived from birth year for compat
                val age = cal.get(Calendar.YEAR) - year
                binding.etRegAge.setText(age.toString())
            },
            cal.get(Calendar.YEAR) - 25,
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    private fun setupGenderSpinner() {
        val genders = arrayOf("Select", "Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genders)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGender.adapter = adapter
    }

    private fun doDemoLogin() {
        AuthTokenProvider.setTokenAndPersist(this, "demo")
        AuthTokenProvider.saveUserAndPersist(this, null)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.setTextColor(getColor(R.color.text_secondary))
        binding.tvStatus.text = "Signing in…"

        lifecycleScope.launch {
            try {
                val res = api.login(com.example.bpsync.network.LoginRequest(email, password))
                if (res.isSuccessful) {
                    val body = res.body()
                    val token = body?.token
                    if (!token.isNullOrBlank()) {
                        AuthTokenProvider.setTokenAndPersist(this@MainActivity, token)
                        AuthTokenProvider.saveUserAndPersist(this@MainActivity, body.user)
                        startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        binding.tvStatus.setTextColor(getColor(R.color.error))
                        binding.tvStatus.text = body?.message ?: "Login failed"
                    }
                } else {
                    val code = res.code()
                    binding.tvStatus.setTextColor(getColor(R.color.error))
                    binding.tvStatus.text = when {
                        code == 422 -> "Invalid email or password format"
                        code in 400..499 -> "Incorrect email or password"
                        code >= 500 -> "Server error. Please try again later."
                        else -> "Login error (HTTP $code)"
                    }
                }
            } catch (e: Exception) {
                binding.tvStatus.setTextColor(getColor(R.color.error))
                binding.tvStatus.text = when {
                    e.message?.contains("Failed to connect") == true ||
                    e.message?.contains("Connection refused") == true ->
                        "Cannot connect to server. Is the backend running?"
                    e.message?.contains("timeout") == true -> "Connection timeout."
                    else -> "Error: ${e.message}"
                }
            }
        }
    }

    private fun doRegister() {
        val name = binding.etRegName.text.toString().trim()
        val email = binding.etRegEmail.text.toString().trim()
        val password = binding.etRegPassword.text.toString()

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvRegStatus.visibility = View.VISIBLE
        binding.tvRegStatus.setTextColor(getColor(R.color.text_secondary))
        binding.tvRegStatus.text = "Creating account…"

        lifecycleScope.launch {
            try {
                val res = api.register(com.example.bpsync.network.RegisterRequest(email, password, name))
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null && body.success && body.token != null) {
                        AuthTokenProvider.setTokenAndPersist(this@MainActivity, body.token)
                        AuthTokenProvider.saveUserAndPersist(this@MainActivity, body.user)
                        Toast.makeText(this@MainActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        binding.tvRegStatus.text = body?.message ?: "Registration failed"
                    }
                } else {
                    binding.tvRegStatus.text = "Registration error: ${res.errorBody()?.string()?.take(100) ?: "HTTP ${res.code()}"}"
                }
            } catch (e: Exception) {
                binding.tvRegStatus.text = "Connection error: ${e.message}"
            }
        }
    }
}
