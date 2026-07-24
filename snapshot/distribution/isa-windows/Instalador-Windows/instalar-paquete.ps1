param(
    [Parameter(Mandatory = $true)]
    [string]$PayloadDir,

    [Parameter(Mandatory = $true)]
    [string]$GameDir
)

$ErrorActionPreference = "Stop"
$LogDir = Join-Path $env:LOCALAPPDATA "RPG Dos Almas"
$LogPath = Join-Path $LogDir "instalacion.log"
$BackupDir = $null
$Properties = $null
$PropertyBackup = $null
$PropertiesExisted = $false
$ItemsToReplace = @(
    "mods",
    "mods-disabled",
    "config",
    "resourcepacks",
    "options.txt",
    "servers.dat"
)

function Write-InstallLog([string]$Message) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -LiteralPath $LogPath -Value "[$timestamp] $Message" -Encoding UTF8
    Write-Output $Message
}

function Restore-PreviousInstall {
    if (-not $BackupDir -or -not (Test-Path -LiteralPath $BackupDir)) {
        return
    }

    Write-InstallLog "Restaurando la instalacion anterior..."
    foreach ($item in $ItemsToReplace) {
        $currentPath = Join-Path $GameDir $item
        $backupPath = Join-Path $BackupDir $item

        if (Test-Path -LiteralPath $currentPath) {
            Remove-Item -LiteralPath $currentPath -Recurse -Force
        }
        if (Test-Path -LiteralPath $backupPath) {
            Move-Item -LiteralPath $backupPath -Destination $currentPath -Force
        }
    }
}

