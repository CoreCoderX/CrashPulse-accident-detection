package com.collegeproj.crashpulse.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.collegeproj.crashpulse.core.di.CrashPulseApplication
import com.collegeproj.crashpulse.core.model.*
import com.collegeproj.crashpulse.core.network.CrashPulseRepository
import com.collegeproj.crashpulse.feature.alarm.AlarmActivity
import com.collegeproj.crashpulse.feature.auth.*
import com.collegeproj.crashpulse.feature.home.HomeViewModel
import com.collegeproj.crashpulse.feature.notifications.AlertNotifications
import com.collegeproj.crashpulse.ui.theme.CrashPulseTheme

class MainActivity : ComponentActivity() {
    private val sessionViewModel: SessionViewModel by viewModels {
        val app = application as CrashPulseApplication
        object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(app.container.repository, app.container.sessionStore) as T }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingIncidentId = intent.getStringExtra(EXTRA_PENDING_INCIDENT_ID)
        setContent { CrashPulseTheme { CrashPulseApp(sessionViewModel, pendingIncidentId) } }
    }

    companion object { const val EXTRA_PENDING_INCIDENT_ID = "pending_incident_id" }
}

@Composable
fun CrashPulseApp(session: SessionViewModel, pendingIncidentId: String? = null) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AuthState.Loading -> Centered("Preparing your secure session…", true)
        is AuthState.SignedOut -> LoginScreen(current.error, session.baseUrl(), session::setBaseUrl, session::login)
        is AuthState.TermsRequired -> TermsScreen(current, session::acceptTerms, session::signOut)
        is AuthState.SignedIn -> MainShell(session, pendingIncidentId)
    }
}

