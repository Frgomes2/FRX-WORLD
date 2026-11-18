# FRX-WORLD - sentinela de quedas
# Lancada pelo iniciar-frx.bat a cada vez que o servidor liga. Fica olhando o painel (porta 3000) e o Minecraft (25565).
# Se um dos dois morrer sem que o agente tenha mandado desligar, ela:
#   1. grava em logs\quedas\queda-*.txt quem morreu primeiro, os processos que nasceram e morreram nos ultimos minutos
#      e os avisos do Windows;
#   2. pede "reiniciar" ao agente na hora (no maximo 2 vezes em 30 min), em vez de esperar os 5 minutos da vigia.
# Ela nunca mata nada. Uma sentinela por vez: controle\sentinela.txt guarda o numero da que vale;
# o desligar-frx.bat escreve "parada" ali e ela sai sozinha.
$ErrorActionPreference = 'SilentlyContinue'
$controle = $PSScriptRoot
$raiz = Split-Path -Parent $controle
$pasta = Join-Path $raiz 'logs\quedas'
New-Item -ItemType Directory -Force -Path $pasta | Out-Null
$marca = Join-Path $controle 'sentinela.txt'
$estadoArq = Join-Path $controle 'estado.txt'
$pedido = Join-Path $controle 'pedido.txt'
$religArq = Join-Path $pasta 'religadas-sentinela.txt'
$eventos = Join-Path $raiz 'logs\agente-eventos.log'
$eu = "$PID"
Set-Content -Path $marca -Value $eu -Encoding ASCII

function SouEu {
  try { return ((Get-Content $marca -Raw).Trim() -eq $eu) } catch { return $true }
}
function Dono([int]$porta) {
  try {
    $c = Get-NetTCPConnection -State Listen -LocalPort $porta -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { return [int]$c.OwningProcess }
  } catch {}
  return 0
}
function Vivo([int]$id) {
  if ($id -le 0) { return $false }
  return [bool](Get-Process -Id $id -ErrorAction SilentlyContinue)
}
function AgenteDizLigado {
  try { return ((Get-Content $estadoArq -Raw -Encoding UTF8) -match '^\W*ligado') } catch { return $false }
}
function Hora { return (Get-Date -Format 'HH:mm:ss') }

