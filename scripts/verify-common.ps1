function Get-RepoRoot {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Publish-RuntimeToMavenLocal {
    param([string]$Root = (Get-RepoRoot))
    $gradlew = Resolve-GradleWrapper -Root $Root
    & $gradlew :runtime:publishToMavenLocal --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'runtime publishToMavenLocal failed' }
}

function Resolve-GradleWrapper {
    param([string]$Root = (Get-RepoRoot))
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
