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
# Private optional question-bank settings; absent configuration uses bounded AI quizzes.
$taskBankSettings = Join-Path $taskRepoRoot 'private/studyroom-question-bank/local-settings.json'
if (Test-Path -LiteralPath $taskBankSettings) {
    $taskBank = Get-Content -LiteralPath $taskBankSettings -Raw | ConvertFrom-Json
    if (-not $env:STUDYROOM_QUESTION_BANK_PATH) { $env:STUDYROOM_QUESTION_BANK_PATH = $taskBank.path }
    if (-not $env:STUDYROOM_QUESTION_BANK_BINDINGS_PATH) { $env:STUDYROOM_QUESTION_BANK_BINDINGS_PATH = $taskBank.bindingsPath }
    if (-not $env:STUDYROOM_QUESTION_BANK_ALLOW_DRAFT -and $taskBank.allowDraft -eq $true) { $env:STUDYROOM_QUESTION_BANK_ALLOW_DRAFT = 'true' }
}
$taskCoursewareRoot = Join-Path $taskRepoRoot 'private/courseware-latest'
if (Test-Path -LiteralPath (Join-Path $taskCoursewareRoot '知识库/转换清单.json')) {
    if (-not $env:KNOWLEDGE_DIR) { $env:KNOWLEDGE_DIR = Join-Path $taskCoursewareRoot '知识库' }
    if (-not $env:PRESENTATION_DIR) { $env:PRESENTATION_DIR = Join-Path $taskCoursewareRoot 'WebP' }
    if (-not $env:PRESENTATION_IMAGE_DIR) { $env:PRESENTATION_IMAGE_DIR = Join-Path $taskCoursewareRoot 'WebP' }
}
Push-Location (Join-Path $taskRepoRoot "backend/spring")
try { & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' }
finally { Pop-Location }
