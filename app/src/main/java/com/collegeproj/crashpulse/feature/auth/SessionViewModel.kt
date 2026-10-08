package com.collegeproj.crashpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging
import com.collegeproj.crashpulse.core.model.TermsResponse
import com.collegeproj.crashpulse.core.model.UserResponse
import com.collegeproj.crashpulse.core.network.CrashPulseRepository
import com.collegeproj.crashpulse.core.storage.SessionStore

sealed interface AuthState {
    data object Loading : AuthState
    data class SignedOut(val error: String? = null) : AuthState
    data class TermsRequired(val terms: TermsResponse, val error: String? = null) : AuthState
    data class SignedIn(val user: UserResponse) : AuthState
}

class SessionViewModel(private val repository: CrashPulseRepository, private val session: SessionStore) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init { restore() }

    fun restore() {
        if (!session.hasSession) { _state.value = AuthState.SignedOut(); return }
        viewModelScope.launch {
            repository.terms().fold({ terms ->
                if (terms.is_accepted) repository.me().fold({ _state.value = AuthState.SignedIn(it); syncMessagingToken() }, { signOut() })
                else _state.value = AuthState.TermsRequired(terms)
            }, { _state.value = AuthState.SignedOut("Your saved session could not be restored. Please sign in again.") })
        }
    }

    fun login(loginId: String, password: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            repository.login(loginId, password).fold({ user ->
                repository.terms().fold({ terms ->
                    if (terms.is_accepted) { _state.value = AuthState.SignedIn(user); syncMessagingToken() } else _state.value = AuthState.TermsRequired(terms)
                }, { _state.value = AuthState.SignedOut(it.message ?: "Terms could not be loaded") })
            }, { _state.value = AuthState.SignedOut(it.message ?: "Sign in failed. Check the backend connection.") })
        }
    }

    fun acceptTerms() {
        val current = _state.value
        if (current !is AuthState.TermsRequired) return
        viewModelScope.launch {
            repository.acceptTerms(current.terms).fold({ repository.me().fold({ _state.value = AuthState.SignedIn(it); syncMessagingToken() }, { signOut() }) }, { _state.value = current.copy(error = it.message ?: "Terms could not be accepted") })
        }
    }

    fun signOut() { viewModelScope.launch { repository.logout(); _state.value = AuthState.SignedOut() } }

    /**
     * Backend base URL accessors. The URL must be readable and writable before
     * authentication, because a freshly installed app has no saved host and the
     * default points at the emulator loopback. The Retrofit instance is rebuilt
     * per call from preferences, so a saved URL applies to the next request
     * without restarting the app.
     */
    fun baseUrl(): String = repository.baseUrl()

    fun setBaseUrl(value: String) { repository.setBaseUrl(value) }

    private fun syncMessagingToken() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.registerFcmToken(FirebaseMessaging.getInstance().token.await()) }
        }
    }
}
