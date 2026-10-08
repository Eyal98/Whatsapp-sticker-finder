param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'status',
    [switch]$Headless
)

$ErrorActionPreference = 'Stop'
$root = Join-Path (Split-Path -Parent $PSScriptRoot) '.qa-android'
$env:ANDROID_SDK_ROOT = Join-Path $root 'sdk'
$env:ANDROID_AVD_HOME = Join-Path $root 'avd'
$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
$emulator = Join-Path $env:ANDROID_SDK_ROOT 'emulator\emulator.exe'

if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $emulator)) {
    throw "QA Android SDK is missing from $root"
}

switch ($Action) {
    'start' {
        $arguments = '-avd PeelIt_API34 -no-snapshot -gpu swiftshader_indirect'
        if ($Headless) {
            $arguments += ' -no-window -no-audio'
            $process = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -PassThru
        } else {
            $process = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Normal -PassThru
        }
        Write-Output "Started PeelIt_API34 (PID $($process.Id)). Wait for adb status to report boot_completed=1."
    }
    'stop' {
        & $adb -e emu kill
    }
    'status' {
        $devices = & $adb devices
        $devices
        if ($devices -match 'emulator-\d+\s+device') {
            $boot = & $adb -e shell getprop sys.boot_completed
            Write-Output "boot_completed=$boot"
        } else {
            Write-Output 'Emulator is not ready yet.'
        }
    }
}
