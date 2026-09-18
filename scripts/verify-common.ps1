function Get-RepoRoot {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Resolve-GradleWrapper {
    param([string]$Root = (Get-RepoRoot))
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
