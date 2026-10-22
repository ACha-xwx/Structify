param([ValidateRange(1024,65535)][int]$Port = 8792)
$ErrorActionPreference = "Stop"
$taskRepoRoot = Split-Path -Parent $PSScriptRoot
$taskDatabaseDir = Join-Path $taskRepoRoot "private/dev-db"
New-Item -ItemType Directory -Force -Path $taskDatabaseDir | Out-Null
if (-not $env:DB_URL) {
    $taskDatabasePath = (Join-Path $taskDatabaseDir "reviewed-outline").Replace("\", "/")
    $env:DB_URL = "jdbc:h2:file:${taskDatabasePath};MODE=MySQL;DATABASE_TO_LOWER=TRUE"
}
$env:SERVER_PORT = [string]$Port
Push-Location (Join-Path $taskRepoRoot "backend/spring")
try { & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' }
finally { Pop-Location }