@Composable
private fun LoginScreen(errorMessage: String?, initialBaseUrl: String, onSaveBaseUrl: (String) -> Unit, onLogin: (String, String) -> Unit) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember(errorMessage) { mutableStateOf(errorMessage) }
    var showServer by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(initialBaseUrl) }
    var serverSaved by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("CrashPulse", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("A calm, human-reviewed path from detector evidence to action.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp))
            OutlinedTextField(loginId, { loginId = it; error = null }, label = { Text("Login ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Button(onClick = { if (loginId.isBlank() || password.isBlank()) error = "Enter your ID and password." else onLogin(loginId, password) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentPadding = PaddingValues(16.dp)) { Text("Sign in") }
            TextButton(onClick = { showServer = !showServer; serverSaved = false }, modifier = Modifier.padding(top = 8.dp)) { Text(if (showServer) "Hide server settings" else "Server settings") }
            if (showServer) {
                OutlinedTextField(serverUrl, { serverUrl = it; serverSaved = false; error = null }, label = { Text("Backend server") }, supportingText = { Text("Trusted LAN URL ending in /api/v1/, e.g. http://192.168.1.10:8000/api/v1/") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val trimmed = serverUrl.trim()
                        when {
                            trimmed.isBlank() -> error = "Enter the backend server URL."
                            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> error = "The URL must start with http:// or https://"
                            !trimmed.contains("/api/v1") -> error = "The URL must include the /api/v1/ path."
                            else -> { onSaveBaseUrl(trimmed); serverUrl = trimmed; serverSaved = true; error = null }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentPadding = PaddingValues(12.dp),
                ) { Text("Save server") }
                if (serverSaved) Text("Server saved. It applies to the next sign-in.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            Text("Accounts are created by a local administrator. There is no public sign-up or password recovery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun TermsScreen(state: AuthState.TermsRequired, onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Before you continue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Terms & Conditions · ${state.terms.version}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Card(Modifier.weight(1f).fillMaxWidth()) { Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) { Text(state.terms.content, style = MaterialTheme.typography.bodyLarge) } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun MainShell(session: SessionViewModel, pendingIncidentId: String? = null) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as CrashPulseApplication
    val homeVm: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(c: Class<T>): T = HomeViewModel(app.container.repository) as T })
    val startDestination = pendingIncidentId?.let { "incident/$it" } ?: "home"
    NavHost(navController = nav, startDestination = startDestination) {
        composable("home") { HomeScreen(homeVm, nav) }
        composable("history") { HistoryScreen(app.container.repository, nav) }
        composable("incident/{id}") { backStack -> backStack.arguments?.getString("id")?.let { IncidentScreen(app.container.repository, UUID.fromString(it), nav) } }
        composable("settings") { SettingsScreen(app.container.repository, session, nav) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellScaffold(title: String, nav: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    val route = nav.currentBackStackEntry?.destination?.route
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }, bottomBar = {
        NavigationBar {
            listOf(Triple("home", "Home", Icons.Outlined.Home), Triple("history", "History", Icons.Outlined.History), Triple("settings", "Settings", Icons.Outlined.Settings)).forEach { (destination, label, icon) ->
                NavigationBarItem(selected = route == destination, onClick = { nav.navigate(destination) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }, content = content)
}

@Composable
private fun HomeScreen(vm: HomeViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.upload(uri, context) } }
    LaunchedEffect(Unit) { vm.load() }
    ShellScaffold("Home", nav) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
            item { StatusCard() }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp)) { Text("Analyze a video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("The detector result is evidence for human review, not proof of an accident.", modifier = Modifier.padding(top = 6.dp)); Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.padding(top = 16.dp)) { Icon(Icons.Outlined.UploadFile, null); Text("  Choose video") } } }
            }
            state.job?.let { job -> item { JobCard(job) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Text("Recent incidents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.incidents.isEmpty()) item { Text("No incidents yet. Upload a local clip to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.incidents.take(3), key = { it.id }) { incident -> IncidentCard(incident) { nav.navigate("incident/${incident.id}") } }
        }
    }
}

@Composable private fun StatusCard() { Card { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Backend readiness", style = MaterialTheme.typography.labelLarge); Text("Authenticated session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Alerts remain human-reviewed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("READY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }
@Composable private fun JobCard(job: JobResponse) { Card { Column(Modifier.padding(18.dp)) { Text("Analysis ${job.status.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (job.status in setOf("QUEUED", "RUNNING", "UPLOADING")) CircularProgressIndicator(Modifier.padding(top = 12.dp).size(26.dp)); job.error_message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) } } } }
@Composable private fun IncidentCard(incident: IncidentResponse, open: () -> Unit) { Card(onClick = open) { Column(Modifier.padding(18.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Human review required", fontWeight = FontWeight.Bold); Text(incident.status, color = MaterialTheme.colorScheme.primary) }; Text(incident.safe_summary, modifier = Modifier.padding(top = 8.dp)); Text(incident.created_at, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) } } }

@Composable private fun HistoryScreen(repository: CrashPulseRepository, nav: NavHostController) { var incidents by remember { mutableStateOf<List<IncidentResponse>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }; LaunchedEffect(Unit) { repository.incidents().fold({ incidents = it }, { error = it.message }) }; ShellScaffold("History", nav) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }; if (incidents.isEmpty() && error == null) item { Text("No review history yet.") }; items(incidents, key = { it.id }) { IncidentCard(it) { nav.navigate("incident/${it.id}") } } } } }

@Composable
private fun IncidentScreen(repository: CrashPulseRepository, id: UUID, nav: NavHostController) {
    var detail by remember(id) { mutableStateOf<IncidentDetailResponse?>(null) }
    var result by remember(id) { mutableStateOf<ApiResultView?>(null) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    var savingReview by remember(id) { mutableStateOf(false) }
    var evidence by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    var evidenceNote by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        repository.incident(id).fold({ detail = it }, { message = it.message })
        repository.result(id).onSuccess { result = it }
        // Reviewing from History should show the same evidence frame the alarm
        // does; the alert surface is not the only place a human verifies a claim.
        repository.evidence(id, "strongest.jpg").fold(
            { bytes ->
                evidence = withContext(Dispatchers.Default) {
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                }
                if (evidence == null) evidenceNote = "Evidence frame could not be decoded on this device."
            },
            { evidenceNote = "No strongest evidence frame was recorded for this incident." },
        )
    }

    ShellScaffold("Incident detail", nav) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TextButton(onClick = { nav.popBackStack() }) { Text("← Back") } }
            evidence?.let { image ->
                item {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Image(bitmap = image, contentDescription = "Strongest detector evidence frame", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                            Text("Strongest evidence frame - where detection fired", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            evidenceNote?.let { note -> item { Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            detail?.let { incident ->
                item {
                    Card {
                        Column(Modifier.padding(18.dp)) {
                            Text("Review status: ${incident.status}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(incident.safe_summary, modifier = Modifier.padding(top = 8.dp))
                            Divider(Modifier.padding(vertical = 14.dp))
                            Text("Detector evidence", fontWeight = FontWeight.Bold)
                            Text("No emergency action is authorized by this result.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            Button(
                                enabled = !savingReview,
                                onClick = {
                                    if (!savingReview) {
                                        savingReview = true
                                        message = null
                                        scope.launch {
                                            try {
                                                repository.recordAction(id, "MARKED_REVIEWED").fold(
                                                    onSuccess = {
                                                        // Refresh the authoritative status and audit trail only
                                                        // after the backend confirms the saved review action.
                                                        repository.incident(id).fold(
                                                            { detail = it },
                                                            { message = "Review saved, but details could not be refreshed. Reopen this incident." },
                                                        )
                                                    },
                                                    onFailure = { message = it.message ?: "Review could not be saved. Please try again." },
                                                )
                                            } finally {
                                                savingReview = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.padding(top = 16.dp),
                            ) { Text(if (savingReview) "Saving review…" else "Mark reviewed") }
                        }
                    }
                }
            }
            result?.let { view ->
                item {
                    Card {
                        Column(Modifier.padding(18.dp)) {
                            Text("Result contract ${view.schema_version}", fontWeight = FontWeight.Bold)
                            Text("Decision: ${view.incident_decision}")
                            Text("Complete video processed: ${view.complete_video_processed}")
                            Text("Human review required: ${view.human_review_required}", modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun SettingsScreen(repository: CrashPulseRepository, session: SessionViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(repository.baseUrl()) }
    var saved by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val fullScreenAllowed = AlertNotifications.canUseFullScreen(context)
    ShellScaffold("Settings", nav) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(url, { url = it; saved = false }, label = { Text("Backend URL") }, supportingText = { Text("Use a trusted LAN URL ending in /api/v1/") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repository.setBaseUrl(url); saved = true }, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
            if (saved) Text("Connection saved. Reopen the app if the backend host changed.", color = MaterialTheme.colorScheme.primary)
            Divider()
            Text("Alert readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (notificationsEnabled) "Notifications: enabled" else "Notifications: disabled", color = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (Build.VERSION.SDK_INT >= 33 && !notificationsEnabled) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            Text(if (fullScreenAllowed) "Full-screen alert access: allowed" else "Full-screen alert access: unavailable or restricted", color = if (fullScreenAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (Build.VERSION.SDK_INT >= 34 && !fullScreenAllowed) OutlinedButton(onClick = { startActivity(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Open full-screen alert settings") }
            Text("Android, Do Not Disturb, lock-screen, channel, and OEM settings remain authoritative. CrashPulse cannot guarantee an undismissible or full-screen alert.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Divider()
            Text("Privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("CrashPulse requests location only after Proceed and labels it as the phone's current location. No background tracking is used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { session.signOut(); nav.navigate("home") { popUpTo(0) } }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}

private fun startActivity(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
@Composable private fun Centered(text: String, progress: Boolean) { Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { if (progress) CircularProgressIndicator(); Text(text, modifier = Modifier.padding(top = 16.dp)) } } }
