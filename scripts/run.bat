@echo off
setlocal

REM ── UAC ELEVATION CHECK ──────────────────────────────────────────
REM Ghost needs admin rights to modify the hosts file.
REM Uses a temp VBScript (no PowerShell required) to trigger UAC.
REM SW_HIDE (0) = the elevated cmd window is completely invisible.
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Set UAC = CreateObject^("Shell.Application"^)   > "%temp%\ghost_elev.vbs"
    echo UAC.ShellExecute "cmd.exe", "/c cd /d ""%~sdp0"" && ""%~s0""", "", "runas", 0 >> "%temp%\ghost_elev.vbs"
    cscript //nologo "%temp%\ghost_elev.vbs"
    del "%temp%\ghost_elev.vbs" >nul 2>&1
    exit /b
)
REM ─────────────────────────────────────────────────────────────────

set "LIB_DIR=..\lib"
set "OUT_DIR=..\out"
set "MAIN_CLASS=com.ghost.Main"

REM Use javaw (no console window) and start /b so this cmd exits immediately
start "Ghost of Lab" /b javaw --module-path "%LIB_DIR%" ^
    --add-modules javafx.controls,javafx.fxml ^
    -cp "%OUT_DIR%;%LIB_DIR%\*" ^
    -XX:ActiveProcessorCount=2 ^
    -XX:+UseParallelGC ^
    -XX:ParallelGCThreads=2 ^
    -Xms256m ^
    -Xmx512m ^
    %MAIN_CLASS%

endlocal
