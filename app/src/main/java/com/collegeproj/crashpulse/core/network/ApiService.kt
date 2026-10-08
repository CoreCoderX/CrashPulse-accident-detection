package com.collegeproj.crashpulse.core.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import com.collegeproj.crashpulse.core.model.*

interface ApiService {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): Response<TokenResponse>
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>
    @POST("auth/logout") suspend fun logout(@Body body: RefreshRequest): Response<Unit>
    @GET("auth/me") suspend fun me(): Response<UserResponse>
    @GET("terms/current") suspend fun currentTerms(): Response<TermsResponse>
    @POST("terms/acceptance") suspend fun acceptTerms(@Body body: AcceptTermsRequest): Response<Any>
    @POST("devices/fcm-token") suspend fun registerDevice(@Body body: DeviceRequest): Response<DeviceResponse>
    @DELETE("devices/fcm-token/{deviceId}") suspend fun deleteDevice(@Path("deviceId") deviceId: String): Response<Unit>
    @Multipart @POST("videos/analyze") suspend fun uploadVideo(@Header("Idempotency-Key") key: String, @Part video: MultipartBody.Part): Response<JobResponse>
    @GET("jobs/{jobId}") suspend fun job(@Path("jobId") jobId: String): Response<JobResponse>
    @GET("jobs/{jobId}/result") suspend fun jobResult(@Path("jobId") jobId: String): Response<ResponseBody>
    @GET("incidents") suspend fun incidents(@Query("limit") limit: Int = 50): Response<List<IncidentResponse>>
    @GET("incidents/{incidentId}") suspend fun incident(@Path("incidentId") incidentId: String): Response<IncidentDetailResponse>
    @GET("incidents/{incidentId}/result") suspend fun incidentResult(@Path("incidentId") incidentId: String): Response<ApiResultView>
    @GET("incidents/{incidentId}/report") suspend fun incidentReport(@Path("incidentId") incidentId: String): Response<ResponseBody>
    @GET("incidents/{incidentId}/evidence/{name}") suspend fun incidentEvidence(@Path("incidentId") incidentId: String, @Path("name") name: String): Response<ResponseBody>
    @POST("incidents/{incidentId}/actions") suspend fun action(@Path("incidentId") incidentId: String, @Header("Idempotency-Key") key: String, @Body body: IncidentActionRequest): Response<UserActionResponse>
    @POST("incidents/{incidentId}/location") suspend fun attachLocation(@Path("incidentId") incidentId: String, @Header("Idempotency-Key") key: String, @Body body: LocationAttachmentRequest): Response<UserActionResponse>
}
