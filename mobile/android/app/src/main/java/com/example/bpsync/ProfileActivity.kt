package com.example.bpsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.ActivityProfileBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Profil ekranı. Veriler backend'den (getProfile) çekilir.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val api get() = RetrofitClient.api

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogout.setOnClickListener {
            AuthTokenProvider.clearTokenAndPersist(this)
            startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
            finish()
        }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val res = api.getProfile()
                if (res.isSuccessful) {
                    val body = res.body()
                    val user = body?.user
                    if (user != null) {
                        binding.tvProfileName.text = user.name.ifBlank { "Kullanıcı" }
                        binding.tvProfileEmail.text = user.email
                        val extra = listOfNotNull(
                            user.dateOfBirth?.let { "Doğum: $it" },
                            user.bloodType?.let { "Kan grubu: $it" },
                            user.emergencyContact?.let { "Acil: $it" }
                        )
                        if (extra.isNotEmpty()) {
                            binding.tvProfileExtra.visibility = View.VISIBLE
                            binding.tvProfileExtra.text = extra.joinToString("\n")
                        }
                    } else {
                        binding.tvProfileName.text = "Profil yüklenemedi"
                        binding.tvProfileEmail.text = body?.message ?: ""
                    }
                } else {
                    binding.tvProfileName.text = "Hata: ${res.code()}"
                    binding.tvProfileEmail.text = res.errorBody()?.string() ?: ""
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Profil: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvProfileName.text = "Hata"
                binding.tvProfileEmail.text = e.message
            }
        }
    }
}
