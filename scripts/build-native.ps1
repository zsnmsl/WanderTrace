param([string]$NdkRoot = $env:ANDROID_NDK_HOME)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($NdkRoot)) { $NdkRoot = $env:ANDROID_NDK_ROOT }
if ([string]::IsNullOrWhiteSpace($NdkRoot)) { throw 'Specify -NdkRoot or set ANDROID_NDK_HOME to an Android NDK r27d installation.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ndk = (Resolve-Path -LiteralPath $NdkRoot).Path
$buildDir = Join-Path $projectRoot 'native\build-release'
& cmake -S (Join-Path $projectRoot 'native') -B $buildDir -G Ninja "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release -DWANDERTRACE_BUILD_CHECKS=ON
if ($LASTEXITCODE -ne 0) { throw 'CMake configure failed' }
& cmake --build $buildDir --target wander_native wander_native_checks wander_device_abi_check -j 6
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
$jniDir = Join-Path $projectRoot 'app\src\main\jniLibs\arm64-v8a'
New-Item -ItemType Directory -Force -Path $jniDir | Out-Null
Copy-Item -LiteralPath (Join-Path $buildDir 'libwander_native.so') -Destination $jniDir
& "$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe" --strip-unneeded (Join-Path $jniDir 'libwander_native.so')
if ($LASTEXITCODE -ne 0) { throw 'Native strip failed' }
