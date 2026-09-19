param([ValidateSet(1,2,4)][int]$Workers = 2)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'verify-common.ps1')
$RepoRoot = Get-RepoRoot
Push-Location $RepoRoot
$gradlew = Resolve-GradleWrapper -Root $RepoRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $log = "build/evidence/spring-session-$Workers.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew :verification:sessionTest "-Pworkers=$Workers" --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw "Spring Session keyevent fixture failed; inspect $log" }
    $text = Get-Content -Raw $log
    if ($text -match 'SESSION LEAK') { throw 'Session index leakage detected' }
    foreach ($pattern in @('PTK infrastructure-start ', 'PTK infrastructure-closed')) {
        if ([regex]::Matches($text, $pattern).Count -ne 1) { throw "Infrastructure lifecycle mismatch: $pattern" }
    }
    foreach ($pattern in @('SESSION verified ', 'SESSION audited ', 'SESSION indexed ')) {
        if ([regex]::Matches($text, $pattern).Count -ne 4) { throw "Expected four completed session classes: $pattern" }
    }
    $namespaces = [regex]::Matches($text, 'SESSION indexed worker=\d+ namespace=(\S+) session=') | ForEach-Object { $_.Groups[1].Value }
    if (@($namespaces | Select-Object -Unique).Count -ne $Workers) { throw 'Session index namespaces must map one-to-one with workers' }
    if ([regex]::Matches($text, "SESSION verified worker=\d+ peers=$Workers").Count -ne 4) {
        throw 'Barrier did not prove the requested cross-worker concurrency'
    }
    $count = 0
    foreach ($file in Get-ChildItem 'verification/build/test-results/sessionTest/TEST-*.xml') {
        [xml]$report = Get-Content -Raw $file
        if ([int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0) { throw "Failed or skipped test in $file" }
        $count += [int]$report.testsuite.tests
    }
    if ($count -ne 4) { throw "Expected four successful tests, got $count" }
    Write-Output "PASS: Spring Session keyevent ACL fixture workers=$Workers; indexed sessions, keyevents, cleanup audit."
} finally { Pop-Location }
