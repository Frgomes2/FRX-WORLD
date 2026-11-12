# Mantem o AutoModpack do servidor completo:
# - copia para server\automodpack\host-modpack\main\mods os mods que so existem no cliente
#   (ex.: embeddium), que o AutoModpack nao acharia em server\mods;
# - deixa uma copia do jar do AutoModpack em amigos\ para o kit dos amigos.
$raiz    = Split-Path -Parent $PSScriptRoot
$srvMods = Join-Path $raiz 'server\mods'
$cliMods = Join-Path $raiz 'cliente\jogo\mods'
$extra   = Join-Path $raiz 'server\automodpack\host-modpack\main\mods'
$amigos  = Join-Path $raiz 'amigos'

if (-not (Test-Path $cliMods)) { exit 0 }
New-Item -ItemType Directory -Force -Path $extra | Out-Null

$noServidor = @{}
Get-ChildItem $srvMods -Filter *.jar -EA 0 | ForEach-Object { $noServidor[$_.Name] = $true }
$soCliente = @(Get-ChildItem $cliMods -Filter *.jar | Where-Object { -not $noServidor.ContainsKey($_.Name) })

# remove versoes antigas que sairam do pack
Get-ChildItem $extra -Filter *.jar -EA 0 | Where-Object { $soCliente.Name -notcontains $_.Name } | Remove-Item -Force
foreach ($j in $soCliente) {
  $destino = Join-Path $extra $j.Name
  if (-not (Test-Path $destino)) { Copy-Item $j.FullName $destino }
}

$amp = Get-ChildItem $srvMods -Filter 'automodpack*.jar' -EA 0 | Select-Object -First 1
if ($amp) {
  Get-ChildItem $amigos -Filter 'automodpack*.jar' -EA 0 | Where-Object { $_.Name -ne $amp.Name } | Remove-Item -Force
  Copy-Item $amp.FullName (Join-Path $amigos $amp.Name) -Force
}
"so-cliente: " + ($soCliente.Name -join ', ')
