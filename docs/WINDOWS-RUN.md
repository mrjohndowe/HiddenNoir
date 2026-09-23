# Run HiddenNoir on Windows + Physical Android Phone

## 1. Install Ollama

Install Ollama for Windows.

Open a new PowerShell and verify:

```powershell
ollama --version
```

## 2. Download the default model

```powershell
ollama pull qwen3:8b
```

Check:

```powershell
ollama list
```

## 3. Verify Ollama API

```powershell
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

## 4. Replace your current source files

Copy this project's files over the current HiddenNoir repository.

Keep your existing:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
```

if those already exist locally.

## 5. Test backend code

```powershell
.\gradlew.bat :api:test
```

Expected:

```text
BUILD SUCCESSFUL
```

## 6. Connect physical phone

Enable USB debugging and connect the phone.

```powershell
adb devices
```

Your phone should show as `device`.

Then:

```powershell
adb reverse tcp:8081 tcp:8081
```

Check:

```powershell
adb reverse --list
```

## 7. Start backend

```powershell
.\gradlew.bat :api:run
```

The backend creates:

```text
data/hiddennoir.db
```

automatically.

## 8. Verify backend

Open another PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/health
```

Expected shape:

```text
status model
------ -----
ok     qwen3:8b
```

## 9. Run Android app

In Android Studio:

1. Sync Gradle.
2. Select the connected physical phone.
3. Run `app`.

The debug app calls `http://127.0.0.1:8081`.

Because `adb reverse` is active, that reaches the backend on your PC.

## 10. Generate

1. Confirm 18+.
2. Paste a conversation.
3. Select relationship.
4. Select tone.
5. Select intensity.
6. Confirm everyone is 18+ if Explicit is selected.
7. Tap Generate replies.

The backend calls Ollama and returns the generated reply cards.

## Common errors

### Ollama unavailable

Test:

```powershell
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

Then:

```powershell
ollama list
```

Make sure the configured model exists.

### Phone cannot reach API

Repeat:

```powershell
adb reverse tcp:8081 tcp:8081
```

### Port 8081 already in use

Find the process:

```powershell
netstat -ano | findstr :8081
```

Or use another backend port and update Android's debug API URL.

### Model too large / slow

Try a smaller model:

```powershell
ollama pull llama3.2
$env:OLLAMA_MODEL="llama3.2"
.\gradlew.bat :api:run
```
