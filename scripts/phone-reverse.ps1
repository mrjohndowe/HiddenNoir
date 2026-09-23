$ErrorActionPreference = "Stop"

Write-Host "Connected Android devices:"
adb devices

Write-Host ""
Write-Host "Forwarding phone localhost:8081 to PC localhost:8081..."
adb reverse tcp:8081 tcp:8081

Write-Host ""
Write-Host "Current reverse mappings:"
adb reverse --list
