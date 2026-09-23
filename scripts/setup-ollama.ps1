$ErrorActionPreference = "Stop"

Write-Host "Checking Ollama..."
ollama --version

$model = if ($env:OLLAMA_MODEL) {
    $env:OLLAMA_MODEL
} else {
    "qwen3:8b"
}

Write-Host "Pulling model: $model"
ollama pull $model

Write-Host ""
Write-Host "Installed models:"
ollama list

Write-Host ""
Write-Host "Ollama setup complete."
