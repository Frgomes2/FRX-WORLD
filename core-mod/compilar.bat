@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "SEM_PAUSA="
if /i "%~1"=="/sem-pausa" set "SEM_PAUSA=1"
call ..\java17.bat || goto :falhou

rem Remove jars antigos para nunca ter duas versoes do mod
if exist build\libs del /q build\libs\frxworld-*.jar 2>nul

call gradlew.bat build --console=plain || goto :falhou

rem Publica o jar novo no pack (clientes recebem pelo atualizar-mods)
del /q ..\pack\mods\frxworld-*.jar 2>nul
for %%F in (build\libs\frxworld-*.jar) do (
  copy /y "%%F" ..\pack\mods\ >nul
  echo Core mod publicado no pack: %%~nxF
)
exit /b 0

:falhou
if not defined SEM_PAUSA pause
exit /b 1
