param([int]$Workers = 2)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
. (Join-Path $PSScriptRoot 'scripts/verify-common.ps1')
$gradlew = Resolve-GradleWrapper -Root $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $log = "build/evidence/config-cache-$Workers.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew :runtime:test :verification:test "-Pworkers=$Workers" --configuration-cache --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw "Configuration cache build failed; inspect $log" }
    $text = Get-Content -Raw $log
    if ($text -notmatch 'Configuration cache entry stored') { throw "Configuration cache was not stored; inspect $log" }
    if ([regex]::Matches($text, 'PTK infrastructure-start ').Count -ne 1 -or [regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) {
        throw 'Invalid shared infrastructure lifecycle under configuration cache'
    }
    Write-Output "PASS: configuration cache stored; workers=$Workers infrastructure lifecycle valid."
} finally { Pop-Location }
