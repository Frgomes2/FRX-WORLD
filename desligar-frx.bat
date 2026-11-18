@echo off
chcp 65001 >nul
title Desligando FRX-WORLD
cd /d "%~dp0"

rem Le o SERVIDOR_TOKEN do painel\.env
set "TOKEN="
for /f "usebackq tokens=1,* delims==" %%A in ("painel\.env") do (
  if /i "%%A"=="SERVIDOR_TOKEN" set "TOKEN=%%~B"
)

rem avisa a sentinela de quedas que este desligamento e de proposito (ela sai sozinha)
>"%~dp0controle\sentinela.txt" echo parada

rem fotografa o estado do PC antes de mexer (ajuda a achar a causa quando tudo cai sozinho); nunca interrompe
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0controle\registrar-estado.ps1" >nul 2>&1

echo [1/4] Parando o servidor Minecraft (salvando o mundo)...
curl -s -m 110 -X POST -H "x-servidor-token: %TOKEN%" http://localhost:3000/api/servidor/desligar >nul 2>&1
echo       ok

echo [2/4] Backup do mundo e do painel...
for /f %%D in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set "QUANDO=%%D"
if not exist "backups" mkdir "backups"
if exist "server\FRX-WORLD\level.dat" (
  tar -a -c -f "backups\mundo-%QUANDO%.zip" --exclude=session.lock -C server FRX-WORLD >nul 2>&1
  if errorlevel 1 ( echo       [aviso] Nao consegui compactar o mundo ^(veja se o servidor fechou^). ) else ( echo       mundo: backups\mundo-%QUANDO%.zip )
) else (
  echo       sem mundo para guardar
)
docker compose exec -T banco pg_dump -U frx frxworld > "backups\painel-%QUANDO%.sql" 2>nul
if errorlevel 1 ( echo       [aviso] Nao consegui salvar o banco do painel. & del "backups\painel-%QUANDO%.sql" 2>nul ) else ( echo       painel: backups\painel-%QUANDO%.sql )
rem guarda em camadas: 7 mais recentes + ultimo de cada dia (7 dias) + ultimo de cada semana (5 semanas)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0controle\limpar-backups.ps1"

echo [3/4] Fechando painel, pack e tunel...
taskkill /fi "WINDOWTITLE eq FRX-WORLD Painel*" /t /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq FRX-WORLD Pack*" /t /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq FRX-WORLD Tunel*" /t /f >nul 2>&1
rem garante: mata quem ainda estiver ouvindo nas portas do painel (3000) e do pack (8080)
for %%Q in (3000 8080) do for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%%Q .*LISTENING"') do taskkill /pid %%P /t /f >nul 2>&1
echo       ok

echo [4/4] Parando o banco...
docker compose stop >nul 2>&1
echo       ok
echo.
echo FRX-WORLD desligado.
timeout /t 4 >nul
