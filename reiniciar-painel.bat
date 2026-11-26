@echo off
chcp 65001 >nul
title FRX-WORLD - reiniciando so o painel
cd /d "%~dp0"
rem Reinicia SO o painel (para aplicar mudancas nele) sem desligar o Minecraft: o painel novo assume o jogo que esta rodando.
rem Chamado pelo botao "Reiniciar so o painel" (aba Servidor), pelo comando /frx painel reiniciar ou com dois cliques.

echo [1/6] Conferindo o codigo do painel...
pushd painel
call npx tsc --noEmit -p . > "%~dp0logs\painel-tsc.log" 2>&1
if errorlevel 1 (
  echo [ERRO] O codigo do painel tem erro: o painel atual continua no ar. Detalhes em logs\painel-tsc.log
  >>"%~dp0logs\agente-eventos.log" echo %date% %time% reiniciar-painel: cancelado, o codigo do painel tem erro ^(logs\painel-tsc.log^)
  popd & exit /b 1
)
popd

echo [2/6] Fechando o painel atual (o jogo continua no ar)...
rem a sentinela sai sozinha (este fechamento e de proposito); outra nasce no fim
>"%~dp0controle\sentinela.txt" echo parada
powershell -NoProfile -Command "$ids=@(Get-NetTCPConnection -State Listen -LocalPort 3000 -EA 0 | ForEach-Object OwningProcess); Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'node.exe' -or $_.Name -eq 'cmd.exe') -and $_.CommandLine -match 'npm(-cli\.js)?\W+run start|next\W+start|next\\dist\\bin\\next' } | ForEach-Object { $ids += $_.ProcessId }; $ids | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force -EA 0 }"
set /a TENTATIVAS=0
:esperaFechar
netstat -ano | findstr /r /c:":3000 .*LISTENING" >nul || goto :fechou
set /a TENTATIVAS+=1
if %TENTATIVAS% GEQ 15 goto :fechou
timeout /t 1 /nobreak >nul
goto :esperaFechar
:fechou

echo [3/6] Banco e compilacao do painel...
pushd painel
call npx prisma migrate deploy >nul 2>&1
call npx prisma generate >nul 2>&1
set "BUILD="
for /f %%R in ('powershell -NoProfile -Command "$b=Get-Item '.next\BUILD_ID' -EA 0; $t=[DateTime[]](@(Get-ChildItem 'src','prisma','public','package.json','.env' -Recurse -File -EA 0).Where({$_.FullName -notmatch 'generated'}).ForEach('LastWriteTime')); [Array]::Sort($t); if (-not $b -or $t[-1] -gt $b.LastWriteTime) {'SIM'} else {'NAO'}"') do set "BUILD=%%R"
if /i "%BUILD%"=="SIM" (
  echo       Painel mudou: compilando, pode levar uns minutos...
  call npm run build > "%~dp0logs\painel-build.log" 2>&1
  if errorlevel 1 echo [ERRO] A compilacao do painel falhou ^(logs\painel-build.log^); subindo assim mesmo.
)
popd

echo [4/6] Ligando o painel...
if exist "%~dp0logs\painel.log" copy /y "%~dp0logs\painel.log" "%~dp0logs\painel-anterior.log" >nul
if not exist "%~dp0logs\node" mkdir "%~dp0logs\node"
start "FRX-WORLD Painel" /min cmd /k "cd /d "%~dp0painel" && set NODE_OPTIONS=--report-on-fatalerror --report-uncaught-exception --report-directory=%~dp0logs\node&& npm run start > "%~dp0logs\painel.log" 2>&1"
set /a TENTATIVAS=0
:esperaPainel
timeout /t 2 /nobreak >nul
curl -s -o nul http://localhost:3000/login
if not errorlevel 1 goto :painelOk
set /a TENTATIVAS+=1
if %TENTATIVAS% LSS 90 goto :esperaPainel
>>"%~dp0logs\agente-eventos.log" echo %date% %time% reiniciar-painel: o painel nao respondeu em 3 min
exit /b 1
:painelOk

echo [5/6] O painel assume o jogo...
set "TOKEN="
for /f "usebackq tokens=1,* delims==" %%A in ("painel\.env") do (
  if /i "%%A"=="SERVIDOR_TOKEN" set "TOKEN=%%~B"
)
curl -s -o nul -X POST -H "x-servidor-token: %TOKEN%" http://localhost:3000/api/servidor/ligar

echo [6/6] Sentinela de quedas...
if exist "%~dp0controle\sentinela.ps1" start "FRX-WORLD Sentinela" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0controle\sentinela.ps1"
>>"%~dp0logs\agente-eventos.log" echo %date% %time% reiniciar-painel: painel reiniciado, o jogo continuou no ar
echo Pronto.
timeout /t 3 >nul
