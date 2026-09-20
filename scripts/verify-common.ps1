function Get-RepoRoot {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Publish-RuntimeToMavenLocal {
    param([string]$Root = (Get-RepoRoot))
    $gradlew = Resolve-GradleWrapper -Root $Root
    $version = (Select-String -Path (Join-Path $Root 'gradle.properties') -Pattern '^version=').Line.Split('=')[1]
    $repo = Join-Path $env:USERPROFILE ".m2\repository\io\github\brody-0125\spring-test-isolation-runtime\$version"
    if (Test-Path $repo) { Remove-Item -Recurse -Force $repo }
    & $gradlew :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'runtime publishToMavenLocal failed' }
}

function Resolve-GradleWrapper {
    param([string]$Root = (Get-RepoRoot))
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
