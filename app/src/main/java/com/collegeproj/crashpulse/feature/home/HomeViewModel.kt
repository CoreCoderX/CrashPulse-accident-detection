package com.collegeproj.crashpulse.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.collegeproj.crashpulse.core.model.IncidentResponse
import com.collegeproj.crashpulse.core.model.JobResponse
import com.collegeproj.crashpulse.core.network.CrashPulseRepository

data class HomeState(val loading: Boolean = false, val incidents: List<IncidentResponse> = emptyList(), val job: JobResponse? = null, val message: String? = null)

class HomeViewModel(private val repository: CrashPulseRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch { repository.incidents().fold({ _state.value = _state.value.copy(incidents = it, message = null) }, { _state.value = _state.value.copy(message = it.message ?: "Could not load incidents") }) }
    }

    fun upload(uri: Uri, context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            repository.upload(uri, context).fold({ job ->
                _state.value = _state.value.copy(loading = false, job = job)
                poll(job.id)
            }, { _state.value = _state.value.copy(loading = false, message = it.message ?: "Upload failed") })
        }
    }

    private suspend fun poll(id: UUID) {
        repeat(450) {
            delay(2000)
            repository.job(id).fold({ job ->
                _state.value = _state.value.copy(job = job)
                if (job.status in setOf("SUCCEEDED", "FAILED", "CANCELLED")) { load(); return }
            }, { _state.value = _state.value.copy(message = it.message ?: "Job status unavailable"); return })
        }
        _state.value = _state.value.copy(message = "Analysis is still running. Check History later.")
    }
}
