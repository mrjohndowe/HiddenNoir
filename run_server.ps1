$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
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

function Build-ApiPackage {
    $apiInstallDirectory = Join-Path $projectRoot "api\build\install\api"
    $expectedInstallDirectory = [System.IO.Path]::GetFullPath($apiInstallDirectory)

    # installDist cannot replace a partial/stale distribution. This is a
    # generated directory only, and Start-ApiWrapper has already confirmed
    # that no API process is listening before reaching this function.
    if (Test-Path -LiteralPath $apiInstallDirectory) {
        $resolvedInstallDirectory = (Resolve-Path -LiteralPath $apiInstallDirectory).Path
        if (-not [string]::Equals(
                [System.IO.Path]::GetFullPath($resolvedInstallDirectory),
                $expectedInstallDirectory,
                [System.StringComparison]::OrdinalIgnoreCase
            )) {
            throw "Refusing to remove an unexpected API install path: $resolvedInstallDirectory"
        }

        Write-Host "Removing the stale generated API package..."
        Remove-Item -LiteralPath $resolvedInstallDirectory -Recurse -Force
    }

    Write-Host "Building the standalone API package..."
    Invoke-Gradle :api:installDist
}

function Invoke-Gradle {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & "$projectRoot\gradlew.bat" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }
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

function Get-ConnectedDeviceCount {
    if (-not (Test-Path $adb)) {
        throw "ADB was not found at $adb"
    }

    @(& $adb devices) |
        Where-Object { $_ -match "^\S+\s+device$" } |
        Measure-Object |
        Select-Object -ExpandProperty Count
}

function Build-AndroidApp {
    param([switch]$Clean)

    if ($Clean) { Invoke-Gradle :app-android:app:clean }
    Invoke-Gradle :app-android:app:assembleDebug

    $apk = Join-Path $projectRoot 'app-android\app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) { throw "Debug APK was not found at $apk." }
    Write-Host "Standalone APK: $apk"
}

function Install-AndroidApp {
    param([switch]$Clean)

    if ((Get-ConnectedDeviceCount) -ne 1) {
        throw 'Connect exactly one authorized Android device for installation.'
    }
    Build-AndroidApp -Clean:$Clean
    $apk = Join-Path $projectRoot 'app-android\app\build\outputs\apk\debug\app-debug.apk'
    & $adb install --no-streaming -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'ADB could not install the standalone APK.' }

    $installed = @(& $adb shell pm path com.hiddennoir.app) | Select-String -Quiet 'package:'
    if (-not $installed) { throw 'HiddenNoir could not be verified on the device.' }
    Write-Host 'Installed HiddenNoir. Open the app and import a local GGUF model.' -ForegroundColor Green
}

Set-Location -LiteralPath $projectRoot

while ($true) {
    if (-not [Console]::IsOutputRedirected) { Clear-Host }
    Write-Host 'HiddenNoir Control Center' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Standalone Android'
    Write-Host '  1. Build debug APK'
    Write-Host '  2. Build and install on the connected device'
    Write-Host '  3. Clean build and update the app on the connected device'
    Write-Host ''
    Write-Host 'Legacy desktop API'
    Write-Host '  4. Start API with adult writing model'
    Write-Host '  5. Start API with fast general model'
    Write-Host '  6. Stop the API'
    Write-Host '  7. Run API build and tests'
    Write-Host '  8. Check Ollama and API health'
    Write-Host '  9. Stream live API logs'
    Write-Host ' 10. Open API logs folder'
    Write-Host ' 11. Exit'
    Write-Host ''

    try {
        switch (Read-Host 'Choose an option') {
            '1' { Build-AndroidApp }
            '2' { Install-AndroidApp }
            '3' { Install-AndroidApp -Clean }
            '4' { Start-ApiWrapper 'jimscard/adult-film-screenwriter-nsfw:latest' }
            '5' { Start-ApiWrapper 'llama3.2:latest' }
            '6' { Stop-ApiWrapper }
            '7' { Invoke-Gradle :api:compileKotlin :api:test }
            '8' { Test-Ollama; Show-ServerStatus }
            '9' { Show-LiveServerLogs }
            '10' { Invoke-Item $logDirectory }
            '11' { return }
            default { Write-Host 'Choose a number from 1 to 11.' -ForegroundColor Yellow }
        }
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
    Pause-Menu
}
