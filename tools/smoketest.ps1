# Смоук-тест сервера одной командой (карточка 008).
# Запуск из корня репозитория: powershell -ExecutionPolicy Bypass -File tools/smoketest.ps1
# PowerShell 5, без Python. Поднимает сервер через ./gradlew.bat runServer, гоняет проверки по RCON
# и пишет run/smoke-report.txt (PASS/FAIL по каждой строке). Код выхода 0 = всё прошло, 1 = есть ошибки.

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root "run"
$logFile = Join-Path $runDir "smoke-server.log"
$latestLog = Join-Path $runDir "logs\latest.log"
$reportFile = Join-Path $runDir "smoke-report.txt"
$checksFile = Join-Path $PSScriptRoot "smoke_checks.txt"
$rconPort = 25575
$rconPassword = "sz"
$startupMinutes = 15

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$levelName = "smoke_$stamp"

# Читает файл, который пишется другим процессом (лог сервера).
function Get-LogText([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return "" }
    $fs = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $sr = New-Object System.IO.StreamReader($fs, (New-Object System.Text.UTF8Encoding($false)))
        return $sr.ReadToEnd()
    } finally {
        $sr.Dispose()
        $fs.Dispose()
    }
}

function Write-Text([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}
# --- Свой маленький клиент Source RCON на System.Net.Sockets.TcpClient ---
$script:rconId = 0

function Send-RconPacket($stream, [int]$id, [int]$type, [string]$body) {
    $payload = [System.Text.Encoding]::UTF8.GetBytes($body)
    $len = 4 + 4 + $payload.Length + 2
    $buf = New-Object byte[] (4 + $len)
    [System.BitConverter]::GetBytes([int]$len).CopyTo($buf, 0)
    [System.BitConverter]::GetBytes([int]$id).CopyTo($buf, 4)
    [System.BitConverter]::GetBytes([int]$type).CopyTo($buf, 8)
    if ($payload.Length -gt 0) { [System.Array]::Copy($payload, 0, $buf, 12, $payload.Length) }
    $stream.Write($buf, 0, $buf.Length)
    $stream.Flush()
}

function Read-Exactly($stream, [byte[]]$buffer, [int]$count) {
    $total = 0
    while ($total -lt $count) {
        $n = $stream.Read($buffer, $total, $count - $total)
        if ($n -le 0) { break }
        $total += $n
    }
    return $total
}

function Read-RconPacket($stream) {
    $lenBuf = New-Object byte[] 4
    if ((Read-Exactly $stream $lenBuf 4) -lt 4) { return $null }
    $len = [System.BitConverter]::ToInt32($lenBuf, 0)
    if ($len -lt 10 -or $len -gt 65536) { return $null }
    $body = New-Object byte[] $len
    if ((Read-Exactly $stream $body $len) -lt $len) { return $null }
    $id = [System.BitConverter]::ToInt32($body, 0)
    $type = [System.BitConverter]::ToInt32($body, 4)
    $payloadLen = $len - 10
    $payload = ""
    if ($payloadLen -gt 0) {
        $payload = [System.Text.Encoding]::UTF8.GetString($body, 8, $payloadLen).TrimEnd([char]0)
    }
    return [pscustomobject]@{ Id = $id; Type = $type; Payload = $payload }
}

function Invoke-Rcon($stream, [string]$command) {
    $script:rconId = $script:rconId + 1
    Send-RconPacket $stream $script:rconId 2 $command
    $sb = New-Object System.Text.StringBuilder
    while ($true) {
        $pkt = Read-RconPacket $stream
        if ($null -eq $pkt) { break }
        [void]$sb.Append($pkt.Payload)
        Start-Sleep -Milliseconds 100
        if (-not $stream.DataAvailable) { break }
    }
    return $sb.ToString()
}
# --- 1. Готовим run/ ---
if (-not (Test-Path -LiteralPath $runDir)) { New-Item -ItemType Directory -Path $runDir | Out-Null }
Write-Text (Join-Path $runDir "eula.txt") "eula=true`n"
$props = @(
    "level-type=stalkerzone:zone",
    "enable-rcon=true",
    "rcon.password=$rconPassword",
    "rcon.port=$rconPort",
    "max-tick-time=-1",
    "level-name=$levelName"
)
Write-Text (Join-Path $runDir "server.properties") (($props -join "`n") + "`n")

# Убираем старые логи, чтобы «Done (» из прошлого запуска не дал ложное срабатывание.
Remove-Item -LiteralPath $logFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $latestLog -Force -ErrorAction SilentlyContinue

# --- 2. Запускаем сервер в фоне ---
$env:JAVA_TOOL_OPTIONS = "-Dstalkerzone.scenario=kordon"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = '/c gradlew.bat runServer > "' + $logFile + '" 2>&1'
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$proc = [System.Diagnostics.Process]::Start($psi)

Write-Host "Сервер запускается (лог: $logFile), ждём 'Done (' (таймаут $startupMinutes мин)..."

$deadline = (Get-Date).AddMinutes($startupMinutes)
$ready = $false
$crash = $false
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) { $crash = $true; break }
    $txt = (Get-LogText $logFile) + "`n" + (Get-LogText $latestLog)
    if ($txt -match 'Done \(') { $ready = $true; break }
    if ($txt -match 'Crash|Exception in server tick loop|BUILD FAILED|FAILURE: Build failed') { $crash = $true; break }
    Start-Sleep -Seconds 2
}

