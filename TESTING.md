# CrashPulse Android testing and handoff

## Build and unit checks

Run with Android Studio's bundled JDK 21 or a configured JDK 17+:

```powershell
Set-Location "C:\Users\sivap\Downloads\College_Proj\CrashPulse"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

The Phase 4/5 Windows verification recorded successful `assembleDebug` and
`testDebugUnitTest` tasks. The debug artifact is:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Real-phone preparation

1. Start the Backend on the detection computer.
2. Confirm `/health/ready` locally.
3. Bind the Backend to the trusted LAN interface and apply a private-network
   firewall rule only.
4. Install the fresh debug APK on the phone.
5. Enter `http://<Windows-LAN-IP>:8000/api/v1/` in Settings.
6. Sign in with an administrator-provisioned mobile account.
7. Accept the current Terms version.
8. Allow notifications when prompted from Settings.
9. Verify the FCM token appears only through the authenticated device route.

## Required test matrix

| Scenario | Expected behavior | Actual status |
|---|---|---|
| Foreground FCM | Local alert is created immediately | Pending real phone/FCM test |
| Background FCM | Heads-up notification; tap opens alarm | Pending real phone/FCM test |
| Locked screen | Best-effort private notification/full-screen if OS permits | Pending real phone/OS test |
| Notifications denied | No guaranteed alert; Settings explains recovery | Source implemented; device test pending |
| Full-screen denied | Heads-up/tap-open fallback | Source implemented; device test pending |
| Duplicate FCM delivery | Same incident is not reposted | Source/static verified; device test pending |
| Ignore | Backend audit action, alert closes | Source implemented; device test pending |
| Mark reviewed | Backend audit action, original incident remains | Source implemented; device test pending |
| Proceed | Backend action saved before response choices | Source implemented; device test pending |
| Open Phone | `ACTION_DIAL` opens 108 for user confirmation | Source implemented; device test pending |
| Call now denied | Dialer fallback; no automatic call | Source implemented; device test pending |
| Location denied | Call/dialer still available; no location stored | Source implemented; device test pending |
| Network loss | Durable backend incident/history remains source of truth | Pending real LAN test |

## Safety limits

- FCM is best effort and not a guaranteed alarm channel.
- Android controls notification permission, channels, DND, lock-screen visibility,
  full-screen eligibility, call confirmation, and OEM background restrictions.
- The notification is ongoing while unreviewed but cannot honestly be promised to
  be impossible to dismiss.
- CrashPulse never auto-calls, sends SMS, dispatches emergency services, or tracks
  location in the background.
- A detector confirmation is temporal model evidence requiring human review; it is
  not verified ground truth or a medical/emergency determination.
