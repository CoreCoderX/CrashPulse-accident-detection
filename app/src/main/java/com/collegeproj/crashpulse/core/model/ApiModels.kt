package com.collegeproj.crashpulse.core.model

import java.util.UUID

data class LoginRequest(val login_id: String, val password: String)
data class RefreshRequest(val refresh_token: String)
data class TokenResponse(val access_token: String, val refresh_token: String, val expires_at: String)
data class UserResponse(val id: UUID, val login_id: String, val role: String, val is_active: Boolean, val created_at: String)
data class TermsResponse(val version: String, val content: String, val content_sha256: String, val is_accepted: Boolean)
data class AcceptTermsRequest(val version: String, val content_sha256: String)
data class JobResponse(val id: UUID, val status: String, val error_message: String?, val incident_id: UUID?, val created_at: String, val completed_at: String?, val decision: String?)
data class IncidentResponse(val id: UUID, val status: String, val safe_summary: String, val occurred_at: String?, val created_at: String, val detector_run_id: String?, val result_available: Boolean, val evidence_available: Boolean)
data class IncidentDetailResponse(val id: UUID, val status: String, val safe_summary: String, val occurred_at: String?, val created_at: String, val detector_run_id: String?, val result_available: Boolean, val evidence_available: Boolean, val job_id: UUID?, val user_actions: List<UserActionResponse>)
data class UserActionResponse(val id: UUID, val action: String, val call_method: String?, val location_consent: Boolean, val created_at: String)
data class IncidentActionRequest(val action: String, val call_method: String? = null, val location_consent: Boolean = false)
data class DeviceRequest(val token: String, val platform: String = "ANDROID")
data class DeviceResponse(val id: UUID, val platform: String, val is_active: Boolean, val last_seen_at: String)
data class LocationAttachmentRequest(val latitude: Double, val longitude: Double, val accuracy_meters: Float, val captured_at: String, val consent: Boolean = true)
data class ApiResultView(val schema_version: String, val run_id: String, val created_at_utc: String, val incident_decision: String, val accident_detected: Boolean?, val complete_video_processed: Boolean, val termination_reason: String, val human_review_required: Boolean, val emergency_action_authorized: Boolean, val incidents: List<Map<String, Any?>> = emptyList())
