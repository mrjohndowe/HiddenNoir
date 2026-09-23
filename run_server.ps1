$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$adb = "C:\Users\MrJohnDowe\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apiPort = 8081
$apiBaseUrl = "http://127.0.0.1:$apiPort"
$logDirectory = Join-Path $projectRoot ".run-logs"

function Pause-Menu {
    Write-Host ""
    Read-Host "Press Enter to return to the menu" | Out-Null
}

function Get-ApiListener {
    Get-NetTCPConnection -State Listen -LocalPort $apiPort -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Get-ApiHealth {
    try {
        Invoke-RestMethod -Uri "$apiBaseUrl/health" -TimeoutSec 5
    } catch {
        $null
    }
}

function Show-ServerStatus {
    $listener = Get-ApiListener
    $health = Get-ApiHealth

    if ($health) {
        Write-Host "API status: running" -ForegroundColor Green
        Write-Host "Model: $($health.model)"
        Write-Host "URL: $apiBaseUrl"
    } elseif ($listener) {
        Write-Host "API status: port $apiPort is occupied by PID $($listener.OwningProcess), but health is unavailable." -ForegroundColor Yellow
    } else {
        Write-Host "API status: stopped" -ForegroundColor DarkYellow
    }
}

function Test-Ollama {
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 10
    Write-Host "Local Ollama is reachable. Installed models:"
    $response.models | ForEach-Object { Write-Host "  - $($_.name)" }
}

function Connect-Phone {
    if (-not (Test-Path $adb)) {
        throw "ADB was not found at $adb"
    }

    Write-Host "Checking connected Android devices..."
    & $adb devices
    Write-Host ""
    Write-Host "Forwarding phone localhost:$apiPort to this PC..."
    & $adb reverse "tcp:$apiPort" "tcp:$apiPort"
    Write-Host ""
    Write-Host "Active reverse mappings:"
    & $adb reverse --list
}

function Build-ApiPackage {
    Write-Host "Building the standalone API package..."
    & "$projectRoot\gradlew.bat" :api:installDist
}

function Start-ApiWrapper {
    param([Parameter(Mandatory)][string]$Model)

    if (Get-ApiListener) {
        Show-ServerStatus
        Write-Host "Stop the existing API before starting another one." -ForegroundColor Yellow
        return
    }

    Test-Ollama
    Build-ApiPackage

    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $serverOutput = Join-Path $logDirectory "api-$runStamp-output.log"
    $serverError = Join-Path $logDirectory "api-$runStamp-error.log"

    $startOptions = @{
        FilePath = "$projectRoot\api\build\install\api\bin\api.bat"
        WorkingDirectory = $projectRoot
        RedirectStandardOutput = $serverOutput
        RedirectStandardError = $serverError
        WindowStyle = "Hidden"
        PassThru = $true
    }

    $previousModel = $env:OLLAMA_MODEL
    $env:OLLAMA_MODEL = $Model
    try {
        $process = Start-Process @startOptions
    } finally {
        $env:OLLAMA_MODEL = $previousModel
    }

    # The JVM can take a few seconds to bind the HTTP port, especially after
    # Gradle has just rebuilt the distribution.  Poll instead of declaring a
    # failed launch after one early request.
    $health = $null
    $healthDeadline = (Get-Date).AddSeconds(15)
    do {
        $health = Get-ApiHealth
        if ($health) {
            break
        }

        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $healthDeadline)

    if ($health) {
        Write-Host "API wrapper started successfully (wrapper PID $($process.Id))." -ForegroundColor Green
        Write-Host "Model: $($health.model)"
        Write-Host "Logs: $logDirectory"
    } else {
        Write-Host "The API did not pass its health check. Review:" -ForegroundColor Red
        Write-Host "  $serverOutput"
        Write-Host "  $serverError"
    }
}

function Stop-ApiWrapper {
    $listener = Get-ApiListener
    if (-not $listener) {
        Write-Host "No API is listening on port $apiPort."
        return
    }

    Stop-Process -Id $listener.OwningProcess -Force
    Start-Sleep -Seconds 1
    Write-Host "Stopped the API process that was listening on port $apiPort."
}

function Show-LiveServerLogs {
    $latestLog = Get-ChildItem -Path $logDirectory -Filter "*-output.log" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $latestLog) {
        Write-Host "No API output log exists yet. Start the API first." -ForegroundColor Yellow
        return
    }

    Write-Host "Streaming $($latestLog.Name). Press Ctrl+C to stop viewing logs and return to the menu." -ForegroundColor Cyan
    Get-Content -Path $latestLog.FullName -Tail 30 -Wait
}

function Install-AndroidApp {
    Write-Host "Building and installing the debug app with its direct Wi-Fi API URL..."
    & "$projectRoot\gradlew.bat" :app-android:app:installDebug
}

Set-Location $projectRoot

do {
    Clear-Host
    Write-Host "HiddenNoir Server Wrapper" -ForegroundColor Cyan
    Write-Host ""
    Show-ServerStatus
    Write-Host ""
    Write-Host "1. Start API with adult writing model"
    Write-Host "2. Start API with fast general model"
    Write-Host "3. Stop the running API"
    Write-Host "4. Configure optional USB ADB reverse tunnel"
    Write-Host "5. Build and install Android app (direct Wi-Fi API)"
    Write-Host "6. Run API build and tests"
    Write-Host "7. Check Ollama and API health"
    Write-Host "8. Stream live API logs in this terminal"
    Write-Host "9. Open API logs folder"
    Write-Host "10. Exit"
    Write-Host ""

    switch (Read-Host "Choose an option") {
        "1" { Start-ApiWrapper "jimscard/adult-film-screenwriter-nsfw:latest"; Pause-Menu }
        "2" { Start-ApiWrapper "llama3.2:latest"; Pause-Menu }
        "3" { Stop-ApiWrapper; Pause-Menu }
        "4" { Connect-Phone; Pause-Menu }
        "5" { Install-AndroidApp; Pause-Menu }
        "6" {
            & "$projectRoot\gradlew.bat" :api:compileKotlin :api:test
            Pause-Menu
        }
        "7" { Test-Ollama; Show-ServerStatus; Pause-Menu }
        "8" { Show-LiveServerLogs }
        "9" { Invoke-Item $logDirectory; Pause-Menu }
        "10" { break }
        default {
            Write-Host "Choose a number from 1 to 10." -ForegroundColor Yellow
            Start-Sleep -Seconds 1
        }
    }
} while ($true)
