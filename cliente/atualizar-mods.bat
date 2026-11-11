@echo off
chcp 65001 >nul
cd /d "%~dp0jogo"
rem ===== FRX-WORLD: baixa/atualiza os mods do pack na pasta do jogo (cliente\jogo) =====
rem Precisa do pack\servir.bat rodando.
call ..\..\java17.bat || ( pause & exit /b 1 )
java -jar packwiz-installer-bootstrap.jar -g -s client http://localhost:8080/pack.toml
echo.
echo Pronto. No Minecraft Launcher a instalacao do FRX-WORLD deve usar a pasta:
echo %CD%
if /i not "%~1"=="/sem-pausa" pause
