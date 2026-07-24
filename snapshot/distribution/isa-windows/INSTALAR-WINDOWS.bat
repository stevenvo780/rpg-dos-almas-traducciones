@echo off
title Instalador RPG Dos Almas
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar-windows.ps1"
if errorlevel 1 (
  echo.
  echo La instalacion encontro un problema. No cierres esta ventana.
  pause
)
