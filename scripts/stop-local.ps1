<#
  stop-local.ps1  -  stop everything started by run-local.ps1
#>
$ErrorActionPreference = "SilentlyContinue"
$root = Split-Path -Parent $PSScriptRoot
$pf   = Join-Path $root ".local-pids"

$stopped = 0
if (Test-Path $pf) {
  foreach ($id in ((Get-Content $pf) -split "\s+" | Where-Object { $_ })) {
    $p = Get-Process -Id ([int]$id) -ErrorAction SilentlyContinue
    if ($p) { Stop-Process -Id $p.Id -Force; Write-Host "stopped pid $id"; $stopped++ }
  }
  Remove-Item $pf
}

# fallback: any java process running a jar from this repo's services\ tree,
# plus the cmd.exe wrapper windows that launched them
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='cmd.exe'" |
  Where-Object { $_.CommandLine -and $_.CommandLine -match [regex]::Escape($root) -and $_.CommandLine -match "services\\" } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host "stopped pid $($_.ProcessId) ($($_.Name), fallback)"; $stopped++ }

if ($stopped -eq 0) { Write-Host "nothing to stop" } else { Write-Host "`n$stopped process(es) stopped" }