try {
    if (-not (Test-Path -LiteralPath $PayloadDir -PathType Container)) {
        throw "No se encontro el contenido del modpack."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $PayloadDir "mods") -PathType Container)) {
        throw "El paquete esta incompleto: falta la carpeta mods."
    }

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    Write-InstallLog "Iniciando instalacion de RPG Dos Almas."

    $runningGame = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            ($_.Name -eq "java.exe" -or $_.Name -eq "javaw.exe") -and
            $_.CommandLine -like "*minecraft-rpg-dos-almas*"
        } |
        Select-Object -First 1
    if ($runningGame) {
        throw "Minecraft esta abierto. Cierralo y vuelve a ejecutar el instalador."
    }

    $driveRoot = [System.IO.Path]::GetPathRoot($GameDir)
    $driveInfo = [System.IO.DriveInfo]::new($driveRoot)
    if ($driveInfo.AvailableFreeSpace -lt 1GB) {
        throw "Se necesita al menos 1 GB libre adicional para completar la instalacion."
    }

    New-Item -ItemType Directory -Force -Path $GameDir | Out-Null

    $hasPreviousContent = $false
    foreach ($item in $ItemsToReplace) {
        if (Test-Path -LiteralPath (Join-Path $GameDir $item)) {
            $hasPreviousContent = $true
            break
        }
    }

    if ($hasPreviousContent) {
        $backupRoot = Join-Path $env:APPDATA "RPG-Dos-Almas-Respaldos"
        $BackupDir = Join-Path $backupRoot (Get-Date -Format "yyyyMMdd-HHmmss")
        New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
        Write-InstallLog "Guardando la instalacion anterior en $BackupDir"

        foreach ($item in $ItemsToReplace) {
            $sourcePath = Join-Path $GameDir $item
            if (Test-Path -LiteralPath $sourcePath) {
                Move-Item -LiteralPath $sourcePath -Destination $BackupDir -Force
            }
        }
    }

    Write-InstallLog "Copiando mods y configuracion optimizada..."
    Copy-Item -Path (Join-Path $PayloadDir "*") -Destination $GameDir -Recurse -Force

    $TLauncherDir = Join-Path $env:APPDATA ".tlauncher"
    $Properties = Join-Path $TLauncherDir "tlauncher-2.0.properties"
    New-Item -ItemType Directory -Force -Path $TLauncherDir | Out-Null

    if (Test-Path -LiteralPath $Properties) {
        $PropertiesExisted = $true
        $PropertyBackup = "$Properties.backup-rpg-dos-almas"
        Copy-Item -LiteralPath $Properties -Destination $PropertyBackup -Force
    }

    $TotalRamGB = 8
    try {
        $TotalRamGB = [math]::Round(
            (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB
        )
    } catch {
        Write-InstallLog "No se pudo detectar la RAM; se usara un valor seguro."
    }

    if ($TotalRamGB -ge 24) { $MemoryMB = 8192 }
    elseif ($TotalRamGB -ge 16) { $MemoryMB = 6144 }
    elseif ($TotalRamGB -ge 12) { $MemoryMB = 5120 }
    elseif ($TotalRamGB -ge 8) { $MemoryMB = 4096 }
    else { $MemoryMB = 3072 }

    $Lines = New-Object "System.Collections.Generic.List[string]"
    if (Test-Path -LiteralPath $Properties) {
        foreach ($line in Get-Content -LiteralPath $Properties) {
            [void]$Lines.Add([string]$line)
        }
    } else {
        [void]$Lines.Add("# TLauncher configuration file")
    }

    function Set-TLProperty([string]$Name, [string]$Value) {
        for ($i = 0; $i -lt $Lines.Count; $i++) {
            if ($Lines[$i] -match "^$([regex]::Escape($Name))=") {
                $Lines[$i] = "$Name=$Value"
                return
            }
        }
        [void]$Lines.Add("$Name=$Value")
    }

    $GamePath = $GameDir.Replace("\", "/")
    Set-TLProperty "minecraft.gamedir" $GamePath
    Set-TLProperty "minecraft.memory.ram3" "$MemoryMB"
    Set-TLProperty "login.version.game" "Forge 1.20.1"
    Set-TLProperty "minecraft.versions.modified" "true"

    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Properties, $Lines, $Utf8NoBom)

    $ServerInfo = Join-Path $GameDir "SERVIDOR-RPG-DOS-ALMAS.txt"
    $ServerText = @"
RPG DOS ALMAS

Servidor: minecraft.stevenvallejo.com
Version: Forge 1.20.1
Perfil: hiper optimizado para GPU integrada

Abre el acceso RPG Dos Almas del escritorio.
En TLauncher confirma Forge 1.20.1 y pulsa Entrar al juego.
La direccion funciona desde Internet y desde la red local.
"@
    [System.IO.File]::WriteAllText($ServerInfo, $ServerText, $Utf8NoBom)

    $InstallInfo = Join-Path $GameDir "PERFIL-INSTALADO.txt"
    $InstallText = @"
Instalacion completada: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
RAM detectada: $TotalRamGB GB
RAM asignada a Minecraft: $MemoryMB MB
Carpeta de juego: $GameDir
"@
    [System.IO.File]::WriteAllText($InstallInfo, $InstallText, $Utf8NoBom)

    Write-InstallLog "Instalacion completada. RAM asignada: $MemoryMB MB."
    if ($BackupDir) {
        Write-InstallLog "La instalacion anterior quedo respaldada en $BackupDir"
    }
    exit 0
} catch {
    try {
        Write-InstallLog "ERROR: $($_.Exception.Message)"
        Restore-PreviousInstall
        if ($Properties) {
            if ($PropertiesExisted -and
                $PropertyBackup -and
                (Test-Path -LiteralPath $PropertyBackup)) {
                Copy-Item -LiteralPath $PropertyBackup -Destination $Properties -Force
            } elseif (-not $PropertiesExisted -and
                (Test-Path -LiteralPath $Properties)) {
                Remove-Item -LiteralPath $Properties -Force
            }
        }
    } catch {
        Write-Output "No fue posible restaurar automaticamente la instalacion anterior."
    }
    exit 1
}
