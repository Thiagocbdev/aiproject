# run-local.ps1 — Inicia um MS localmente com env vars do .env
#
# Pré-requisitos:
#   - docker compose up -d postgres redis   (infra sempre em Docker)
#   - Ollama a correr nativamente (http://localhost:11434)
#   - .env na raiz do projecto com OPENROUTER_API_KEY e GEMINI_API_KEY
#
# Uso:
#   .\run-local.ps1 hotel-info    (porta 8081)
#   .\run-local.ps1 ai-data       (porta 8082)
#   .\run-local.ps1 concierge     (porta 8080)

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("hotel-info", "ai-data", "concierge")]
    [string]$ms
)

# ── Java 21 ──────────────────────────────────────────────────────────
$java21Paths = @(
    "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.5",
    "$env:USERPROFILE\.jdks\openjdk-21",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.8",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.7"
)
$jdk21 = $java21Paths | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($jdk21) {
    $env:JAVA_HOME = $jdk21
    $env:PATH = "$jdk21\bin;$env:PATH"
    Write-Host "[run-local] JAVA_HOME -> $jdk21" -ForegroundColor Cyan
} else {
    Write-Warning "Java 21 nao encontrado — verificar JAVA_HOME ($env:JAVA_HOME)"
}

# ── Carregar .env ────────────────────────────────────────────────────
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        if ($parts.Length -eq 2) {
            [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
        }
    }
    Write-Host "[run-local] .env carregado" -ForegroundColor Cyan
} else {
    Write-Warning ".env nao encontrado em $envFile — API keys podem faltar"
}

# ── Iniciar MS ────────────────────────────────────────────────────────
$dirs = @{
    "hotel-info" = "ms-hotel-info"
    "ai-data"    = "ms-ai-data"
    "concierge"  = "ms-hotel-concierge-ai"
}
$ports = @{ "hotel-info" = 8081; "ai-data" = 8082; "concierge" = 8080 }

$dir = Join-Path $PSScriptRoot $dirs[$ms]
Write-Host "[run-local] Iniciando $($dirs[$ms]) na porta $($ports[$ms])..." -ForegroundColor Green
Push-Location $dir
try {
    mvn spring-boot:run
} finally {
    Pop-Location
}
