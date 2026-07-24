Unicode true

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "WinVer.nsh"

!ifndef PAYLOAD_DIR
    !error "Debes definir PAYLOAD_DIR."
!endif
!ifndef HELPER_DIR
    !error "Debes definir HELPER_DIR."
!endif
!ifndef OUTPUT_FILE
    !error "Debes definir OUTPUT_FILE."
!endif

Name "RPG Dos Almas"
Caption "Instalador de RPG Dos Almas"
OutFile "${OUTPUT_FILE}"
InstallDir "$APPDATA\.minecraft-rpg-dos-almas"
RequestExecutionLevel user
ManifestDPIAware true
ManifestSupportedOS Win10
CRCCheck on
SetCompressor /SOLID lzma
SetCompressorDictSize 64
SetDatablockOptimize on
ShowInstDetails nevershow
BrandingText "RPG Dos Almas - Forge 1.20.1"

VIProductVersion "1.0.0.0"
VIAddVersionKey /LANG=1034 "ProductName" "RPG Dos Almas"
VIAddVersionKey /LANG=1034 "FileDescription" "Instalador amigable del modpack RPG Dos Almas"
VIAddVersionKey /LANG=1034 "CompanyName" "RPG Dos Almas"
VIAddVersionKey /LANG=1034 "FileVersion" "1.0.0"
VIAddVersionKey /LANG=1034 "ProductVersion" "1.0.0"
VIAddVersionKey /LANG=1034 "LegalCopyright" "Uso personal"

!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "Bienvenida a RPG Dos Almas"
!define MUI_WELCOMEPAGE_TEXT "Este asistente instalara el modpack hiper optimizado para Forge 1.20.1.$\r$\n$\r$\nPrepara TLauncher, ajusta la RAM automaticamente y crea un acceso directo. TLauncher debe estar instalado o descargado por separado.$\r$\n$\r$\nCompatible con Windows 10 y Windows 11. No necesita permisos de administrador."
!define MUI_FINISHPAGE_TITLE "RPG Dos Almas esta listo"
!define MUI_FINISHPAGE_TEXT "La instalacion termino correctamente.$\r$\n$\r$\nComprueba que TLauncher tenga seleccionada la version Forge 1.20.1. Los dos equipos deben estar en la misma red local."
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchRPG
!define MUI_FINISHPAGE_RUN_TEXT "Abrir TLauncher ahora"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\COMO-JUGAR.txt"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Leer la guia rapida"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "Spanish"

Function .onInit
    ${IfNot} ${AtLeastWin10}
        MessageBox MB_ICONSTOP|MB_OK "Este instalador necesita Windows 10 o Windows 11."
        Abort
    ${EndIf}
FunctionEnd

Section "RPG Dos Almas" MainSection
    SectionIn RO
    DetailPrint "Preparando el contenido del modpack..."

    SetOutPath "$PLUGINSDIR\payload"
    File /r "${PAYLOAD_DIR}/*"

    SetOutPath "$PLUGINSDIR"
    File /oname=instalar-paquete.ps1 "${HELPER_DIR}/instalar-paquete.ps1"

    DetailPrint "Instalando y configurando TLauncher..."
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\instalar-paquete.ps1" -PayloadDir "$PLUGINSDIR\payload" -GameDir "$INSTDIR"'
    Pop $0

    ${If} $0 != 0
        MessageBox MB_ICONSTOP|MB_OK "No se pudo completar la instalacion.$\r$\n$\r$\nCierra Minecraft e intentalo de nuevo. El registro esta en:$\r$\n$LOCALAPPDATA\RPG Dos Almas\instalacion.log"
        SetErrorLevel 1
        Quit
    ${EndIf}

    SetOutPath "$INSTDIR"
    File /oname=abrir-rpg-dos-almas.ps1 "${HELPER_DIR}/abrir-rpg-dos-almas.ps1"
    File /oname=COMO-JUGAR.txt "${HELPER_DIR}/COMO-JUGAR.txt"

    CreateDirectory "$SMPROGRAMS\RPG Dos Almas"
    CreateShortCut "$DESKTOP\RPG Dos Almas.lnk" \
        "$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" \
        "-NoLogo -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File $\"$INSTDIR\abrir-rpg-dos-almas.ps1$\"" \
        "" 0 SW_SHOWNORMAL "" "Abrir RPG Dos Almas con TLauncher"
    CreateShortCut "$SMPROGRAMS\RPG Dos Almas\RPG Dos Almas.lnk" \
        "$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" \
        "-NoLogo -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File $\"$INSTDIR\abrir-rpg-dos-almas.ps1$\"" \
        "" 0 SW_SHOWNORMAL "" "Abrir RPG Dos Almas con TLauncher"
    CreateShortCut "$SMPROGRAMS\RPG Dos Almas\Guia rapida.lnk" \
        "$INSTDIR\COMO-JUGAR.txt"

    WriteRegStr HKCU "Software\RPG Dos Almas" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "Software\RPG Dos Almas" "Version" "1.0.0"
    WriteRegStr HKCU "Software\RPG Dos Almas" "Servidor" "192.168.78.210:25565"
SectionEnd

Function LaunchRPG
    ExecShell "open" "$DESKTOP\RPG Dos Almas.lnk"
FunctionEnd
