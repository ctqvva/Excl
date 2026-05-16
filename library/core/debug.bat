@echo off

where bash >nul 2>nul
if errorlevel 1 (
    echo AmneziaWG debug core builds must use library/core/debug.sh through Git Bash or a compatible bash shell.
    exit /b 1
)

bash debug.sh
