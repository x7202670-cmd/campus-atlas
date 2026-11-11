param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleRelease'))
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$workspace = Split-Path $projectRoot -Parent

$toolsDrive = $null
$projectDrive = $null

# Avoid non-ASCII path issues with Windows worker argument files
if ($workspace -match '[^\x00-\x7F]') {
    $letters = @('S','T','U','V','W','X','Y','Z')
    foreach ($l in $letters) {
        if (!(Test-Path ($l + ':\'))) {
            if (!$toolsDrive) {
                & subst.exe ($l + ':') $workspace
                $toolsDrive = $l + ':'
            } elseif (!$projectDrive) {
                & subst.exe ($l + ':') $projectRoot
                $projectDrive = $l + ':'
                break
            }
        }
    }
}

$toolsBase = if ($toolsDrive) { $toolsDrive } else { $workspace }
$runDir = if ($projectDrive) { $projectDrive + '\' } else { $projectRoot }

$env:JAVA_HOME = Join-Path $toolsBase '.tools/jdk21/jdk-21.0.12.1+1'
$env:ANDROID_HOME = Join-Path $toolsBase '.tools/android-sdk'
$env:GRADLE_USER_HOME = Join-Path $toolsBase '.tools/gradle-cache'
$env:ATLAS_ROBOLECTRIC_CACHE = Join-Path $toolsBase '.tools/robolectric-cache'
$env:ANDROID_USER_HOME = Join-Path $toolsBase '.tools/android-user'

$gradle = Join-Path $toolsBase '.tools/gradle/gradle-8.11.1/bin/gradle.bat'
if (!(Test-Path $gradle)) { throw '缺少 Gradle 8.11.1' }

Push-Location $runDir
try {
    # Ensure AMAP_API_KEY is empty for open-source release build
    & $gradle --no-daemon --console=plain -PAMAP_API_KEY="" @Tasks
    if ($LASTEXITCODE -ne 0) { throw "构建失败：$LASTEXITCODE" }
} finally {
    Pop-Location
    if ($projectDrive) { & subst.exe $projectDrive /D }
    if ($toolsDrive) { & subst.exe $toolsDrive /D }
}
