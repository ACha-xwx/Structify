param([ValidateRange(1024,65535)][int]$Port = 3030)
$ErrorActionPreference = 'Stop'
$referenceRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../OpenMAIC'))
if (-not (Test-Path (Join-Path $referenceRepo '.env.local'))) {
    throw 'OpenMAIC local configuration is missing; configure its ignored .env.local first.'
}
if (-not (Test-Path (Join-Path $referenceRepo 'node_modules/next/dist/bin/next'))) {
    throw 'OpenMAIC dependencies are missing.'
}
Push-Location $referenceRepo
try {
    & docker compose --env-file .env.local -p openmaic-structify-demo -f docker-compose.db.yml up -d --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'OpenMAIC PostgreSQL did not become healthy. Check Docker Desktop.' }
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        Write-Output "A service is already listening at http://127.0.0.1:$Port; no second server was started."
        return
    }
    Write-Output "OpenMAIC reference: http://127.0.0.1:$Port"
    & node node_modules/next/dist/bin/next dev --hostname 127.0.0.1 --port $Port
    if ($LASTEXITCODE -ne 0) { throw 'OpenMAIC development server stopped with an error.' }
}
finally { Pop-Location }
