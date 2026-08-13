package com.example.sls_assistant_pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sls_assistant_pro.data.local.SavedAccountEntity
import com.example.sls_assistant_pro.data.repository.SlsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Authenticated : AuthState
    data class Unauthenticated(val error: String? = null) : AuthState
}

class AuthViewModel(private val repository: SlsRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<SavedAccountEntity>>(emptyList())
    val savedAccounts: StateFlow<List<SavedAccountEntity>> = _savedAccounts.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    init {
        checkSession()
        observeSavedAccounts()
    }

    fun checkSession() {
        if (repository.hasSavedSession()) {
            _authState.value = AuthState.Authenticated
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }

    private fun observeSavedAccounts() {
        viewModelScope.launch {
            repository.savedAccounts.collectLatest { list ->
                _savedAccounts.value = list
            }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Unauthenticated("يرجى إدخال البريد والكلمة المرورية.")
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                repository.login(email.trim(), pass.trim())
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated(e.message ?: "فشل تسجيل الدخول.")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Unauthenticated()
    }

    fun removeSavedAccount(email: String) {
        viewModelScope.launch {
            repository.removeSavedAccount(email)
        }
    }
}
