function Resolve-GradleWrapper {
    param([string]$Root = $PSScriptRoot)
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
