function Get-RepoRoot {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Publish-RuntimeToMavenLocal {
    param([string]$Root = (Get-RepoRoot))
    $gradlew = Resolve-GradleWrapper -Root $Root
    $version = (Select-String -Path (Join-Path $Root 'gradle.properties') -Pattern '^version=').Line.Split('=')[1]
    $repo = Join-Path $env:USERPROFILE ".m2\repository\io\github\brody-0125\spring-test-isolation-runtime\$version"
    if (Test-Path $repo) { Remove-Item -Recurse -Force $repo }
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $gradleCache = Join-Path $gradleHome 'caches\modules-2\files-2.1\io.github.brody-0125\spring-test-isolation-runtime'
    if (Test-Path $gradleCache) { Remove-Item -Recurse -Force $gradleCache }
    & $gradlew :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'runtime publishToMavenLocal failed' }
}

function Resolve-GradleWrapper {
    param([string]$Root = (Get-RepoRoot))
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