try {
  # 1. espera os dois subirem (ate 6 minutos)
  $node = 0
  $java = 0
  $limite = (Get-Date).AddMinutes(6)
  while ((Get-Date) -lt $limite) {
    if (-not (SouEu)) { exit }
    $node = Dono 3000
    $java = Dono 25565
    if ($node -gt 0 -and $java -gt 0) { break }
    Start-Sleep -Seconds 2
  }
  if ($node -le 0 -or $java -le 0) { exit }
  $inicio = Get-Date

  # 2. vigia: nos primeiros 15 minutos, a cada segundo, anotando processos que nascem e morrem; depois, a cada 3 s, so os dois
  $hist = New-Object System.Collections.ArrayList
  $antes = @{}
  $morteNode = ''
  $morteJava = ''
  while ($true) {
    if (-not (SouEu)) { exit }
    $intenso = ((Get-Date) -lt $inicio.AddMinutes(15))
    if ($intenso) {
      $agora = @{}
      foreach ($p in (Get-CimInstance Win32_Process)) {
        $id = [int]$p.ProcessId
        $agora[$id] = $p.Name
        if ($antes.Count -gt 0 -and -not $antes.ContainsKey($id)) {
          $cmd = "$($p.CommandLine)"
          if ($cmd.Length -gt 200) { $cmd = $cmd.Substring(0, 200) }
          [void]$hist.Add("$(Hora) NASCEU $id $($p.Name) (pai $($p.ParentProcessId)) $cmd")
        }
      }
      if ($antes.Count -gt 0) {
        foreach ($id in @($antes.Keys)) {
          if (-not $agora.ContainsKey($id)) { [void]$hist.Add("$(Hora) MORREU $id $($antes[$id])") }
        }
      }
      $antes = $agora
      while ($hist.Count -gt 800) { $hist.RemoveAt(0) }
    }
    $nodeVivo = Vivo $node
    $javaVivo = Vivo $java
    if (-not $nodeVivo -and $morteNode -eq '') { $morteNode = Hora }
    if (-not $javaVivo -and $morteJava -eq '') { $morteJava = Hora }
    if (-not $nodeVivo -or -not $javaVivo) {
      Start-Sleep -Seconds 3
      if (-not (Vivo $node) -and $morteNode -eq '') { $morteNode = Hora }
      if (-not (Vivo $java) -and $morteJava -eq '') { $morteJava = Hora }
      if (-not (SouEu)) { exit }
      if (-not (AgenteDizLigado)) { exit }

      # queda de verdade: fotografa
      $arq = Join-Path $pasta ("queda-{0}.txt" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
      & {
        "== QUEDA detectada pela sentinela em $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        "servidor ligado desde (visto pela sentinela): $($inicio.ToString('HH:mm:ss'))"
        "painel (node, processo $node): morreu as '$morteNode' (vazio = ainda vivo)"
        "minecraft (java, processo $java): morreu as '$morteJava' (vazio = ainda vivo)"
        "== processos que nasceram e morreram (so nos primeiros 15 min depois de ligar)"
        $hist
        "== processos agora"
        Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(node|java|javaw|cmd|powershell|pwsh|conhost|taskkill|playit|packwiz|cloudflared|docker|wsl|schtasks|wscript|cscript)' } |
          Select-Object ProcessId, ParentProcessId, Name, CreationDate, @{n='Comando';e={ if ($_.CommandLine) { $_.CommandLine.Substring(0, [Math]::Min(130, $_.CommandLine.Length)) } }} |
          Sort-Object CreationDate | Format-Table -AutoSize | Out-String -Width 240
        "== memoria"
        $so = Get-CimInstance Win32_OperatingSystem
        "livre: {0:N0} MB de {1:N0} MB; paginacao livre: {2:N0} MB" -f ($so.FreePhysicalMemory / 1024), ($so.TotalVisibleMemorySize / 1024), ($so.FreeVirtualMemory / 1024)
        "== eventos do Windows dos ultimos 10 min (Sistema e Aplicativo, todos os niveis)"
        $desde = (Get-Date).AddMinutes(-10)
        Get-WinEvent -FilterHashtable @{ LogName = 'System', 'Application'; StartTime = $desde } -MaxEvents 300 | Sort-Object TimeCreated | ForEach-Object {
          $m = ("$($_.Message)" -replace '\s+', ' ')
          "{0:HH:mm:ss} [{1}] {2} #{3}: {4}" -f $_.TimeCreated, $_.LevelDisplayName, $_.ProviderName, $_.Id, $m.Substring(0, [Math]::Min(240, $m.Length))
        }
        "== Windows Defender, ultimos 10 min"
        Get-WinEvent -FilterHashtable @{ LogName = 'Microsoft-Windows-Windows Defender/Operational'; StartTime = $desde } -MaxEvents 50 | Sort-Object TimeCreated | ForEach-Object {
          $m = ("$($_.Message)" -replace '\s+', ' ')
          "{0:HH:mm:ss} #{1}: {2}" -f $_.TimeCreated, $_.Id, $m.Substring(0, [Math]::Min(300, $m.Length))
        }
        "== fim do log do painel"
        Get-Content (Join-Path $raiz 'logs\painel.log') -Tail 20
        "== fim do log do servidor"
        Get-Content (Join-Path $raiz 'server\logs\latest.log') -Tail 12
      } *> $arq

      # pede o religamento na hora, se ainda nao pediu 2 vezes em 30 min e o agente esta livre
      $recentes = @()
      if (Test-Path $religArq) {
        foreach ($l in (Get-Content $religArq)) {
          $d = [datetime]::MinValue
          if ([datetime]::TryParse($l, [ref]$d)) { if ($d -gt (Get-Date).AddMinutes(-30)) { $recentes += $l } }
        }
      }
      $pediu = $false
      if ($recentes.Count -lt 2 -and (SouEu) -and (AgenteDizLigado) -and -not (Test-Path $pedido)) {
        $recentes += (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
        Set-Content -Path $religArq -Value $recentes -Encoding ASCII
        Set-Content -Path $pedido -Value 'reiniciar' -Encoding ASCII
        $pediu = $true
      }
      $texto = "sentinela: queda (painel '$morteNode', minecraft '$morteJava') - foto em $arq"
      if ($pediu) { $texto = $texto + ' - pedi reiniciar' } else { $texto = $texto + ' - nao pedi reiniciar (limite ou agente ocupado); a vigia cuida' }
      Add-Content -Path $eventos -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $texto" -Encoding UTF8
      exit
    }
    if ($intenso) { Start-Sleep -Milliseconds 800 } else { Start-Sleep -Seconds 3 }
  }
} catch {
  try { Add-Content -Path $eventos -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') sentinela: erro $_" -Encoding UTF8 } catch {}
}
