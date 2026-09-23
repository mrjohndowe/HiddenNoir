function Write-Wait {
    param(
        [string]$Message = "Waiting",
        [int]$Seconds = 3
    )

    for ($i = $Seconds; $i -ge 1; $i--) {
        Write-Host "`r$Message... $i " -NoNewline
        Start-Sleep -Seconds 1
    }

    Write-Host "`r$Message... Done!    "
}

cls
Write-Wait "This script will uninstall the app from the device and re-install it and verify." -Seconds 2
Write-Wait "Press any key to continue or Ctrl+C to cancel."
[void][System.Console]::ReadKey($true)
Write-Wait "Processing ..."
$root = (Get-Location).Path
Write-Wait "HiddenNoir Directory: $root" -Seconds 1
Write-Wait "Uninstalling App: " -Seconds 1
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" uninstall com.hiddennoir.app
Write-Wait "App Removed " -Seconds 1
Write-Wait "Cleaning the build " -Seconds 1
.\gradlew.bat clean
Write-Wait "Build is cleaned" -Seconds 1
Write-Wait "Creating a new Build: " -Seconds 1
.\gradlew.bat :app-android:app:assembleDebug
Write-Wait "New Build Created: " -Seconds 1
Write-Wait "Installing the new APK: " -Seconds 1
.\gradlew.bat :app-android:app:installDebug --warning-mode all
Write-Wait "New APK installed: " -Seconds 1
Write-Wait "Verifying the Java Build: " -Seconds 1
Write-Wait "Press any key to continue or Ctrl+C to cancel."
[void][System.Console]::ReadKey($true)
Get-ChildItem -Recurse -Filter BuildConfig.java
Write-Wait "Java Verified: " -Seconds 2
Write-Wait "All done! " -Seconds 2
Write-Wait "Press any key to exit."
[void][System.Console]::ReadKey($true)

