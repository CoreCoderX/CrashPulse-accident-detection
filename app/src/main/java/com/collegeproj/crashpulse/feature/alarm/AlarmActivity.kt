package com.collegeproj.crashpulse.feature.alarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import com.collegeproj.crashpulse.core.di.CrashPulseApplication
import com.collegeproj.crashpulse.core.model.IncidentDetailResponse
import com.collegeproj.crashpulse.core.network.ApiException
import com.collegeproj.crashpulse.core.network.CrashPulseRepository
import com.collegeproj.crashpulse.feature.notifications.AlertNotifications
import com.collegeproj.crashpulse.ui.MainActivity
import com.collegeproj.crashpulse.ui.theme.CrashPulseTheme

sealed interface AlarmState {
    data object Loading : AlarmState
    data class Ready(val incident: IncidentDetailResponse) : AlarmState
    data class Proceeding(val incident: IncidentDetailResponse) : AlarmState
    data class Proceed(val incident: IncidentDetailResponse) : AlarmState
    data class Error(val message: String) : AlarmState
}

class AlarmActivity : ComponentActivity() {
    private lateinit var repository: CrashPulseRepository
    private var state by mutableStateOf<AlarmState>(AlarmState.Loading)
    private var pendingCallMethod: String? = null

    /** Strongest detector evidence frame, decoded for the reviewer. */
    private var evidenceImage by mutableStateOf<ImageBitmap?>(null)
    private var evidenceNote by mutableStateOf<String?>(null)

    /**
     * Loops an alarm-grade sound while this alert is on screen, and stops it as
     * soon as the reviewer acts (Proceed / Ignore / Mark reviewed). This is the
     * same behaviour as an incoming call: the sound belongs to the visible alert,
     * not only to the notification that launched it.
     */
    private var alarmPlayer: MediaPlayer? = null

    /**
     * Set once the reviewer has engaged with the alert (slid to Proceed, or
     * acted in any other way). It latches so the tone cannot restart when the
     * activity is resumed after the user briefly leaves it - for example to open
     * the dialer and come back.
     */
    private var alarmAcknowledged = false

    private fun startAlarmSound() {
        if (alarmPlayer != null || alarmAcknowledged) return
        val uri = AlertNotifications.alarmUri() ?: return
        alarmPlayer = runCatching {
            MediaPlayer.create(this, uri)?.apply {
                setAudioAttributes(AlertNotifications.alarmSound())
                isLooping = true
                start()
            }
        }.getOrNull()
    }

    /** Silences the alarm for good, for this alert. Safe to call repeatedly. */
    private fun acknowledgeAlarm() {
        alarmAcknowledged = true
        stopAlarmSound()
    }

    private fun stopAlarmSound() {
        runCatching { alarmPlayer?.stop() }
        runCatching { alarmPlayer?.release() }
        alarmPlayer = null
    }

    override fun onResume() {
        super.onResume()
        startAlarmSound()
    }

