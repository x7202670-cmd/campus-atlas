$ErrorActionPreference = 'Stop'

$ndkRoot = $env:ANDROID_NDK_ROOT
if (-not $ndkRoot) { $ndkRoot = $env:ANDROID_NDK_HOME }
if (-not $ndkRoot) {
    throw 'ANDROID_NDK_ROOT or ANDROID_NDK_HOME environment variable is required to build the native IO probe.'
}

$toolchain = Join-Path $ndkRoot 'toolchains/llvm/prebuilt/windows-x86_64'
if (-not (Test-Path $toolchain)) {
    # Check for linux/darwin host if run on other platforms
    $llvmDir = Join-Path $ndkRoot 'toolchains/llvm/prebuilt'
    $hostDir = Get-ChildItem -Path $llvmDir -Directory | Select-Object -First 1 -ExpandProperty FullName
    if ($hostDir) { $toolchain = $hostDir }
}

$compiler = Join-Path $toolchain 'bin/clang.exe'
if (-not (Test-Path $compiler)) {
    $compiler = Join-Path $toolchain 'bin/clang'
}
if (-not (Test-Path $compiler)) {
    throw "Clang compiler not found under $toolchain"
}

$output = Join-Path $PSScriptRoot 'build/ioProbe/jniLibs/arm64-v8a/libatlas_io_probe.so'
New-Item -ItemType Directory -Path (Split-Path $output -Parent) -Force | Out-Null
& $compiler '--target=aarch64-linux-android26' "--sysroot=$(Join-Path $toolchain 'sysroot')" '-shared' '-fPIC' '-Wall' '-Wextra' '-Werror' '-Wl,-z,max-page-size=16384' (Join-Path $PSScriptRoot 'src/debug/cpp/io_probe.c') '-o' $output
if ($LASTEXITCODE -ne 0) { throw 'Native IO probe compilation failed' }
Write-Output $output
