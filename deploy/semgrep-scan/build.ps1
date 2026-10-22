param([string]$Image = 'foundry/semgrep-scan:local', [switch]$Start, [ValidateSet("linux/amd64", "linux/arm64")][string]$Platform)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location $PSScriptRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required' }
    $base = Get-Content -LiteralPath 'base-image.json' -Raw | ConvertFrom-Json
    if ($base.reference -notmatch '^semgrep/semgrep@sha256:[0-9a-f]{64}$') { throw 'Expected digest-locked official base image' }
    $env:SEMGREP_BASE_IMAGE = $base.reference
    $env:SCAN_IMAGE = $Image
    & docker version --format '{{.Server.Version}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine unavailable' }
    if (-not $Platform) {
        $arch = & docker version --format '{{.Server.Arch}}'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot detect Docker architecture' }
        $Platform = "linux/$arch"
    }
    if ($Platform -notin $base.platforms) { throw "Unsupported platform: $Platform" }
    $env:SCAN_PLATFORM = $Platform
    & docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose unavailable' }
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration' }
    & docker pull --platform $Platform $base.reference
    if ($LASTEXITCODE -ne 0) { throw 'Base image pull failed' }
    & docker compose build --pull=false semgrep-scan
    if ($LASTEXITCODE -ne 0) { throw 'Service image build failed' }
    Write-Host "Built $Image"
    if ($Start) {
        & docker compose up -d --no-build --pull never
        if ($LASTEXITCODE -ne 0) { throw 'Service startup failed' }
    }
} finally {
    Pop-Location
}
