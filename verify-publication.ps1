$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force 'build/evidence' | Out-Null
    $log = 'build/evidence/publication-verify.log'
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "Runtime publication failed; inspect $log" }
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat -p plugin clean publishToMavenLocal validatePlugins --rerun-tasks --console=plain *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw "Plugin publication failed; inspect $log" }
    $text = Get-Content -Raw $log
    if ($text -notmatch 'BUILD SUCCESSFUL') { throw 'Gradle did not report success' }
    if ($text -notmatch 'validatePlugins') { throw 'validatePlugins did not run' }
    $version = (Select-String -Path 'gradle.properties' -Pattern '^version=(.+)$').Matches[0].Groups[1].Value
    $m2 = Join-Path $env:USERPROFILE ".m2\repository\io\github\brody-0125"
    $required = @(
        "spring-test-isolation-runtime\$version\spring-test-isolation-runtime-$version.jar",
        "spring-test-isolation-gradle-plugin\$version\spring-test-isolation-gradle-plugin-$version.jar"
    )
    foreach ($rel in $required) {
        if (-not (Test-Path (Join-Path $m2 $rel))) { throw "Missing Maven Local artifact: $rel" }
    }
    $marker = Join-Path $env:USERPROFILE ".m2\repository\io\github\brody-0125\spring-test-isolation\io.github.brody-0125.spring-test-isolation.gradle.plugin\$version"
    if (-not (Test-Path $marker)) { throw "Missing Gradle plugin marker under $marker" }
    $pom = Get-Content -Raw (Join-Path $m2 "spring-test-isolation-runtime\$version\spring-test-isolation-runtime-$version.pom")
    if ($pom -notmatch 'MIT License' -or $pom -notmatch 'Seokhyeon Kim') { throw 'Runtime POM missing license or developer metadata' }
    Write-Output "PASS: Maven Local publication, plugin marker, validatePlugins, POM metadata (version=$version)."
} finally { Pop-Location }
