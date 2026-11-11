@echo off
chcp 65001 >nul
cd /d "%~dp0"
rem ===== FRX-WORLD: atualiza o indice e publica o pack em http://localhost:8080/pack.toml =====
packwiz refresh || ( pause & exit /b 1 )
echo Pack no ar em http://localhost:8080/pack.toml  (Ctrl+C para parar)
packwiz serve
