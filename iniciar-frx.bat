@echo off
chcp 65001 >nul
title FRX-WORLD
cd /d "%~dp0"
rem /auto = chamado pelo agente (sem perguntas, sem pausas, sem abrir o navegador)
set "AUTO="
if /i "%~1"=="/auto" set "AUTO=1"
echo.
echo   ========================================
echo                FRX-WORLD
echo   ========================================
echo.

rem ---------- 0. Ja esta ligado? (ligar de novo por cima derrubava o servidor sem salvar)
curl -s -m 3 -o nul http://localhost:3000/login
if errorlevel 1 goto :naoLigado
echo   O FRX-WORLD ja esta ligado.
echo   Para aplicar atualizacoes e preciso desligar com seguranca (salva o mundo e faz backup) e ligar de novo.
if defined AUTO goto :reiniciarAuto
choice /c SN /n /m "  Reiniciar tudo agora? [S/N] "
if errorlevel 2 (
  start "" http://localhost:3000/admin/servidor
  exit /b 0
)
:reiniciarAuto
call "%~dp0desligar-frx.bat"
cd /d "%~dp0"
:naoLigado

rem ---------- 1. Docker (banco do painel)
echo [1/4] Banco de dados...
docker info >nul 2>&1
if not errorlevel 1 goto :dockerOk
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
echo       Abrindo o Docker Desktop, aguarde...
set /a TENTATIVAS=0
:esperaDocker
timeout /t 3 /nobreak >nul
docker info >nul 2>&1
if not errorlevel 1 goto :dockerOk
set /a TENTATIVAS+=1
if %TENTATIVAS% LSS 60 goto :esperaDocker
echo [ERRO] O Docker nao abriu. Abra o Docker Desktop e rode o atalho de novo.
(if not defined AUTO pause) & exit /b 1
:dockerOk
docker compose up -d >nul 2>&1
echo       ok

rem ---------- 1b. Core mod: compila sozinho se o codigo mudou
set "PRECISA="
for /f %%R in ('powershell -NoProfile -Command "$j=Get-ChildItem 'core-mod\build\libs\frxworld-*.jar' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1; $s=Get-ChildItem 'core-mod\src','core-mod\gradle.properties','core-mod\build.gradle' -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if (-not $j -or $s.LastWriteTime -gt $j.LastWriteTime) {'SIM'} else {'NAO'}"') do set "PRECISA=%%R"
if /i "%PRECISA%"=="SIM" (
  echo [1b] Core mod mudou: compilando, pode levar alguns minutos...
  call core-mod\compilar.bat /sem-pausa > logs\compilacao.log 2>&1
  if errorlevel 1 (
    echo [ERRO] A compilacao do core mod falhou. Detalhes em logs\compilacao.log
    powershell -NoProfile -Command "Get-Content '%~dp0logs\compilacao.log' -Tail 25"
    (if not defined AUTO pause) & exit /b 1
  )
  echo       ok
)
cd /d "%~dp0"

rem ---------- 2. Pack (mods para os jogadores)
if exist "pack\pendentes.txt" (
  echo [2a] Adicionando mods novos ao pack, pode demorar...
  pushd pack
  for /f "usebackq eol=# tokens=1,2" %%A in ("pendentes.txt") do (
    echo       %%B
    packwiz -y %%A add %%B >> ..\logs\pack.log 2>&1 || echo       [aviso] falhou: %%B ^(veja logs\pack.log^)
  )
  packwiz refresh >> ..\logs\pack.log 2>&1
  move /y pendentes.txt ..\logs\pendentes-aplicados.txt >nul
  popd
)
echo [2/4] Pack de mods...
start "FRX-WORLD Pack" /min cmd /c "cd /d "%~dp0pack" && packwiz refresh && packwiz serve"
echo       ok

rem Espera o pack responder e atualiza os mods do seu jogo (cliente\jogo)
set /a TENTATIVAS=0
:esperaPack
timeout /t 1 /nobreak >nul
curl -s -o nul http://localhost:8080/pack.toml
if not errorlevel 1 goto :packOk
set /a TENTATIVAS+=1
if %TENTATIVAS% LSS 30 goto :esperaPack
echo       [aviso] O pack nao respondeu; mods do cliente nao foram atualizados.
goto :painel
:packOk
echo       Atualizando mods do servidor...
pushd server
call ..\java17.bat >nul
java -jar packwiz-installer-bootstrap.jar -g -s server http://localhost:8080/pack.toml > ..\logs\mods-servidor.log 2>&1
popd
echo       Atualizando mods do seu jogo...
call "%~dp0cliente\atualizar-mods.bat" /sem-pausa >nul 2>&1
rem AutoModpack: mods so de cliente + jar para o kit dos amigos
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\automodpack-sincronizar.ps1" > logs\automodpack.log 2>&1
echo       ok
cd /d "%~dp0"

