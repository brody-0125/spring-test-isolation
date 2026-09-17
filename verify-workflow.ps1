param([ValidateSet(1,2,4)][int]$Workers = 2, [ValidateRange(1,20)][int]$Runs = 1)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    for ($run = 1; $run -le $Runs; $run++) {
        $log = "build/evidence/workflow-$Workers-run-$run.log"
        $ErrorActionPreference = 'Continue'
        & ./gradlew.bat :verification:workflowTest "-Pworkers=$Workers" --rerun-tasks --console=plain *> $log
        $workflowCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($workflowCode -ne 0) { throw "Workflow integration failed; inspect $log" }
        $text = Get-Content -Raw $log
        if ($text -match 'Invocation of (close|destroy) method failed|Worker storage cleanup failed') { throw 'Lifecycle cleanup warning was swallowed by the framework' }
        foreach ($pattern in @('PTK infrastructure-start ', 'PTK infrastructure-closed')) {
            if ([regex]::Matches($text, $pattern).Count -ne 1) { throw "Infrastructure lifecycle mismatch: $pattern" }
        }
        foreach ($pattern in @('WORKFLOW verified ', 'WORKFLOW drained ', 'WORKFLOW audited ', 'PTK class-clean worker=\d+ class=workflow\.')) {
            if ([regex]::Matches($text, $pattern).Count -ne 4) { throw "Expected four completed boundaries: $pattern" }
        }
        if ([regex]::Matches($text, 'WORKFLOW destroyed ').Count -ne $Workers) { throw 'Expected one successful context destruction per worker' }
        if ([regex]::Matches($text, "WORKFLOW verified worker=\d+ peers=$Workers cleaner=").Count -ne 4) {
            throw 'Barrier did not prove the requested cross-worker concurrency'
        }
        $starts = [regex]::Matches($text, 'WORKFLOW start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)')
        $ends = [regex]::Matches($text, 'WORKFLOW end time=(\d+) worker=(\d+) class=(\w+)')
        if ($starts.Count -ne 4 -or $ends.Count -ne 4) { throw 'Expected four workflow classes' }
        $events = foreach ($start in $starts) {
            $end = @($ends | Where-Object { $_.Groups[3].Value -eq $start.Groups[3].Value })
            if ($end.Count -ne 1) { throw 'Missing or duplicate class completion' }
            [pscustomobject]@{ Class=$start.Groups[3].Value; Worker=$start.Groups[2].Value; Context=$start.Groups[4].Value; Start=[long]$start.Groups[1].Value; End=[long]$end[0].Groups[1].Value }
        }
        if (@($events.Worker | Select-Object -Unique).Count -ne $Workers) { throw 'Unexpected worker count' }
        foreach ($a in $events) { foreach ($b in $events) {
            if ($a.Class -ne $b.Class -and $a.Worker -eq $b.Worker -and $a.Start -lt $b.End -and $b.Start -lt $a.End) {
                throw 'Concurrent classes inside a worker'
            }
        } }
        if ($Workers -lt 4 -and !($events | Group-Object Worker,Context | Where-Object Count -gt 1)) { throw 'Context reuse was not observed' }
        $count = 0
        foreach ($file in Get-ChildItem 'verification/build/test-results/workflowTest/TEST-*.xml') {
            [xml]$report = Get-Content -Raw $file
            if ([int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0) { throw "Failed or skipped test in $file" }
            $count += [int]$report.testsuite.tests
        }
        if ($count -ne 4) { throw "Expected four successful tests, got $count" }
        $infra = [regex]::Match($text, 'PTK infrastructure-start postgres=(\w+) redis=(\w+)')
        $remaining = @(& docker ps -aq --no-trunc)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot verify container removal' }
        if ($remaining -contains $infra.Groups[1].Value -or $remaining -contains $infra.Groups[2].Value) { throw 'Build containers leaked' }
        $events | ConvertTo-Json | Set-Content "build/evidence/workflow-$Workers-run-$run-events.json"
        Write-Output "PASS: workflow workers=$Workers run=$run; transactions, retry, deduplication, local reset, late work, lifecycle."
    }
} finally { Pop-Location }
