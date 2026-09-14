param([switch]$Download)
$ErrorActionPreference = 'Stop'
$javaRuntime = 'C:\Program Files\Eclipse Adoptium\jre-21.0.10.7-hotspot\bin\java.exe'
if (!(Test-Path -LiteralPath $javaRuntime)) { $javaRuntime = 'java' }
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolDir = Join-Path $repoRoot '.tools/kotlin'
$buildDir = Join-Path $repoRoot 'build/sdk-jvm'
New-Item -ItemType Directory -Force -Path $toolDir, $buildDir | Out-Null
$coordinates = @(
 'org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.22/kotlin-compiler-embeddable-1.9.22.jar',
 'org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar',
 'org/jetbrains/kotlin/kotlin-script-runtime/1.9.22/kotlin-script-runtime-1.9.22.jar',
 'org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar',
 'org/jetbrains/kotlin/kotlin-daemon-embeddable/1.9.22/kotlin-daemon-embeddable-1.9.22.jar',
 'org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar',
 'org/jetbrains/annotations/13.0/annotations-13.0.jar',
 'com/google/code/gson/gson/2.10.1/gson-2.10.1.jar',
 'org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar',
 'org/jetbrains/kotlinx/kotlinx-coroutines-test-jvm/1.7.3/kotlinx-coroutines-test-jvm-1.7.3.jar',
 'com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar',
 'com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar',
 'junit/junit/4.13.2/junit-4.13.2.jar',
 'org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar'
)
foreach ($coordinate in $coordinates) {
    $destination = Join-Path $toolDir (Split-Path -Leaf $coordinate)
    if (!(Test-Path -LiteralPath $destination)) {
        if (!$Download) { throw "Missing dependency. Run scripts/test_sdk.ps1 -Download" }
        Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/$coordinate" -OutFile $destination
    }
}
$classpath = (Get-ChildItem -LiteralPath $toolDir -Filter '*.jar').FullName -join ';'
$sources = @((Get-ChildItem (Join-Path $repoRoot 'packages/android-sdk/src/main') -Recurse -Filter '*.kt' | Where-Object Name -ne 'MemoryCpuProfiler.kt').FullName)
$sources += @((Get-ChildItem (Join-Path $repoRoot 'packages/android-sdk/src/test') -Recurse -Filter '*.kt').FullName)
& $javaRuntime -cp $classpath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $classpath -d $buildDir @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$resources = Join-Path $repoRoot 'packages/android-sdk/src/main/resources'
$testResources = Join-Path $repoRoot 'packages/android-sdk/src/test/resources'
$classes = @((Get-ChildItem (Join-Path $repoRoot 'packages/android-sdk/src/test') -Recurse -Filter '*Test.kt').BaseName | ForEach-Object { "com.vyuha.sdk.$_" })
& $javaRuntime -cp "$classpath;$buildDir;$resources;$testResources" org.junit.runner.JUnitCore @classes
exit $LASTEXITCODE