:painel
rem ---------- 3. Painel
echo [3/4] Painel...
if not exist "painel\node_modules" (
  echo       Primeira vez: instalando dependencias, pode demorar...
  pushd painel & call npm install & popd
)
pushd painel
echo       Atualizando banco do painel...
call npx prisma migrate deploy || ( echo [ERRO] Falha ao atualizar o banco. & popd & (if not defined AUTO pause) & exit /b 1 )
call npx prisma generate || ( echo [ERRO] Falha ao gerar o Prisma Client. & popd & (if not defined AUTO pause) & exit /b 1 )
rem Modo producao (o painel fica acessivel de fora pelo Cloudflare Tunnel): so recompila se o codigo mudou
set "BUILD="
for /f %%R in ('powershell -NoProfile -Command "$b=Get-Item '.next\BUILD_ID' -EA 0; $t=[DateTime[]](@(Get-ChildItem 'src','prisma','public','package.json','.env' -Recurse -File -EA 0).Where({$_.FullName -notmatch 'generated'}).ForEach('LastWriteTime')); [Array]::Sort($t); if (-not $b -or $t[-1] -gt $b.LastWriteTime) {'SIM'} else {'NAO'}"') do set "BUILD=%%R"
if /i "%BUILD%"=="SIM" (
  echo       Painel mudou: compilando, pode levar uns minutos...
  call npm run build > "%~dp0logs\painel-build.log" 2>&1
  if errorlevel 1 (
    echo [ERRO] A compilacao do painel falhou. Detalhes em logs\painel-build.log
    powershell -NoProfile -Command "Get-Content '%~dp0logs\painel-build.log' -Tail 25"
    popd & (if not defined AUTO pause) & exit /b 1
  )
)
popd
rem guarda o log do painel anterior: se o painel cair sozinho, a causa fica em painel-anterior.log
if exist "%~dp0logs\painel.log" copy /y "%~dp0logs\painel.log" "%~dp0logs\painel-anterior.log" >nul
if not exist "%~dp0logs\node" mkdir "%~dp0logs\node"
rem relatorio do node se o painel travar (causa das quedas): fica em logs\node
start "FRX-WORLD Painel" /min cmd /k "cd /d "%~dp0painel" && set NODE_OPTIONS=--report-on-fatalerror --report-uncaught-exception --report-directory=%~dp0logs\node&& npm run start > "%~dp0logs\painel.log" 2>&1"

set /a TENTATIVAS=0
:esperaPainel
timeout /t 2 /nobreak >nul
curl -s -o nul http://localhost:3000/login
if not errorlevel 1 goto :painelOk
set /a TENTATIVAS+=1
if %TENTATIVAS% LSS 60 goto :esperaPainel
echo [ERRO] O painel nao respondeu. Veja a janela "FRX-WORLD Painel" na barra de tarefas.
(if not defined AUTO pause) & exit /b 1
:painelOk
echo       ok

rem ---------- 3a. Tunel playit.gg (para os amigos entrarem de fora)
set "PLAYIT="
for %%P in ("%ProgramFiles%\playit_gg\bin\playit.exe" "%LOCALAPPDATA%\playit_gg\bin\playit.exe" "%~dp0tools\playit.exe") do if exist %%P set "PLAYIT=%%~P"
if not defined PLAYIT for /f "delims=" %%P in ('where playit 2^>nul') do if not defined PLAYIT set "PLAYIT=%%P"
if not defined PLAYIT goto :semPlayit
tasklist /fi "imagename eq playit.exe" | find /i "playit.exe" >nul
if errorlevel 1 start "FRX-WORLD Tunel" /min "%PLAYIT%"
echo [3a] Tunel playit.gg ligado (amigos podem entrar)
goto :fimPlayit
:semPlayit
echo [3a] playit.gg nao instalado: so voce consegue entrar (veja docs\08-jogar-com-amigos.md)
:fimPlayit

rem ---------- 3b. Liga o servidor Minecraft
set "TOKEN="
for /f "usebackq tokens=1,* delims==" %%A in ("painel\.env") do (
  if /i "%%A"=="SERVIDOR_TOKEN" set "TOKEN=%%~B"
)
echo [3b] Ligando o servidor Minecraft...
curl -s -o nul -X POST -H "x-servidor-token: %TOKEN%" http://localhost:3000/api/servidor/ligar
echo       ok (acompanhe pelo console na aba Servidor)
rem sentinela: registra e religa na hora se o painel ou o servidor morrerem sozinhos (nunca mata nada)
if exist "%~dp0controle\sentinela.ps1" start "FRX-WORLD Sentinela" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0controle\sentinela.ps1"

rem ---------- 4. Abre o navegador
echo [4/4] Abrindo o painel...
if not defined AUTO start "" http://localhost:3000/admin/servidor
echo.
echo   Tudo no ar. O servidor esta ligando: quando o console mostrar "Done", abra o jogo.
echo   As janelas "FRX-WORLD Pack" e "FRX-WORLD Painel" ficam minimizadas: nao feche.
timeout /t 6 >nul
