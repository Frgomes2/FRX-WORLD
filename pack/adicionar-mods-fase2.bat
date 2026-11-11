@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem ===== FRX-WORLD fase 2: FTB Quests, Apothic Attributes e 5 dimensoes (Minecraft 1.20.1 / Forge) =====
rem Requer packwiz.exe nesta pasta. O packwiz escolhe a versao 1.20.1 Forge e puxa as dependencias sozinho (-y).
if not exist packwiz.exe (
  echo [ERRO] Coloque o packwiz.exe nesta pasta primeiro.
  pause & exit /b 1
)

echo.
echo ==== Base das quests (FTB) ====
packwiz -y mr add architectury-api || packwiz -y cf add architectury-api
packwiz -y cf add ftb-library-forge
packwiz -y cf add ftb-teams-forge
packwiz -y cf add ftb-quests-forge

echo.
echo ==== Atributos (so o Apothic Attributes, sem o Apotheosis completo) ====
packwiz -y mr add placebo || packwiz -y cf add placebo
packwiz -y mr add apothic-attributes || packwiz -y cf add apothic-attributes

echo.
echo ==== Dimensoes ====
packwiz -y cf add the-twilight-forest
packwiz -y mr add aether || packwiz -y cf add aether
packwiz -y mr add structure-gel-api || packwiz -y cf add structure-gel-api
packwiz -y mr add blue-skies || packwiz -y cf add blue-skies
packwiz -y mr add the-undergarden || packwiz -y cf add the-undergarden
packwiz -y mr add deeperdarker || packwiz -y cf add deeperdarker

packwiz refresh
echo.
echo Pronto. Confira acima se algum mod deu [ERRO] e me mande o print se der.
echo Depois e so desligar e ligar o servidor pelo atalho.
pause
