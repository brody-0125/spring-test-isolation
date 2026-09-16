param([int]$Workers = 2)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $logPath = Join-Path $PSScriptRoot "build/evidence/workers-$Workers-negative-False.log"
    $samplePath = Join-Path $PSScriptRoot "build/evidence/workers-$Workers-memory.json"
    Set-Content $logPath ''
    # Observe only fixture JVMs whose PID and startup are reported in this run's log.
    $observer = Start-Job -ArgumentList $logPath,$samplePath -ScriptBlock {
        param($logPath,$samplePath)
        $samples = [System.Collections.Generic.List[object]]::new()
        $deadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $deadline) {
            if (Test-Path $logPath) {
                $logText = Get-Content -Raw $logPath -ErrorAction SilentlyContinue
                if ([string]::IsNullOrEmpty($logText)) { Start-Sleep -Milliseconds 250; continue }
                $ids = [regex]::Matches([string]$logText, 'Starting \w+ using Java [^\r\n]+ with PID (\d+)') | ForEach-Object { [int]$_.Groups[1].Value } | Sort-Object -Unique
                $processes = @($ids | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
                if ($processes.Count) {
                    $samples.Add([pscustomobject]@{ Time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); Processes=$processes.Count; WorkingSetBytes=($processes | Measure-Object WorkingSet64 -Sum).Sum; PrivateBytes=($processes | Measure-Object PrivateMemorySize64 -Sum).Sum })
                }
                if ($logText -match 'BUILD (SUCCESSFUL|FAILED)') { break }
            }
            Start-Sleep -Milliseconds 250
        }
        $samples | ConvertTo-Json | Set-Content $samplePath
    }
    # Truncate before invoking verify to prevent stale build-completion evidence.
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    try { & ./verify.ps1 -Workers $Workers } finally {
        $timer.Stop()
        Wait-Job $observer -Timeout 10 | Out-Null
        Receive-Job $observer | Out-Null
        if ($observer.State -eq 'Running') { Stop-Job $observer }
        Remove-Job $observer
    }
    Write-Output "Wall clock including Gradle and container startup: $($timer.Elapsed.TotalSeconds) seconds"
} finally { Pop-Location }
