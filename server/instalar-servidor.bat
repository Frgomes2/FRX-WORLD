@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===== FRX-WORLD: instala o servidor Forge 1.20.1 nesta pasta =====
set FORGE=1.20.1-47.4.10
set URL=https://maven.minecraftforge.net/net/minecraftforge/forge/%FORGE%/forge-%FORGE%-installer.jar

call ..\java17.bat || ( pause & exit /b 1 )

if not exist forge-installer.jar (
  echo Baixando Forge %FORGE%...
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile 'forge-installer.jar'"
  if errorlevel 1 ( echo [ERRO] Falha no download. & pause & exit /b 1 )
)

echo Instalando servidor Forge...
java -jar forge-installer.jar --installServer
if errorlevel 1 ( echo [ERRO] Falha na instalacao do Forge. & pause & exit /b 1 )
del forge-installer.jar forge-installer.jar.log 2>nul

rem Memoria do servidor (6 a 10 jogadores)
> user_jvm_args.txt echo -Xms4G
>> user_jvm_args.txt echo -Xmx6G

echo.
echo O Minecraft exige aceitar o EULA: https://aka.ms/MinecraftEULA
set /p ACEITO=Voce aceita o EULA? (S/N): 
if /i "%ACEITO%"=="S" (
  >eula.txt echo eula=true
) else (
  >eula.txt echo eula=false
)

if not exist mods mkdir mods
echo.
echo Pronto! Agora rode iniciar.bat
pause