    override fun onPause() {
        stopAlarmSound()
        super.onPause()
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
    }

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        lifecycleScope.launch { if (permissions.values.any { it }) attachCurrentLocation(); executePendingCall() }
    }
    private val callLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) executePendingCall() else openDialer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repository = (application as CrashPulseApplication).container.repository
        val id = intent.getStringExtra(EXTRA_INCIDENT_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (id == null) state = AlarmState.Error("This alert does not contain a valid incident reference.") else load(id)
        setContent { CrashPulseTheme { AlarmRoot(state, evidenceImage, evidenceNote, ::ignore, ::showProceed, ::markReviewed, ::proceedChoices, ::openSettings) } }
    }

    /**
     * Loads the strongest evidence frame. A missing frame is not an error: the
     * backend only serves roles the published result selected, and a clip with
     * insufficient surrounding context has no "strongest" companion frames.
     */
    private fun loadEvidence(id: UUID) {
        lifecycleScope.launch {
            repository.evidence(id, "strongest.jpg").fold(
                { bytes ->
                    val decoded = withContext(Dispatchers.Default) {
                        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                    }
                    evidenceImage = decoded
                    if (decoded == null) evidenceNote = "Evidence frame could not be decoded on this device."
                },
                {
                    // Never block the alarm: the reviewer still gets the alert
                    // text, actions, and audit trail without the frame.
                    evidenceNote = "Evidence frame unavailable for this incident."
                },
            )
        }
    }

    private fun load(id: UUID) {
        lifecycleScope.launch {
            repository.incident(id).fold(
                { state = AlarmState.Ready(it); loadEvidence(id) },
                {
                    val hasSession = (application as CrashPulseApplication).container.sessionStore.hasSession
                    if (it is ApiException && it.code in setOf(401, 403) || !hasSession) {
                        startActivity(Intent(this@AlarmActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_PENDING_INCIDENT_ID, id.toString()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    } else {
                        // This screen has no retry path, so leaving the alarm
                        // looping here would buzz with nothing the user can do.
                        acknowledgeAlarm()
                        state = AlarmState.Error(it.message ?: "Incident details are unavailable. Open CrashPulse to sync.")
                    }
                },
            )
        }
    }

    private fun ignore(incident: IncidentDetailResponse) = saveAction(incident, "IGNORED")
    private fun markReviewed(incident: IncidentDetailResponse) = saveAction(incident, "MARKED_REVIEWED")

    private fun saveAction(incident: IncidentDetailResponse, action: String) {
        acknowledgeAlarm()
        lifecycleScope.launch {
            repository.recordAction(incident.id, action).fold({ finishAlert(incident.id.toString()) }, { state = AlarmState.Error(it.message ?: "Could not save your review action.") })
        }
    }

    private fun showProceed(incident: IncidentDetailResponse) {
        // The reviewer has engaged, so the alarm tone stops here and the screen
        // becomes the response chooser. Latched so it cannot restart on resume.
        acknowledgeAlarm()
        state = AlarmState.Proceeding(incident)
        lifecycleScope.launch { repository.recordAction(incident.id, "PROCEEDED").fold({ state = AlarmState.Proceed(incident) }, { state = AlarmState.Error(it.message ?: "Proceed could not be saved.") }) }
    }
    private fun proceedChoices(incident: IncidentDetailResponse, method: String) { pendingCallMethod = method; requestLocationThenCall() }

    private fun requestLocationThenCall() {
        val needs = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (needs.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) lifecycleScope.launch { attachCurrentLocation(); executePendingCall() }
        else locationLauncher.launch(needs.toTypedArray())
    }

    private suspend fun attachCurrentLocation() {
        val location = withTimeoutOrNull(6000) { currentLocation() } ?: return
        val incident = (state as? AlarmState.Proceed)?.incident ?: return
        repository.attachLocation(incident.id, location.latitude, location.longitude, location.accuracy, Instant.ofEpochMilli(location.time).toString())
    }

    private suspend fun currentLocation(): android.location.Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(0).setDurationMillis(5000).build()
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(request, cancellation.token)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        continuation.invokeOnCancellation { cancellation.cancel() }
    }

    private fun executePendingCall() {
        when (pendingCallMethod) {
            "DIALER" -> openDialer()
            "CALL_NOW" -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:108")))
                intent.getStringExtra(EXTRA_INCIDENT_ID)?.let(::finishAlert)
            } else callLauncher.launch(Manifest.permission.CALL_PHONE)
        }
        pendingCallMethod = null
    }

    private fun openDialer() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
        intent.getStringExtra(EXTRA_INCIDENT_ID)?.let(::finishAlert)
    }
    private fun openSettings() { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
    private fun finishAlert(id: String) { AlertNotifications.clear(this, id); finish() }

    companion object { const val EXTRA_INCIDENT_ID = "incident_id" }
}

