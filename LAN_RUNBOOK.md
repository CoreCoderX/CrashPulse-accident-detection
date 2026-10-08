# CrashPulse trusted-LAN runbook

This is for a local college demonstration only. It is not a public deployment
guide.

## Windows computer

Find the active private-network IPv4 address:

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
  Select-Object IPAddress,InterfaceAlias,PrefixLength
```

Start the Backend on one worker:

```powershell
Set-Location "C:\Users\sivap\Downloads\College_Proj\Accident_Detection\Backend"
.\.venv_backend\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

Limit Windows Defender Firewall to the trusted private subnet before using a
phone. Do not open port 8000 to public or untrusted networks. Confirm locally:

```powershell
Invoke-WebRequest http://127.0.0.1:8000/health/live
Invoke-WebRequest http://127.0.0.1:8000/health/ready
```

## Android phone

Connect the phone and Windows computer to the same trusted Wi-Fi. In CrashPulse
Settings enter:

```text
http://<Windows-private-LAN-IP>:8000/api/v1/
```

Sign in, accept Terms, enable notifications, and verify the Home readiness card.
The API's LAN HTTP exception applies only to the debug build. Release builds
require TLS and a secured deployment.

## Recovery

- If the phone cannot connect, first check the private IP, Wi-Fi isolation,
  firewall, server bind address, and `/health/live`.
- If login works but Terms is blocked, seed/verify the current terms version.
- If uploads fail, check Backend private storage, queue capacity, and detector
  path configuration.
- If no alert appears, check FCM credentials/device token, notification permission,
  alert channel, DND, battery restrictions, and provider/network delivery.
