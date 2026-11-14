# 启动本地 Chroma（数据落在本目录 data/）
# 用法：.\start.ps1            → 8000 端口
#       .\start.ps1 -Port 8001 → 换端口（记得同步改 .env 的 CHROMA_PORT）
param([int]$Port = 8000)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$data = Join-Path $root 'data'
New-Item -ItemType Directory -Force -Path $data | Out-Null

# 端口已被占用时直接提示，避免重复启动
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    $pidText = ($busy | Select-Object -First 1).OwningProcess
    Write-Host "端口 $Port 已被占用（PID $pidText），可能 Chroma 已经在运行。" -ForegroundColor Yellow
    Write-Host "自检：http://localhost:$Port/api/v2/heartbeat"
    exit 0
}

Write-Host "数据目录：$data"
Write-Host "服务地址：http://localhost:$Port  （Ctrl+C 停止）"
Write-Host ""

$chroma = Get-Command chroma -ErrorAction SilentlyContinue
if ($chroma) {
    & $chroma.Source run --path $data --port $Port
} else {
    Write-Host "未找到 chroma 命令，改用 python -m chromadb.cli.cli" -ForegroundColor Yellow
    python -m chromadb.cli.cli run --path $data --port $Port
}