@Composable
private fun AlarmRoot(state: AlarmState, evidenceImage: ImageBitmap?, evidenceNote: String?, onIgnore: (IncidentDetailResponse) -> Unit, onProceed: (IncidentDetailResponse) -> Unit, onReviewed: (IncidentDetailResponse) -> Unit, onChoices: (IncidentDetailResponse, String) -> Unit, onSettings: () -> Unit) {
    when (state) {
        AlarmState.Loading -> Surface(Modifier.fillMaxSize(), color = Color(0xFF8F1D2C)) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Color.White); Text("Opening alert…", color = Color.White, modifier = Modifier.padding(top = 16.dp)) } }
        is AlarmState.Error -> Surface(Modifier.fillMaxSize(), color = Color(0xFF8F1D2C)) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Alert needs app access", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(state.message, color = Color.White, modifier = Modifier.padding(top = 12.dp)); Button(onClick = onSettings, modifier = Modifier.padding(top = 24.dp)) { Text("Open notification settings") } } }
        is AlarmState.Ready -> AlarmContent(state.incident, evidenceImage, evidenceNote, onIgnore, onProceed, onReviewed)
        is AlarmState.Proceeding -> Surface(Modifier.fillMaxSize(), color = Color(0xFF8F1D2C)) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Color.White); Text("Saving Proceed…", color = Color.White, modifier = Modifier.padding(top = 16.dp)) } }
        is AlarmState.Proceed -> ProceedContent(state.incident, onChoices)
    }
}

@Composable
private fun AlarmContent(incident: IncidentDetailResponse, evidenceImage: ImageBitmap?, evidenceNote: String?, onIgnore: (IncidentDetailResponse) -> Unit, onProceed: (IncidentDetailResponse) -> Unit, onReviewed: (IncidentDetailResponse) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF8F1D2C)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column { Text("CRASHPULSE ALERT", color = Color(0xFFFFD6D8), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Text("Review required", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text("Temporal detector evidence is available. It is not proof of an accident.", color = Color(0xFFFFE9E9), modifier = Modifier.padding(top = 12.dp)); evidenceImage?.let { image -> Card(Modifier.fillMaxWidth().padding(top = 28.dp)) { Column(Modifier.padding(12.dp)) { Image(bitmap = image, contentDescription = "Strongest detector evidence frame", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit); Text("Strongest evidence frame - where detection fired", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) } } }; evidenceNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }; Card(Modifier.fillMaxWidth().padding(top = 28.dp)) { Column(Modifier.padding(18.dp)) { Text(incident.safe_summary, fontWeight = FontWeight.Bold); Text("Keep your judgment in the loop before any action.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) } } }
            Column { SlideToRespond(onProceed = { onProceed(incident) }); Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { onIgnore(incident) }) { Text("Ignore") }; OutlinedButton(onClick = { onReviewed(incident) }) { Text("Mark reviewed") } }; Text("Android may suppress or dismiss alerts based on device settings, Do Not Disturb, notification permission, and full-screen eligibility.", color = Color(0xFFFFD6D8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp)) } }
    }
}

@Composable
private fun ProceedContent(incident: IncidentDetailResponse, onChoices: (IncidentDetailResponse, String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF8F1D2C)) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Choose how to respond", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Location is optional and means the phone's current location, not the camera or crash location.", color = Color(0xFFFFE9E9), modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)); Button(onClick = { onChoices(incident, "DIALER") }, modifier = Modifier.fillMaxWidth()) { Text("Open Phone with 108 filled in") }; Button(onClick = { onChoices(incident, "CALL_NOW") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Call now") }; Text("Emergency calls remain OS-mediated and may require system confirmation. If permission is denied, CrashPulse opens the dialer instead.", color = Color(0xFFFFD6D8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp)) } }
}

@Composable
private fun SlideToRespond(onProceed: () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val thumbWidth = 68.dp.value
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(68.dp).onSizeChanged { width = it.width.toFloat() }.clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = .18f)).pointerInput(Unit) { detectDragGestures(onDrag = { change, drag -> change.consume(); offset = (offset + drag.x).coerceIn(0f, width - thumbWidth) }, onDragEnd = { if (offset > width * .55f) { offset = 0f; onProceed() } else offset = 0f }) }) {
            Text("Slide to respond", color = Color.White, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.size(68.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Filled.ArrowForward, contentDescription = "Slide to respond", tint = Color(0xFF8F1D2C)) }
        }
        Text("Or use the buttons below", color = Color(0xFFFFD6D8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
