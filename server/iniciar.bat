@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist run.bat (
  echo Rode primeiro instalar-servidor.bat
  pause & exit /b 1
)

rem Copia a versao mais recente do core mod, se ja foi compilado
if not exist mods mkdir mods
del /q mods\frxworld-*.jar 2>nul
for %%F in (..\core-mod\build\libs\frxworld-*.jar) do (
  copy /y "%%F" mods\ >nul
  echo Core mod atualizado: %%~nxF
)

call ..\java17.bat || ( pause & exit /b 1 )
call run.bat nogui
