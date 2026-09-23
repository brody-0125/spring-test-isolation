param(
    [string]$Version = $(Get-Content "$PSScriptRoot\..\gradle.properties" | Where-Object { $_ -match '^version=' } | ForEach-Object { $_.Split('=')[1] })
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\.."
Push-Location $root
try {
    ./gradlew :runtime:publishToMavenLocal :maven-plugin:publishToMavenLocal -Pversion="$Version" --no-daemon
    $evidence = Join-Path $root "build\evidence\verify-maven"
    New-Item -ItemType Directory -Force -Path $evidence | Out-Null
    $log = Join-Path $evidence "mvn-test.log"
    Push-Location (Join-Path $root "verification-maven")
    mvn -q -Dspring-test-isolation.version="$Version" test *> $log
    if ($LASTEXITCODE -ne 0) {
        Get-Content $log
        throw "Maven verification failed (see $log)"
    }
    Write-Host "Maven verification PASS (log: $log)"
} finally {
    Pop-Location
    Pop-Location
}
