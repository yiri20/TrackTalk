param(
    [string]$Device,
    [string]$WirelessAddress,
    [string]$PairingAddress,
    [string]$PairingCode,
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$PackageName = "com.trackvoice",
    [switch]$Build,
    [switch]$Launch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Resolve-AdbPath {
    $adbCandidates = [System.Collections.Generic.List[string]]::new()

    if ($env:ANDROID_SDK_ROOT) {
        $adbCandidates.Add((Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"))
    }
    if ($env:ANDROID_HOME) {
        $adbCandidates.Add((Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"))
    }
    if ($env:LOCALAPPDATA) {
        $adbCandidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"))
    }

    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adbCandidates.Insert(0, $adbCommand.Source)
    }

    foreach ($adbCandidate in $adbCandidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $adbCandidate) {
            return (Resolve-Path -LiteralPath $adbCandidate).Path
        }
    }

    throw "adb.exe를 찾지 못했습니다. Android SDK platform-tools를 설치하거나 ANDROID_SDK_ROOT를 설정하세요."
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & $script:adbPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB 명령이 실패했습니다: adb $($Arguments -join ' ')"
    }
}

$adbPath = Resolve-AdbPath

if ($PairingAddress) {
    if (-not $PairingCode) {
        throw "-PairingAddress를 사용할 때는 -PairingCode도 입력해야 합니다."
    }

    Write-Host "무선 디버깅 페어링: $PairingAddress"
    Invoke-Adb pair $PairingAddress $PairingCode
}

if ($WirelessAddress) {
    Write-Host "무선 ADB 연결: $WirelessAddress"
    Invoke-Adb connect $WirelessAddress
    if (-not $Device) {
        $Device = $WirelessAddress
    }
}

$resolvedApkPath = if ([System.IO.Path]::IsPathRooted($ApkPath)) {
    $ApkPath
} else {
    Join-Path $projectRoot $ApkPath
}

if ($Build) {
    Write-Host "Debug APK 빌드 중..."
    & (Join-Path $projectRoot "gradlew.bat") --no-daemon --console=plain :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Debug APK 빌드에 실패했습니다."
    }
}

if (-not (Test-Path -LiteralPath $resolvedApkPath)) {
    throw "APK를 찾지 못했습니다: $resolvedApkPath`n먼저 gradlew.bat :app:assembleDebug를 실행하거나 -Build를 사용하세요."
}
$resolvedApkPath = (Resolve-Path -LiteralPath $resolvedApkPath).Path

if (-not $Device) {
    $connectedDevices = @(
        (& $adbPath devices) |
            ForEach-Object {
                if ($_ -match '^(.+?)\s+device(?:\s|$)') {
                    $Matches[1].Trim()
                }
            }
    )

    if ($connectedDevices.Count -eq 0) {
        throw "연결된 Android 기기가 없습니다. USB 디버깅 또는 무선 디버깅을 켜고 다시 시도하세요."
    }
    if ($connectedDevices.Count -gt 1) {
        throw "기기가 여러 대 연결되어 있습니다: $($connectedDevices -join ', ')`n-Device로 대상 serial을 지정하세요."
    }
    $Device = $connectedDevices[0]
}

Write-Host "APK 설치: $resolvedApkPath -> $Device"
Invoke-Adb -Arguments @("-s", $Device, "install", "-r", $resolvedApkPath)

if ($Launch) {
    Write-Host "앱 실행: $PackageName"
    Invoke-Adb -Arguments @("-s", $Device, "shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1")
}

Write-Host "원격 APK 설치가 완료되었습니다."
