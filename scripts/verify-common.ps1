function Get-RepoRoot {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Publish-RuntimeToMavenLocal {
    param([string]$Root = (Get-RepoRoot))
    $gradlew = Resolve-GradleWrapper -Root $Root
    $version = (Select-String -Path (Join-Path $Root 'gradle.properties') -Pattern '^version=').Line.Split('=')[1]
    $m2 = Join-Path $env:USERPROFILE ".m2\repository\io\github\brody-0125"
    @(
        "spring-test-isolation-runtime\$version",
        "spring-test-isolation-descriptor\$version"
    ) | ForEach-Object {
        $path = Join-Path $m2 $_
        if (Test-Path $path) { Remove-Item -Recurse -Force $path }
    }
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    @(
        'spring-test-isolation-runtime',
        'spring-test-isolation-descriptor'
    ) | ForEach-Object {
        $gradleCache = Join-Path $gradleHome "caches\modules-2\files-2.1\io.github.brody-0125\$_"
        if (Test-Path $gradleCache) { Remove-Item -Recurse -Force $gradleCache }
    }
    & $gradlew :descriptor:clean :descriptor:publishToMavenLocal :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'runtime publishToMavenLocal failed' }
}

function Resolve-GradleWrapper {
    param([string]$Root = (Get-RepoRoot))
    $name = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
    Join-Path $Root $name
}
