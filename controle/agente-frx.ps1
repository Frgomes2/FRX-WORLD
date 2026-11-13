# FRX-WORLD - agente de controle remoto
# Fica rodando escondido (inicia com o Windows). A cada 3 s olha controle\pedido.txt:
#   ligar | desligar | reiniciar  -> roda os mesmos .bat dos atalhos, sem perguntas.
# Estado em controle\estado.txt; eventos em logs\agente-eventos.log; saida de cada execucao em logs\agente\*.log
# Se este arquivo mudar, o agente se reinicia sozinho com a versao nova.
# Vigia: depois de um "ligar"/"reiniciar", se a porta do Minecraft (25565) parar de responder por 1 minuto,
#   o agente religa tudo sozinho (ate 3 vezes em 30 min). "desligar" desarma a vigia.
#   controle\vigia.txt com "desligada" pausa a vigia sem mexer no servidor.

$ErrorActionPreference = 'Continue'
$controle = $PSScriptRoot
$raiz = Split-Path -Parent $controle
$pedido = Join-Path $controle 'pedido.txt'
$estado = Join-Path $controle 'estado.txt'
$batida = Join-Path $controle 'batida.txt'
$desejadoArq = Join-Path $controle 'desejado.txt'
$vigiaArq = Join-Path $controle 'vigia.txt'
$portaJogo = 25565
$pastaLogs = Join-Path $raiz 'logs\agente'
$eventos = Join-Path $raiz 'logs\agente-eventos.log'
New-Item -ItemType Directory -Force -Path $pastaLogs | Out-Null
$script = $PSCommandPath
$versao = (Get-Item $script).LastWriteTime

# uma instancia so (espera ate 20 s a anterior sair, para a troca de versao)
$criado = $false
$mutex = New-Object System.Threading.Mutex($false, 'Global\FRX-WORLD-agente')
try { $criado = $mutex.WaitOne(20000) } catch [System.Threading.AbandonedMutexException] { $criado = $true }
if (-not $criado) { exit }

