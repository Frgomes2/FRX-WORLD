@echo off
chcp 65001 >nul
cd /d "%~dp0"
rem ===== FRX-WORLD: baixa/atualiza no servidor os mods do pack (precisa do pack\servir.bat rodando) =====
call ..\java17.bat || ( pause & exit /b 1 )
java -jar packwiz-installer-bootstrap.jar -g -s server http://localhost:8080/pack.toml
pause
