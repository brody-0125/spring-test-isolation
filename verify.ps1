param([int]$Workers = 2, [switch]$Negative)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $task = @(if ($Negative) { ':verification:cleanupFailureTest' } else { ':runtime:test', ':verification:test' })
    $log = "build/evidence/workers-$Workers-negative-$Negative.log"
    & ./gradlew.bat @task "-Pworkers=$Workers" --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
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
} finally { Pop-Location }
