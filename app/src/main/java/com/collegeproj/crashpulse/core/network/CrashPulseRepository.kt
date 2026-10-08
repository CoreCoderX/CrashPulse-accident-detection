package com.collegeproj.crashpulse.core.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import com.collegeproj.crashpulse.core.model.*
import com.collegeproj.crashpulse.core.storage.AppPreferences
import com.collegeproj.crashpulse.core.storage.SessionStore

class CrashPulseRepository(private val client: ApiClient, private val session: SessionStore, private val preferences: AppPreferences) {
    private fun api() = client.service()
    suspend fun login(loginId: String, password: String): Result<UserResponse> = call {
        val tokens = api().login(LoginRequest(loginId.trim(), password)).requireBody()
        session.save(tokens.access_token, tokens.refresh_token)
        api().me().requireBody()
    }
    suspend fun logout() = withContext(Dispatchers.IO) { session.refreshToken?.let { runCatching { api().logout(RefreshRequest(it)) } }; session.clear() }
    suspend fun me(): Result<UserResponse> = call { api().me().requireBody() }
    suspend fun terms(): Result<TermsResponse> = call { api().currentTerms().requireBody() }
    suspend fun acceptTerms(terms: TermsResponse): Result<Unit> = call { api().acceptTerms(AcceptTermsRequest(terms.version, terms.content_sha256)).requireBodyOrNull(); Unit }
    suspend fun upload(uri: Uri, context: Context): Result<JobResponse> = call {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("selected video could not be opened") }
        require(bytes.size <= 100 * 1024 * 1024) { "video is larger than the 100 MB app limit" }
        val name = queryName(uri, context) ?: "camera-video.mp4"
        api().uploadVideo(UUID.randomUUID().toString(), MultipartBody.Part.createFormData("video", name, bytes.toRequestBody("video/mp4".toMediaType()))).requireBody()
    }
    suspend fun job(id: UUID): Result<JobResponse> = call { api().job(id.toString()).requireBody() }
    suspend fun incidents(): Result<List<IncidentResponse>> = call { api().incidents().requireBody() }
    suspend fun incident(id: UUID): Result<IncidentDetailResponse> = call { api().incident(id.toString()).requireBody() }
    suspend fun result(id: UUID): Result<ApiResultView> = call { api().incidentResult(id.toString()).requireBody() }

    /**
     * Fetches one evidence frame for the alarm screen. The backend only serves
     * the roles selected by the published result, so "strongest.jpg" may be
     * absent for a clip that lacked surrounding context.
     */
    suspend fun evidence(id: UUID, name: String): Result<ByteArray> = call { api().incidentEvidence(id.toString(), name).requireBody().bytes() }
    suspend fun recordAction(id: UUID, action: String, method: String? = null): Result<UserActionResponse> = call { api().action(id.toString(), UUID.randomUUID().toString(), IncidentActionRequest(action, method)).requireBody() }
    suspend fun registerFcmToken(token: String): Result<DeviceResponse> = call { api().registerDevice(DeviceRequest(token)).requireBody() }
    suspend fun attachLocation(id: UUID, latitude: Double, longitude: Double, accuracy: Float, capturedAt: String): Result<UserActionResponse> = call {
        api().attachLocation(id.toString(), UUID.randomUUID().toString(), LocationAttachmentRequest(latitude, longitude, accuracy, capturedAt)).requireBody()
    }
    fun baseUrl(): String = preferences.apiBaseUrl
    fun setBaseUrl(value: String) { preferences.apiBaseUrl = value }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching { block() }.onFailure { if (it is ApiException && it.code == 401) session.clear() } }
    private fun queryName(uri: Uri, context: Context): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}

class ApiException(val code: Int, message: String) : Exception(message)
private fun <T> retrofit2.Response<T>.requireBody(): T { if (!isSuccessful) throw ApiException(code(), "Backend request failed (${code()})"); return body() ?: throw ApiException(code(), "Backend returned an empty response") }
private fun <T> retrofit2.Response<T>.requireBodyOrNull(): T? { if (!isSuccessful) throw ApiException(code(), "Backend request failed (${code()})"); return body() }
