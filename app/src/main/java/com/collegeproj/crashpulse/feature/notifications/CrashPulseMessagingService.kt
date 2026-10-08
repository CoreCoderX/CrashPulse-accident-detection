package com.collegeproj.crashpulse.feature.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.collegeproj.crashpulse.core.di.AppContainer

class CrashPulseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AppContainer(applicationContext).repository.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Do not wait for the API before showing the local alert. The backend
        // incident is durable and the app synchronizes its details after open.
        val incidentId = message.data["incident_id"] ?: return
        if (!AlertDeduplicator(this).accept(incidentId)) return
        val title = message.data["title"] ?: "CrashPulse alert"
        val body = message.data["body"] ?: "A detector alert needs human review."
        AlertNotifications.showIncident(this, incidentId, title, body)
    }
}
