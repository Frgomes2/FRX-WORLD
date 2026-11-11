@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===== FRX-WORLD: instala o Forge 1.20.1 no Minecraft Launcher oficial =====
set FORGE=1.20.1-47.4.10
set URL=https://maven.minecraftforge.net/net/minecraftforge/forge/%FORGE%/forge-%FORGE%-installer.jar

call ..\java17.bat || ( pause & exit /b 1 )

if not exist forge-installer.jar (
  echo Baixando Forge %FORGE%...
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile 'forge-installer.jar'"
  if errorlevel 1 ( echo [ERRO] Falha no download. & pause & exit /b 1 )
)

echo.
echo Feche o Minecraft Launcher antes de continuar.
echo Na janela do Forge: deixe marcado "Install client" e clique em OK.
pause
java -jar forge-installer.jar
echo.
echo Pronto. Abra o Minecraft Launcher: vai aparecer a instalacao "forge" (1.20.1-forge-47.4.10).
pause
