param([int]$Workers = 2)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'verify-common.ps1')
$RepoRoot = Get-RepoRoot
Push-Location $RepoRoot
$gradlew = Resolve-GradleWrapper -Root $RepoRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $log = "build/evidence/mysql-workers-$Workers.log"
    $guardLog = "build/evidence/mysql-connectionGuardTest.log"
    $ErrorActionPreference = 'Continue'
    & $gradlew :runtime:test `
        :verification:test --tests 'example.FlywaySmokeTest' --tests 'example.MysqlStorageSmokeTest' `
        "-PjdbcBackend=mysql" "-Pworkers=$Workers" --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "Gradle failed; inspect $log" }
    & $gradlew :verification:connectionGuardTest "-PjdbcBackend=mysql" "-Pworkers=1" --rerun-tasks --console=plain *> $guardLog
    $guardCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $text = Get-Content -Raw $log
    $guardText = Get-Content -Raw $guardLog
    if ([regex]::Matches($text, 'PTK infrastructure-start ').Count -ne 1 -or [regex]::Matches($text, 'PTK infrastructure-closed').Count -ne 1) {
        throw 'Invalid shared infrastructure lifecycle'
    }
    if ($text -notmatch 'jdbc\.backend=mysql') { throw 'MySQL backend was not used' }
    if ($guardCode -eq 0 -or $guardText -notmatch 'DataSource bypasses worker database' -or $guardText -match 'FORBIDDEN_CONNECTION_BODY') {
        throw "Connection guard contract failed; inspect $guardLog"
    }
    Write-Output "PASS: MySQL jdbcBackend workers=$Workers; flyway smoke, storage reset, connection guard."
} finally { Pop-Location }
exit 0