$lines = @()
$results = @()
$failed = 0

if (-not $ready -or $crash) {
    $failed = 1
    if ($crash) { $lines += "Запуск сервера: FAIL (сервер упал при старте)" }
    else { $lines += "Запуск сервера: FAIL (таймаут $startupMinutes минут)" }
    if (-not $proc.HasExited) { & taskkill /PID $proc.Id /T /F 2>$null | Out-Null }
}
else {
    $lines += "Запуск сервера: PASS (готов к работе)"
    # --- 3. Подключаемся по RCON (с повторами — порт поднимается сразу после старта) ---
    $tcp = $null
    $stream = $null
    $connected = $false
    for ($attempt = 1; $attempt -le 30 -and -not $connected; $attempt++) {
        try {
            if ($stream -ne $null) { $stream.Dispose(); $stream = $null }
            if ($tcp -ne $null) { $tcp.Dispose(); $tcp = $null }
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", $rconPort)
            $stream = $tcp.GetStream()
            $stream.ReadTimeout = 15000

            $script:rconId = 1
            Send-RconPacket $stream $script:rconId 3 $rconPassword
            $auth = Read-RconPacket $stream
            if ($null -ne $auth -and $auth.Id -ne -1) { $connected = $true }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if (-not $connected) { throw "RCON: не удалось подключиться к 127.0.0.1:$rconPort" }

    try {
        foreach ($raw in Get-Content -LiteralPath $checksFile -Encoding UTF8) {
            $line = $raw.Trim()
            if ($line -eq "" -or $line.StartsWith("#")) { continue }
            $sep = $line.IndexOf("|")
            if ($sep -lt 0) { continue }
            $cmd = $line.Substring(0, $sep).Trim()
            $frag = $line.Substring($sep + 1).Trim()

            $resp = Invoke-Rcon $stream $cmd

            $neg = $frag.StartsWith("!")
            if ($neg) { $needle = $frag.Substring(1) } else { $needle = $frag }
            $found = $resp.IndexOf($needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
            if ($neg) { $ok = -not $found } else { $ok = $found }

            $mark = "PASS"
            if (-not $ok) { $mark = "FAIL"; $failed++ }
            $results += "$mark  $cmd  ($frag)"
            Write-Host "$mark  $cmd  ($frag)"
            if (-not $ok) {
                $short = $resp -replace "`r?`n", " | "
                $results += "     ответ: $short"
            }
        }

        # --- 4. Останавливаем сервер ---
        Invoke-Rcon $stream "stop" | Out-Null
    }
    finally {
        if ($stream -ne $null) { $stream.Dispose() }
        if ($tcp -ne $null) { $tcp.Dispose() }
    }
    $lines += "Проверки RCON: " + $(if ($failed -eq 0) { "PASS" } else { "FAIL ($failed)" })
}
# Ждём, пока сервер (и gradle) завершатся после stop.
if (-not $proc.HasExited) {
    [void]$proc.WaitForExit(120000)
    if (-not $proc.HasExited) {
        & taskkill /PID $proc.Id /T /F 2>$null | Out-Null
        $lines += "Сервер не остановился сам — прибит принудительно."
        $failed++
    }
}

# --- 5. Отчёт ---
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("Смоук-тест Stalker Zone — $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
[void]$sb.AppendLine("Сценарий: kordon, мир: $levelName")
foreach ($l in $lines) { [void]$sb.AppendLine($l) }
[void]$sb.AppendLine("== Проверки ==")
foreach ($r in $results) { [void]$sb.AppendLine($r) }

$grep = @(((Get-LogText $logFile) -split "`r?`n") | Where-Object { $_ -match 'ERROR|stalkerzone' })
[void]$sb.AppendLine("== Строки лога сервера (ERROR / stalkerzone) ==")
if ($grep.Count -eq 0) { [void]$sb.AppendLine("(нет)") }
else { foreach ($g in $grep) { [void]$sb.AppendLine($g) } }

if ($failed -eq 0) { $overall = "PASS" } else { $overall = "FAIL" }
[void]$sb.AppendLine("Итог: $overall")

Write-Text $reportFile $sb.ToString()
Write-Host ""
Write-Host (Get-LogText $reportFile)

if ($failed -gt 0) { exit 1 } else { exit 0 }



