param([int]$Workers = 2, [switch]$Negative)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'verify-common.ps1')
$RepoRoot = Get-RepoRoot
Push-Location $RepoRoot
$gradlew = Resolve-GradleWrapper -Root $RepoRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $task = @(if ($Negative) { ':verification:cleanupFailureTest' } else { ':runtime:test', ':verification:test' })
    $log = "build/evidence/workers-$Workers-negative-$Negative.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew @task "-Pworkers=$Workers" --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $text = Get-Content -Raw $log
    if ($Negative) {
        if ($Workers -ne 1) { throw 'Cleanup poison verification requires -Workers 1' }
        if ($code -eq 0 -or $text -notmatch 'INJECTED_CLEANUP_FAILURE' -or $text -notmatch 'NEGATIVE_BODY CleanupATest' -or $text -match 'NEGATIVE_BODY CleanupBTest') {
            throw "Cleanup poison contract failed; inspect $log"
        }
        if ([regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) { throw 'Infrastructure did not close after cleanup failure' }
        Write-Output 'PASS: injected cleanup failed and later class body did not execute.'
        return
    }
    if ($code -ne 0) { throw "Gradle failed; inspect $log" }
    if ([regex]::Matches($text, 'PTK infrastructure-start ').Count -ne 1 -or [regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) { throw 'Invalid shared infrastructure lifecycle' }
    $starts = [regex]::Matches($text, 'EVIDENCE start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)')
    $ends = [regex]::Matches($text, 'EVIDENCE end time=(\d+) worker=(\d+) class=(\w+)')
    if ($starts.Count -ne 6 -or $ends.Count -ne 6) { throw 'Expected six completed storage classes' }
    $events = foreach ($start in $starts) {
        $end = $ends | Where-Object { $_.Groups[3].Value -eq $start.Groups[3].Value }
        [pscustomobject]@{ Class=$start.Groups[3].Value; Worker=$start.Groups[2].Value; Context=$start.Groups[4].Value; Start=[long]$start.Groups[1].Value; End=[long]$end.Groups[1].Value }
    }
    $overlap = $false
    foreach ($a in $events) { foreach ($b in $events) {
        if ($a.Class -eq $b.Class) { continue }
        if ($a.Start -lt $b.End -and $b.Start -lt $a.End) {
            if ($a.Worker -eq $b.Worker) { throw 'Concurrent test bodies inside a worker' }
            $overlap = $true
        }
    } }
    if ($Workers -gt 1 -and !$overlap) { throw 'No measured overlap across workers' }
    $reused = $events | Group-Object Worker,Context | Where-Object Count -gt 1
    if (!$reused) { throw 'No demonstrated Context reuse' }
    $events | ConvertTo-Json | Set-Content "build/evidence/workers-$Workers-events.json"
    Write-Output "PASS: storage scenarios, internal serial execution, Context reuse; cross-worker overlap=$overlap"
    $testngLog = "build/evidence/workers-$Workers-testng.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew :verification:testngSmoke "-Pworkers=$Workers" --rerun-tasks --console=plain *> $testngLog
    $testngCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $testngText = Get-Content -Raw $testngLog
    if ($testngCode -ne 0 -or $testngText -notmatch 'EVIDENCE start' -or $testngText -notmatch 'PTK class-clean') {
        throw "TestNG storage smoke failed; inspect $testngLog"
    }
    Write-Output 'PASS: TestNG native worker storage smoke'
    $kotestLog = "build/evidence/workers-$Workers-kotest.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew :verification:kotestSmoke "-Pworkers=$Workers" --rerun-tasks --console=plain *> $kotestLog
    $kotestCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $kotestText = Get-Content -Raw $kotestLog
    if ($kotestCode -ne 0 -or $kotestText -notmatch 'PTK class-clean' -or $kotestText -notmatch 'Auto-closing context after') {
        throw "Kotest worker isolation failed; inspect $kotestLog"
    }
    $kotestStarts = [regex]::Matches($kotestText, 'EVIDENCE start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)')
    $kotestEnds = [regex]::Matches($kotestText, 'EVIDENCE end time=(\d+) worker=(\d+) class=(\w+)')
    if ($kotestStarts.Count -ne 4 -or $kotestEnds.Count -ne 4) { throw 'Expected four completed Kotest specs' }
    $kotestEvents = foreach ($start in $kotestStarts) {
        $end = $kotestEnds | Where-Object { $_.Groups[3].Value -eq $start.Groups[3].Value }
        [pscustomobject]@{ Class=$start.Groups[3].Value; Worker=$start.Groups[2].Value; Context=$start.Groups[4].Value; Start=[long]$start.Groups[1].Value; End=[long]$end.Groups[1].Value }
    }
    foreach ($a in $kotestEvents) { foreach ($b in $kotestEvents) {
        if ($a.Class -eq $b.Class) { continue }
        if ($a.Start -lt $b.End -and $b.Start -lt $a.End -and $a.Worker -eq $b.Worker) {
            throw 'Concurrent Kotest spec bodies inside a worker'
        }
    } }
    if (-not ($kotestEvents | Group-Object Worker,Context | Where-Object Count -gt 1)) {
        throw 'No demonstrated Kotest Context reuse'
    }
    Write-Output 'PASS: Kotest storage, Smart Context close, serial specs inside each worker'
} finally { Pop-Location }
