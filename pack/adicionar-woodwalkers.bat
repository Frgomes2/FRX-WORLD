@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem ===== FRX-WORLD: Woodwalkers (transformacao em criaturas, para a classe Druida) =====
rem Requer packwiz.exe nesta pasta. O packwiz escolhe a versao 1.20.1 Forge e puxa as dependencias sozinho (-y).
if not exist packwiz.exe (
  echo [ERRO] Coloque o packwiz.exe nesta pasta primeiro.
  pause & exit /b 1
)

echo.
echo ==== CraftedCore (apoio do Woodwalkers) ====
packwiz -y mr add crafted-core || packwiz -y cf add craftedcore

echo.
echo ==== Woodwalkers ====
packwiz -y mr add woodwalkers || packwiz -y cf add woodwalkers

packwiz refresh
echo.
echo ==== O que entrou ====
findstr /i "filename" mods\woodwalkers.pw.toml mods\crafted-core.pw.toml mods\craftedcore.pw.toml 2>nul
echo.
echo Pronto. Nao precisa reiniciar o servidor agora: so avise o Claude que rodou.
echo Se algum mod deu [ERRO], mande o print.
pause
