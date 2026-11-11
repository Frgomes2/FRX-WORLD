@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem ===== FRX-WORLD: adiciona os mods de terceiros do documento de design =====
rem Requer packwiz.exe nesta pasta (veja o README).
if not exist packwiz.exe (
  echo [ERRO] Coloque o packwiz.exe nesta pasta primeiro.
  pause & exit /b 1
)

rem Base de conteudo e scripts
packwiz -y mr add kubejs
rem Mapa e teleporte
packwiz -y mr add journeymap
packwiz -y mr add waystones
rem Modelos, acessorios, loot e NPCs
packwiz -y mr add geckolib
packwiz -y mr add curios
packwiz -y mr add lootr
packwiz -y mr add easy-npc
packwiz -y cf add sophisticated-backpacks
rem Dungeons prontas enquanto as nossas nao ficam prontas
packwiz -y mr add when-dungeons-arise
packwiz -y mr add yungs-better-dungeons
rem Desempenho
packwiz -y mr add embeddium
packwiz -y mr add modernfix
packwiz -y mr add ferritecore

packwiz refresh
echo.
echo Pronto. Confira a pasta mods\ e o arquivo index.toml.
pause
