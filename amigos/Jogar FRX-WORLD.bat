@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Jogar FRX-WORLD
rem ===== FRX-WORLD (amigos): atualiza os mods e ja abre o launcher. Use sempre este para jogar. =====

rem atalho "Jogar FRX-WORLD" na area de trabalho (refeito toda vez, caso a pasta mude de lugar)
powershell -NoProfile -Command "$d=[Environment]::GetFolderPath('Desktop'); $s=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $d 'Jogar FRX-WORLD.lnk')); $s.TargetPath='%~f0'; $s.WorkingDirectory='%~dp0'; $s.IconLocation='%SystemRoot%\System32\imageres.dll,77'; $s.Save()" >nul 2>&1

echo Atualizando os mods do FRX-WORLD...
call "%~dp02-atualizar-mods.bat" /jogar
if errorlevel 1 exit /b 1

set "LAUNCHER=1"
if exist launcher.txt set /p LAUNCHER=<launcher.txt
set "LAUNCHER=%LAUNCHER: =%"

rem caminho escolhido a mao (Shiginima ou outro): uma linha com o .exe em launcher-caminho.txt
if exist launcher-caminho.txt (
  set /p CAMINHO=<launcher-caminho.txt
  call start "" "%%CAMINHO%%"
  exit /b 0
)
if "%LAUNCHER%"=="2" (
  if exist "%APPDATA%\.minecraft\TLauncher.exe" ( start "" "%APPDATA%\.minecraft\TLauncher.exe" & exit /b 0 )
) else (
  for %%L in ("%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe" "%LOCALAPPDATA%\Programs\Minecraft Launcher\MinecraftLauncher.exe") do (
    if exist %%L ( start "" %%L & exit /b 0 )
  )
  rem launcher da Microsoft Store / Xbox
  start "" "shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft" && exit /b 0
)
echo.
echo Mods atualizados! Nao achei o seu launcher: abra ele normalmente.
echo (Dica: escreva o caminho do .exe do launcher no arquivo launcher-caminho.txt e na proxima vez ele abre sozinho.)
pause
