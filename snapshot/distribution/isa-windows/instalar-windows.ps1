$ErrorActionPreference = "Stop"
$PackageDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$GameDir = Join-Path $env:APPDATA ".minecraft-rpg-dos-almas"
$TLauncherDir = Join-Path $env:APPDATA ".tlauncher"
$Properties = Join-Path $TLauncherDir "tlauncher-2.0.properties"

Write-Host "Instalando RPG Dos Almas para TLauncher..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $GameDir, $TLauncherDir | Out-Null

if (Test-Path $Properties) {
    Copy-Item $Properties "$Properties.backup-rpg-dos-almas" -Force
}

Copy-Item (Join-Path $PackageDir "pack\*") $GameDir -Recurse -Force

$TotalRamGB = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
if ($TotalRamGB -ge 24) { $MemoryMB = 8192 }
elseif ($TotalRamGB -ge 16) { $MemoryMB = 6144 }
elseif ($TotalRamGB -ge 12) { $MemoryMB = 5120 }
else { $MemoryMB = 4096 }

if (Test-Path $Properties) {
    $Lines = [System.Collections.Generic.List[string]](Get-Content $Properties)
} else {
    $Lines = [System.Collections.Generic.List[string]]::new()
    $Lines.Add("# TLauncher configuration file")
}

function Set-TLProperty([string]$Name, [string]$Value) {
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match "^$([regex]::Escape($Name))=") {
            $Lines[$i] = "$Name=$Value"
            return
        }
    }
    $Lines.Add("$Name=$Value")
}

$GamePath = $GameDir.Replace('\', '/')
Set-TLProperty "minecraft.gamedir" $GamePath
Set-TLProperty "minecraft.memory.ram3" "$MemoryMB"
Set-TLProperty "login.version.game" "Forge 1.20.1"
Set-TLProperty "minecraft.versions.modified" "true"
$Lines | Set-Content -Path $Properties -Encoding UTF8

$ServerInfo = Join-Path $GameDir "SERVIDOR-RPG-DOS-ALMAS.txt"
@"
Servidor: minecraft.stevenvallejo.com
Version: Forge 1.20.1

Abre TLauncher, confirma que diga Forge 1.20.1 y pulsa Entrar al juego.
En Multijugador usa la direccion indicada arriba.
"@ | Set-Content $ServerInfo -Encoding UTF8

$Shell = New-Object -ComObject WScript.Shell
$Shortcut = $Shell.CreateShortcut((Join-Path ([Environment]::GetFolderPath('Desktop')) "RPG Dos Almas.lnk"))
$Shortcut.TargetPath = $GameDir
$Shortcut.Description = "Carpeta de RPG Dos Almas para TLauncher"
$Shortcut.Save()

Write-Host ""
Write-Host "INSTALACION COMPLETADA" -ForegroundColor Green
Write-Host "RAM detectada: $TotalRamGB GB; asignada a Minecraft: $([math]::Round($MemoryMB / 1024)) GB"
Write-Host "Ahora abre TLauncher y pulsa Entrar al juego con Forge 1.20.1."
Write-Host "Servidor: minecraft.stevenvallejo.com"
Read-Host "Pulsa Enter para cerrar"
