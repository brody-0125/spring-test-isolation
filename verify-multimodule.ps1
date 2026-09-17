$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $log = 'build/evidence/multimodule.log'
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat :verification:test :verification-peer:test --parallel --max-workers=4 --rerun-tasks --console=plain *> $log
    $multimoduleCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($multimoduleCode -ne 0) { throw "Multimodule tests failed; inspect $log" }
    $text = Get-Content -Raw $log
    if ([regex]::Matches($text, 'PTK infrastructure-start ').Count -ne 1 -or [regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) {
        throw 'Expected exactly one shared infrastructure lifecycle across both modules'
    }
    foreach ($module in @('verification','verification-peer')) {
        $total = 0
        foreach ($file in Get-ChildItem "$module/build/test-results/test/TEST-*.xml") {
            [xml]$report = Get-Content -Raw $file
            if ([int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0) { throw "Failure in $file" }
            $total += [int]$report.testsuite.tests
        }
        if ($total -ne 13) { throw "Expected thirteen tests for $module; got $total" }
    }
    Write-Output 'PASS: both modules share one container pair; 26 tests passed.'
} finally { Pop-Location }
