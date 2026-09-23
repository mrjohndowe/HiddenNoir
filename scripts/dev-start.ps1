$ErrorActionPreference = "Stop"

Write-Host "Checking Ollama API..."
Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -Method Get | Out-Null
Write-Host "Ollama is reachable."

Write-Host "Configuring physical phone tunnel..."
adb reverse tcp:8081 tcp:8081
Write-Host "ADB reverse configured."

Write-Host "Starting HiddenNoir API..."
.\gradlew.bat :api:run
