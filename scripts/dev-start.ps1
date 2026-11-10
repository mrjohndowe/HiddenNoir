$ErrorActionPreference = "Stop"

Write-Host "Checking Ollama API..."
Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -Method Get | Out-Null
Write-Host "Ollama is reachable."

Write-Host "Starting the legacy HiddenNoir desktop API..."
.\gradlew.bat :api:run
