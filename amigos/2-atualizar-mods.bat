@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title FRX-WORLD - Atualizar mods
rem ===== FRX-WORLD (amigos): baixa/atualiza os mods do servidor. Rode SEMPRE antes de jogar. =====
rem Launcher oficial: pasta propria. TLauncher/Shiginima: usam a .minecraft (escolha salva em launcher.txt)
set "LAUNCHER="
if exist launcher.txt set /p LAUNCHER=<launcher.txt
if not defined LAUNCHER (
  echo Qual launcher voce usa?
  echo   1 - Minecraft Launcher oficial
  echo   2 - TLauncher, Shiginima ou outro
  set /p LAUNCHER=Digite 1 ou 2 e aperte Enter: 
)
if "%LAUNCHER%"=="2" (
  set "PASTA=%APPDATA%\.minecraft"
  echo Usando a pasta .minecraft do TLauncher/Shiginima
  echo [aviso] Tire da pasta mods os mods de outros modpacks antes de jogar.
) else (
  set "LAUNCHER=1"
  set "PASTA=%APPDATA%\.frx-world"
)
> launcher.txt echo %LAUNCHER%

set "PACK="
if exist endereco-pack.txt for /f "usebackq eol=# tokens=*" %%L in ("endereco-pack.txt") do if not defined PACK set "PACK=%%L"
if not defined PACK (
  echo [ERRO] Falta o endereco do pack no arquivo endereco-pack.txt. Peca ao dono do servidor.
  pause & exit /b 1
)

call :acharJava || ( pause & exit /b 1 )

if not exist "%PASTA%" mkdir "%PASTA%"
copy /y packwiz-installer-bootstrap.jar "%PASTA%\" >nul
cd /d "%PASTA%"
echo Atualizando mods a partir de %PACK% ...
"%JAVA%" -jar packwiz-installer-bootstrap.jar -g -s client "%PACK%"
if errorlevel 1 (
  echo.
  echo [ERRO] Nao consegui baixar o pack. O servidor do FRX-WORLD esta ligado?
  pause & exit /b 1
)
echo.
echo Pronto! Os mods estao em: %PASTA%
echo Abra o Minecraft Launcher e jogue com a instalacao "FRX-WORLD" (veja o LEIA-ME).
pause
exit /b 0

:acharJava
set "JAVA="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Eclipse Adoptium\jre-17*" "C:\Program Files\Java\jdk-17*" "C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Zulu\zulu-17*") do if exist "%%~fD\bin\java.exe" set "JAVA=%%~fD\bin\java.exe"
if defined JAVA exit /b 0
echo [ERRO] Nao encontrei o Java 17.
echo Instale o "Temurin 17 (JDK)" em https://adoptium.net/temurin/releases/?version=17 e rode de novo.
exit /b 1
