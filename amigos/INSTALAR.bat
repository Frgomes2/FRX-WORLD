@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title FRX-WORLD - Instalar
rem ===== FRX-WORLD (amigos): rodar UMA vez. Instala o Forge e o AutoModpack.
rem       Depois disso, os mods sao baixados sozinhos ao entrar no servidor. =====
set FORGE=1.20.1-47.4.10
set URL=https://maven.minecraftforge.net/net/minecraftforge/forge/%FORGE%/forge-%FORGE%-installer.jar
set "MODS=%APPDATA%\.minecraft\mods"

call :acharJava || ( pause & exit /b 1 )

set "AMP="
for %%F in (automodpack*.jar) do set "AMP=%%F"
if not defined AMP ( echo [ERRO] Falta o arquivo automodpack*.jar nesta pasta. Peca o kit de novo. & pause & exit /b 1 )

rem ---- 1. Forge
if not exist forge-installer.jar (
  echo Baixando o Forge %FORGE%...
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile 'forge-installer.jar'"
  if errorlevel 1 ( echo [ERRO] Falha no download do Forge. & pause & exit /b 1 )
)
echo.
echo [1/2] Forge
echo   Feche o Minecraft / TLauncher antes de continuar.
echo   Na janela do Forge deixe marcado "Install client" e clique em OK.
echo   (No TLauncher voce tambem pode so escolher a versao "Forge 1.20.1" e pular essa janela.)
pause
"%JAVA%" -jar forge-installer.jar

rem ---- 2. AutoModpack na pasta mods
echo.
echo [2/2] AutoModpack
if not exist "%MODS%" mkdir "%MODS%"
dir /b "%MODS%\*.jar" 2>nul | findstr /v /i "^automodpack" >nul
if not errorlevel 1 (
  echo   Ja existem outros mods em %MODS%.
  echo   Eles podem dar conflito com o FRX-WORLD.
  choice /c SN /m "  Guardar esses mods em .minecraft\mods-guardados"
  if not errorlevel 2 (
    if not exist "%APPDATA%\.minecraft\mods-guardados" mkdir "%APPDATA%\.minecraft\mods-guardados"
    for %%J in ("%MODS%\*.jar") do echo %%~nxJ| findstr /i "^automodpack" >nul || move /y "%%J" "%APPDATA%\.minecraft\mods-guardados\" >nul
  )
)
del /q "%MODS%\automodpack*.jar" 2>nul
copy /y "%AMP%" "%MODS%\" >nul || ( echo [ERRO] Nao consegui copiar o AutoModpack. & pause & exit /b 1 )
echo   ok: %MODS%\%AMP%

echo.
echo ============================================================
echo  Pronto! Agora:
echo   1. Abra o launcher e escolha a versao/instalacao "forge" 1.20.1
echo      (de pelo menos 6 GB de memoria nas configuracoes).
echo   2. Multijogador ^> Adicionar servidor: impairment-marcus.tun.ply.gg
echo   3. Entre no servidor. Na primeira vez ele pede para confirmar
echo      o "fingerprint" (esta no LEIA-ME) e baixa todos os mods.
echo   4. Quando terminar, reinicie o jogo e entre de novo.
echo ============================================================
pause
exit /b 0

:acharJava
set "JAVA="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Eclipse Adoptium\jre-17*" "C:\Program Files\Java\jdk-17*" "C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Zulu\zulu-17*") do if exist "%%~fD\bin\java.exe" set "JAVA=%%~fD\bin\java.exe"
if defined JAVA exit /b 0
echo [ERRO] Nao encontrei o Java 17.
echo Instale o "Temurin 17 (JDK)" em https://adoptium.net/temurin/releases/?version=17 e rode de novo.
exit /b 1
