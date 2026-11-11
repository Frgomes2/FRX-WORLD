@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title FRX-WORLD - Instalar Forge
rem ===== FRX-WORLD (amigos): instala o Forge 1.20.1 no Minecraft Launcher oficial. Rodar UMA vez. =====
set FORGE=1.20.1-47.4.10
set URL=https://maven.minecraftforge.net/net/minecraftforge/forge/%FORGE%/forge-%FORGE%-installer.jar

call :acharJava || ( pause & exit /b 1 )

if not exist forge-installer.jar (
  echo Baixando o Forge %FORGE%...
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile 'forge-installer.jar'"
  if errorlevel 1 ( echo [ERRO] Falha no download do Forge. & pause & exit /b 1 )
)
echo.
echo Feche o Minecraft Launcher antes de continuar.
echo Na janela do Forge deixe marcado "Install client" e clique em OK.
pause
"%JAVA%" -jar forge-installer.jar
echo.
echo Pronto. Agora rode o 2-atualizar-mods.bat
pause
exit /b 0

:acharJava
set "JAVA="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Eclipse Adoptium\jre-17*" "C:\Program Files\Java\jdk-17*" "C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Zulu\zulu-17*") do if exist "%%~fD\bin\java.exe" set "JAVA=%%~fD\bin\java.exe"
if defined JAVA exit /b 0
echo [ERRO] Nao encontrei o Java 17.
echo Instale o "Temurin 17 (JDK)" em https://adoptium.net/temurin/releases/?version=17 e rode de novo.
exit /b 1
