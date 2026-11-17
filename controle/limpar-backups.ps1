# Limpeza dos backups em camadas (chamado pelo desligar-frx.bat).
# Fica: os 7 mais recentes; o ultimo de cada dia dos ultimos 7 dias; o ultimo de cada semana das ultimas 5 semanas.
$pasta = Join-Path (Split-Path $PSScriptRoot -Parent) 'backups'
foreach ($p in 'mundo-*.zip', 'painel-*.sql') {
    $todos = @(Get-ChildItem $pasta -Filter $p | Sort-Object LastWriteTime -Descending)
    $ficam = New-Object System.Collections.Generic.HashSet[string]
    $todos | Select-Object -First 7 | ForEach-Object { [void]$ficam.Add($_.FullName) }
    $agora = Get-Date
    $todos | Where-Object { $_.LastWriteTime -gt $agora.AddDays(-7) } | Group-Object { $_.LastWriteTime.ToString('yyyyMMdd') } |
        ForEach-Object { [void]$ficam.Add(($_.Group | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName) }
    $cal = [System.Globalization.CultureInfo]::InvariantCulture.Calendar
    $todos | Where-Object { $_.LastWriteTime -gt $agora.AddDays(-35) } |
        Group-Object { '{0}-{1}' -f $_.LastWriteTime.Year, $cal.GetWeekOfYear($_.LastWriteTime, 'FirstFourDayWeek', 'Monday') } |
        ForEach-Object { [void]$ficam.Add(($_.Group | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName) }
    $todos | Where-Object { -not $ficam.Contains($_.FullName) } | Remove-Item -Force
}
