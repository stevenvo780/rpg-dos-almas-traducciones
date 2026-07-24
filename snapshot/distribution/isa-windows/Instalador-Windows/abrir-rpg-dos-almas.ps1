$ErrorActionPreference = "SilentlyContinue"
$env:_JAVA_OPTIONS = (($env:_JAVA_OPTIONS + " -Djava.net.preferIPv4Stack=true").Trim())

function Show-FriendlyMessage([string]$Message, [string]$Title) {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show(
        $Message,
        $Title,
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information
    ) | Out-Null
}

$shortcutRoots = @(
    (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"),
    (Join-Path $env:ProgramData "Microsoft\Windows\Start Menu\Programs"),
    ([Environment]::GetFolderPath("Desktop")),
    (Join-Path $env:PUBLIC "Desktop")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

foreach ($root in $shortcutRoots) {
    $link = Get-ChildItem -LiteralPath $root -Filter "*TLauncher*.lnk" -File -Recurse |
        Select-Object -First 1
    if ($link) {
        Start-Process -FilePath $link.FullName
        exit 0
    }
}

$exeCandidates = @(
    (Join-Path $env:LOCALAPPDATA "Programs\TLauncher\TLauncher.exe"),
    (Join-Path $env:APPDATA ".tlauncher\TLauncher.exe"),
    (Join-Path $env:ProgramFiles "TLauncher\TLauncher.exe"),
    (Join-Path $env:USERPROFILE "Downloads\TLauncher.exe")
)

foreach ($candidate in $exeCandidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        Start-Process -FilePath $candidate
        exit 0
    }
}

$downloads = Join-Path $env:USERPROFILE "Downloads"
$jarCandidates = @(
    (Join-Path $downloads "TLauncher.jar"),
    (Join-Path $downloads "TLauncher-2.9.jar")
)

foreach ($jar in $jarCandidates) {
    if (Test-Path -LiteralPath $jar -PathType Leaf) {
        $java = Get-Command "javaw.exe" -ErrorAction SilentlyContinue
        if (-not $java) {
            $java = Get-Command "java.exe" -ErrorAction SilentlyContinue
        }
        if ($java) {
            Start-Process -FilePath $java.Source -ArgumentList "-jar", "`"$jar`""
            exit 0
        }
    }
}

$guide = Join-Path $env:APPDATA ".minecraft-rpg-dos-almas\COMO-JUGAR.txt"
Show-FriendlyMessage `
    "El modpack ya esta instalado, pero no encontre TLauncher. Abre TLauncher manualmente y selecciona Forge 1.20.1. Se abriran las instrucciones." `
    "RPG Dos Almas"

if (Test-Path -LiteralPath $guide) {
    Start-Process -FilePath "notepad.exe" -ArgumentList "`"$guide`""
}
