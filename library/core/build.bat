@echo off

where bash >nul 2>nul
if errorlevel 1 (
    echo AmneziaWG core builds must use library/core/build.sh through Git Bash or a compatible bash shell.
    exit /b 1
)

bash build.sh
