@echo off
chcp 65001 >nul
setlocal
set PORT=%~1
if "%PORT%"=="" set PORT=8000
set DATA=%~dp0data
if not exist "%DATA%" mkdir "%DATA%"

echo 数据目录：%DATA%
echo 服务地址：http://localhost:%PORT%  ^(Ctrl+C 停止^)
echo.

where chroma >nul 2>nul
if %errorlevel%==0 (
    chroma run --path "%DATA%" --port %PORT%
) else (
    echo 未找到 chroma 命令，改用 python -m chromadb.cli.cli
    python -m chromadb.cli.cli run --path "%DATA%" --port %PORT%
)
