# Builds the Windows installer for Advanced Player: desktop\installer\dist\setup.exe
#
# Steps:
#   1. Publish the app as a self-contained 64-bit build, so the PC does not need .NET installed.
#   2. Zip the app folder into payload.zip.
#   3. Publish the installer (desktop\installer\Setup) with payload.zip embedded. The result is one setup.exe.
#
# Requires the .NET 10 SDK. Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File desktop\installer\build-installer.ps1

[CmdletBinding()]
param(
    [string]$Version = "1.3.0"
)

$ErrorActionPreference = "Stop"

$installerDir = $PSScriptRoot
$desktopDir = Split-Path -Parent $installerDir
$appProject = Join-Path $desktopDir "AdvancedPlayer.Desktop"
$setupProject = Join-Path $installerDir "Setup"
$distDir = Join-Path $installerDir "dist"
$appDir = Join-Path $distDir "app"
$payload = Join-Path $distDir "payload.zip"
$setupOut = Join-Path $distDir "setup-out"

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
foreach ($path in @($appDir, $payload, $setupOut)) {
    if (Test-Path $path) { Remove-Item $path -Recurse -Force }
}

# 1. The app, self-contained.
dotnet publish $appProject -c Release -r win-x64 --self-contained true -p:Version=$Version -o $appDir
if ($LASTEXITCODE -ne 0) { throw "Publishing the app failed." }

# 2. The payload: the app folder as a zip, with the files at the top level.
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($appDir, $payload)

# 3. The installer, with the payload embedded.
dotnet publish $setupProject -c Release -r win-x64 --self-contained true -p:Version=$Version -o $setupOut
if ($LASTEXITCODE -ne 0) { throw "Publishing the installer failed." }

Copy-Item (Join-Path $setupOut "setup.exe") (Join-Path $distDir "setup.exe") -Force
Remove-Item $setupOut -Recurse -Force

$sizeMb = [math]::Round((Get-Item (Join-Path $distDir "setup.exe")).Length / 1MB, 1)
Write-Host "Installer: $(Join-Path $distDir 'setup.exe') ($sizeMb MB)"
