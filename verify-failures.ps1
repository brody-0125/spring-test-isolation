$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    & ./verify.ps1 -Workers 1 -Negative
    foreach ($case in @(
        @{ Task='boundaryTimeoutTest'; Required='Boundary hook timed out: QUIESCE'; Forbidden='NEGATIVE_BODY CleanupBTest' },
        @{ Task='guardFailureTest'; Required='@Execution\(CONCURRENT\) is incompatible'; Forbidden='FORBIDDEN_GUARD_BODY_EXECUTED' },
        @{ Task='missingListenerTest'; Required='Required listener missing'; Forbidden='FORBIDDEN_MISSING_LISTENER_BODY' },
        @{ Task='connectionGuardTest'; Required='DataSource bypasses worker database'; Forbidden='FORBIDDEN_CONNECTION_BODY|FORBIDDEN_SQL_INITIALIZER' },
        @{ Task='initializationFailureTest'; Required='INJECTED_INITIALIZATION_FAILURE'; Forbidden='FORBIDDEN_INITIALIZATION_BODY' }
    )) {
        $log = "build/evidence/$($case.Task).log"
        & ./gradlew.bat ":verification:$($case.Task)" -Pworkers=1 --rerun-tasks --console=plain *> $log
        $code = $LASTEXITCODE
        $text = Get-Content -Raw $log
        if ($code -eq 0 -or $text -notmatch $case.Required -or $text -match $case.Forbidden) { throw "Failure contract failed: $($case.Task)" }
        if ([regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) { throw "Shared infrastructure did not close: $($case.Task)" }
        Write-Output "PASS: $($case.Task) rejected before forbidden body"
    }
    & ./gradlew.bat :verification:invalidSettingsTest --console=plain *> 'build/evidence/invalidSettingsTest.log'
    $code = $LASTEXITCODE
    $text = Get-Content -Raw 'build/evidence/invalidSettingsTest.log'
    if ($code -eq 0 -or $text -notmatch 'Incompatible test setting' -or $text -match 'EVIDENCE start') { throw 'Invalid settings were not rejected before execution' }
    Write-Output 'PASS: conflicting execution configuration rejected before worker launch'
} finally { Pop-Location }
