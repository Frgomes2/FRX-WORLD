@echo off
chcp 65001 >nul
rem ===== Cria na Area de Trabalho os atalhos "FRX-WORLD" e "Desligar FRX-WORLD" (rode uma vez) =====
set "RAIZ=%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$raiz = (Resolve-Path '%RAIZ%').Path;" ^
  "$desk = [Environment]::GetFolderPath('Desktop');" ^
  "$sh = New-Object -ComObject WScript.Shell;" ^
  "$a = $sh.CreateShortcut((Join-Path $desk 'FRX-WORLD.lnk'));" ^
  "$a.TargetPath = Join-Path $raiz 'iniciar-frx.bat'; $a.WorkingDirectory = $raiz;" ^
  "$a.IconLocation = (Join-Path $raiz 'atalhos\frx-world.ico'); $a.WindowStyle = 1; $a.Description = 'Liga banco, pack e painel do FRX-WORLD'; $a.Save();" ^
  "$b = $sh.CreateShortcut((Join-Path $desk 'Desligar FRX-WORLD.lnk'));" ^
  "$b.TargetPath = Join-Path $raiz 'desligar-frx.bat'; $b.WorkingDirectory = $raiz;" ^
  "$b.IconLocation = 'shell32.dll,27'; $b.Description = 'Para o servidor salvando o mundo e fecha tudo'; $b.Save();" ^
  "Write-Host ('Atalhos criados em ' + $desk)"
pause
