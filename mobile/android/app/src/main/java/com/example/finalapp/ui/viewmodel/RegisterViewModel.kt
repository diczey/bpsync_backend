package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.RegisterRequest
import com.example.finalapp.data.repository.AuthRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.SettingsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val weight: String = "",
    val height: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private fun t(english: String, turkish: String): String {
        return if (SettingsStore.settings.value.language == "tr") turkish else english
    }

    fun updateName(name: String) = updateField { it.copy(name = name, errorMessage = null) }
    fun updateEmail(email: String) = updateField { it.copy(email = email, errorMessage = null) }
    fun updatePassword(password: String) = updateField { it.copy(password = password, errorMessage = null) }
    fun updateWeight(weight: String) = updateField { it.copy(weight = weight, errorMessage = null) }
    fun updateHeight(height: String) = updateField { it.copy(height = height, errorMessage = null) }
    fun updateGender(gender: String) = updateField { it.copy(gender = gender, errorMessage = null) }
    fun updateDateOfBirth(dateOfBirth: String) = updateField { it.copy(dateOfBirth = dateOfBirth, errorMessage = null) }

    fun register() {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        if (currentState.name.isBlank() || currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = t("Name, email and password are required.", "Ad, e-posta ve şifre zorunludur."))
            }
            return
        }

        val normalizedGender = normalizeGender(currentState.gender)
        if (currentState.gender.isNotBlank() && normalizedGender == null) {
            _uiState.update {
                it.copy(errorMessage = t("Gender must be Male, Female, or Other.", "Cinsiyet Erkek, Kadın veya Diğer olmalıdır."))
            }
            return
        }

        val normalizedDateOfBirth = normalizeDateOfBirth(currentState.dateOfBirth)
        if (currentState.dateOfBirth.isNotBlank() && normalizedDateOfBirth == null) {
            _uiState.update {
                it.copy(errorMessage = t("Date of birth must be a valid date.", "Doğum tarihi geçerli bir tarih olmalıdır."))
            }
            return
        }

        val normalizedWeight = currentState.weight.trim().takeIf { it.isNotBlank() }
        if (normalizedWeight != null && normalizedWeight.toFloatOrNull() == null) {
            _uiState.update {
                it.copy(errorMessage = t("Weight must be a number.", "Kilo sayısal bir değer olmalıdır."))
            }
            return
        }

        val normalizedHeight = currentState.height.trim().takeIf { it.isNotBlank() }
        if (normalizedHeight != null && normalizedHeight.toIntOrNull() == null) {
            _uiState.update {
                it.copy(errorMessage = t("Height must be a whole number.", "Boy tam sayı olmalıdır."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = RegisterRequest(
                email = currentState.email.trim(),
                password = currentState.password,
                name = currentState.name.trim(),
                dateOfBirth = normalizedDateOfBirth,
                gender = normalizedGender,
                weight = normalizedWeight,
                height = normalizedHeight
            )

            when (val result = authRepository.register(request)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(isRegistered = false) }
    }

    private fun updateField(update: (RegisterUiState) -> RegisterUiState) {
        _uiState.update(update)
    }

    private fun normalizeGender(gender: String): String? {
        return when (gender.trim().lowercase(Locale.ROOT)) {
            "" -> null
            "male" -> "Male"
            "female" -> "Female"
            "other" -> "Other"
            "erkek" -> "Male"
            "kadın", "kadin" -> "Female"
            "diğer", "diger" -> "Other"
            else -> null
        }
    }

    private fun normalizeDateOfBirth(dateOfBirth: String): String? {
        val value = dateOfBirth.trim()
        if (value.isBlank()) return null

        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )

        val parsed = formatters.firstNotNullOfOrNull { formatter ->
            try {
                LocalDate.parse(value, formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: return null

        return parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
