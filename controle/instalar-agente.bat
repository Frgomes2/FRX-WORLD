@echo off
chcp 65001 >nul
cd /d "%~dp0"
rem ===== FRX-WORLD: instala o agente que deixa o Claude ligar/desligar o servidor (rodar UMA vez) =====
set "ATALHO=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\FRX-WORLD agente.lnk"
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut($env:ATALHO); $s.TargetPath='powershell.exe'; $s.Arguments='-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""%~dp0agente-frx.ps1""'; $s.WindowStyle=7; $s.Save()"
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0agente-frx.ps1"
echo.
echo Agente instalado: ele liga junto com o Windows e ja esta rodando.
echo Para remover: apague o atalho "FRX-WORLD agente" da pasta Inicializar (Win+R, shell:startup).
pause