function Evento([string]$t) {
  try { Add-Content -Path $eventos -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $t" -Encoding UTF8 } catch {}
}
function Estado([string]$t) {
  try { Set-Content -Path $estado -Value "$t | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -Encoding UTF8 } catch {}
  Evento $t
}

# Cada execucao grava num arquivo proprio: as janelas que o atalho abre (painel, pack) herdam o arquivo
# aberto e o mantem preso; reaproveitar o mesmo log fazia a execucao seguinte falhar na hora.
function Rodar([string]$bat, [string]$args2) {
  $saida = Join-Path $pastaLogs ("{0}-{1}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), [IO.Path]::GetFileNameWithoutExtension($bat))
  Evento "rodando $bat $args2 -> $saida"
  $p = Start-Process -FilePath 'cmd.exe' -ArgumentList "/c `"`"$raiz\$bat`" $args2 > `"$saida`" 2>&1`"" -WorkingDirectory $raiz -WindowStyle Minimized -PassThru
  $p.WaitForExit()   # sem -Wait: ele esperaria tambem as janelas do painel e do pack, que ficam abertas
  Evento "$bat terminou (codigo $($p.ExitCode))"
  return $p.ExitCode
}

# ---- vigia do servidor
function Desejado([string]$v) { try { Set-Content -Path $desejadoArq -Value $v -Encoding ASCII } catch {} }
function QuerLigado { try { return (Test-Path $desejadoArq) -and ((Get-Content $desejadoArq -Raw).Trim() -eq 'ligado') } catch { return $false } }
function VigiaPausada { try { return (Test-Path $vigiaArq) -and ((Get-Content $vigiaArq -Raw).Trim().ToLower() -eq 'desligada') } catch { return $false } }
function PortaAberta {
  try { return [bool](Get-NetTCPConnection -State Listen -LocalPort $portaJogo -ErrorAction SilentlyContinue) } catch { return $true }   # na duvida, nao religa
}
$carencia = (Get-Date).AddMinutes(4)   # tempo para o servidor subir antes de a vigia olhar
$falhas = 0
$religadas = @()
# PC acabou de ligar (servidor fora do ar no arranque do agente): nao sobe o servidor sozinho, espera um "ligar"
if ((QuerLigado) -and -not (PortaAberta)) { Desejado 'desligado'; Evento 'vigia: servidor fora do ar no arranque do agente - aguardando um ligar' }

Estado 'agente pronto'
$voltas = 0
while ($true) {
  try {
    if ($voltas % 10 -eq 0) { Set-Content -Path $batida -Value (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') -Encoding UTF8 }
    $voltas++
    if (Test-Path $pedido) {
      $acao = ''
      $acaoCrua = ''
      try { $acaoCrua = (Get-Content $pedido -Raw -Encoding UTF8).Trim(); $acao = $acaoCrua.ToLower() } catch {}
      Remove-Item $pedido -Force -ErrorAction SilentlyContinue
      # "comando <texto>": manda para o console do Minecraft pelo painel (uma linha por comando; varias linhas = varios comandos)
      if ($acao.StartsWith('comando ')) {
        $tk = ''
        try { foreach ($l in Get-Content (Join-Path $raiz 'painel\.env')) { if ($l -match '^\s*SERVIDOR_TOKEN\s*=\s*"?([^"]+)"?\s*$') { $tk = $Matches[1] } } } catch {}
        $bruto = ''
        try { $bruto = $acaoCrua } catch {}
        foreach ($linha in ($bruto -split "`n")) {
          $c = $linha.Trim()
          if ($c.ToLower().StartsWith('comando ')) { $c = $c.Substring(8).Trim() }
          if (-not $c) { continue }
          try {
            $corpo = [Text.Encoding]::UTF8.GetBytes((@{ comando = $c } | ConvertTo-Json -Compress))
            $r = Invoke-RestMethod -Uri 'http://localhost:3000/api/servidor/comando' -Method Post -Headers @{ 'x-servidor-token' = $tk } -ContentType 'application/json; charset=utf-8' -Body $corpo -TimeoutSec 10
            Evento "comando enviado: $c"
          } catch { Evento "comando falhou: $c -> $_" }
          Start-Sleep -Milliseconds 400
        }
        $acao = ''
      }
      if ($acao -eq '') { Start-Sleep -Seconds 1; continue }
      switch ($acao) {
        'ligar'     { Estado 'ligando'; $c = Rodar 'iniciar-frx.bat' '/auto'; $carencia = (Get-Date).AddMinutes(5); $falhas = 0; if ($c -eq 0) { Desejado 'ligado'; Estado 'ligado' } else { Estado "erro ao ligar (codigo $c) - veja logs\agente" } }
        'desligar'  { Desejado 'desligado'; Estado 'desligando'; $c = Rodar 'desligar-frx.bat' ''; Estado 'desligado' }
        'diagnostico' { $s = Join-Path $raiz 'logs\diagnostico.txt'; & { "== $(Get-Date)"; '== docker ps -a'; docker ps -a 2>&1; '== docker compose ps'; Push-Location $raiz; docker compose ps 2>&1; '== logs banco'; docker compose logs --tail 40 banco 2>&1; Pop-Location; '== porta 3000/5433'; netstat -ano | Select-String ':3000 |:5433 '; '== donos 5433'; Get-NetTCPConnection -LocalPort 5433 -State Listen -EA 0 | ForEach-Object { Get-Process -Id $_.OwningProcess -EA 0 | Select-Object Id,ProcessName,Path } | Format-Table -AutoSize | Out-String -Width 300; '== node'; Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Select-Object ProcessId,ParentProcessId,CreationDate,CommandLine | Format-List | Out-String -Width 400; '== janelas'; Get-Process | Where-Object { $_.MainWindowTitle -like 'FRX-WORLD*' } | Select-Object Id,ProcessName,MainWindowTitle | Format-Table -AutoSize | Out-String -Width 300 } *> $s; Evento 'diagnostico gravado' }
        'reiniciar' { Estado 'reiniciando'; Rodar 'desligar-frx.bat' '' | Out-Null; $c = Rodar 'iniciar-frx.bat' '/auto'; $carencia = (Get-Date).AddMinutes(5); $falhas = 0; if ($c -eq 0) { Desejado 'ligado'; Estado 'ligado' } else { Estado "erro ao ligar (codigo $c) - veja logs\agente" } }
        default     { Estado "pedido desconhecido: $acao" }
      }
    }
    # vigia: a cada ~30 s confere a porta do jogo; duas falhas seguidas = caiu
    if ($voltas % 10 -eq 5 -and (QuerLigado) -and -not (VigiaPausada) -and (Get-Date) -gt $carencia -and -not (Test-Path $pedido)) {
      if (PortaAberta) { $falhas = 0 }
      else {
        $falhas++
        if ($falhas -ge 2) {
          $falhas = 0
          $religadas = @($religadas | Where-Object { $_ -gt (Get-Date).AddMinutes(-30) })
          if ($religadas.Count -ge 3) {
            Desejado 'desligado'
            Estado 'servidor caiu 4 vezes em 30 min - vigia parou (veja server\logs e crash-reports)'
          } else {
            $religadas += (Get-Date)
            Estado "servidor caiu - religando sozinho (tentativa $($religadas.Count) de 3)"
            Rodar 'desligar-frx.bat' '' | Out-Null
            $c = Rodar 'iniciar-frx.bat' '/auto'
            $carencia = (Get-Date).AddMinutes(5)
            if ($c -eq 0) { Estado 'ligado (religado pela vigia)' } else { Estado "erro ao religar (codigo $c) - veja logs\agente" }
          }
        }
      }
    }
    # versao nova do agente: passa a vez para ela
    if ((Get-Item $script).LastWriteTime -ne $versao) {
      Evento 'agente atualizado: reiniciando o agente'
      Start-Process -FilePath 'powershell.exe' -ArgumentList "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`"" -WindowStyle Hidden
      $mutex.ReleaseMutex()
      exit
    }
  } catch { Evento "erro no agente: $_" }
  Start-Sleep -Seconds 3
}
