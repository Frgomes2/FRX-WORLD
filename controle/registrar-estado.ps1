# Fotografa o estado do PC logo antes de cada desligamento (inclusive os da vigia, quando o servidor cai sozinho):
# quais processos do FRX-WORLD ainda existem, memoria livre e os avisos do Windows da ultima meia hora.
# Serve para descobrir por que o painel e o servidor as vezes morrem juntos sem deixar log. Nunca interrompe o desligamento.
$ErrorActionPreference = 'SilentlyContinue'
$raiz = Split-Path -Parent $PSScriptRoot
$pasta = Join-Path $raiz 'logs\quedas'
New-Item -ItemType Directory -Force -Path $pasta | Out-Null
$arq = Join-Path $pasta ("estado-{0}.txt" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
& {
  "== $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  "== porta 25565 / 3000 / 8080 ouvindo"
  Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 25565, 3000, 8080 } | Select-Object LocalPort, OwningProcess | Format-Table -AutoSize | Out-String
  "== processos (node, java, cmd, playit, packwiz, cloudflared)"
  Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(node|java|javaw|cmd|playit|packwiz|cloudflared)' } |
    Select-Object ProcessId, ParentProcessId, Name, CreationDate, @{n='Comando';e={ if ($_.CommandLine) { $_.CommandLine.Substring(0, [Math]::Min(110, $_.CommandLine.Length)) } }} |
    Sort-Object CreationDate | Format-Table -AutoSize | Out-String -Width 220
  "== memoria"
  $so = Get-CimInstance Win32_OperatingSystem
  "livre: {0:N0} MB de {1:N0} MB; arquivo de paginacao livre: {2:N0} MB" -f ($so.FreePhysicalMemory / 1024), ($so.TotalVisibleMemorySize / 1024), ($so.FreeVirtualMemory / 1024)
  "== ultimo arranque do Windows: $($so.LastBootUpTime)"
  "== eventos do Windows (Sistema e Aplicativo) dos ultimos 30 min: erros, avisos e encerramentos"
  $desde = (Get-Date).AddMinutes(-30)
  Get-WinEvent -FilterHashtable @{ LogName = 'System', 'Application'; StartTime = $desde } -MaxEvents 400 |
    Where-Object { $_.Level -in 1, 2, 3 -or $_.ProviderName -match 'Application Error|Windows Error Reporting|User32|Winlogon|Resource-Exhaustion|Kernel-Power|Application Hang|Docker|WSL|Hyper-V' } |
    Sort-Object TimeCreated | ForEach-Object {
      $m = ($_.Message -replace '\s+', ' ')
      "{0:HH:mm:ss} [{1}] {2} #{3}: {4}" -f $_.TimeCreated, $_.LevelDisplayName, $_.ProviderName, $_.Id, $m.Substring(0, [Math]::Min(260, $m.Length))
    }
  "== fim do log do painel"
  Get-Content (Join-Path $raiz 'logs\painel.log') -Tail 15
  "== fim do log do servidor"
  Get-Content (Join-Path $raiz 'server\logs\latest.log') -Tail 8
} *> $arq
# guarda so as 30 fotos mais recentes
Get-ChildItem $pasta -Filter 'estado-*.txt' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 30 | Remove-Item -Force
