@echo off
setlocal

REM ── UAC ELEVATION CHECK ──────────────────────────────────────────
REM Ghost needs admin rights to modify the hosts file.
REM If not already elevated, relaunch this script as Administrator.
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -NoProfile -Command ^
        "Start-Process cmd -ArgumentList '/c cd /d \"%~dp0\" && \"%~f0\"' -Verb RunAs"
    exit /b
)
REM ─────────────────────────────────────────────────────────────────

set "LIB_DIR=..\lib"
set "OUT_DIR=..\out"
set "MAIN_CLASS=com.ghost.Main"

echo Running Ghost with optimized JVM settings...

REM JVM Optimization Flags:
REM -XX:ActiveProcessorCount=2        : Limit JVM to use 2 CPU cores
REM -XX:+UseParallelGC                : Use parallel garbage collector for better multi-core performance
REM -XX:ParallelGCThreads=2           : Use 2 threads for garbage collection
REM -Xms256m -Xmx512m                 : Set initial/max heap size to prevent excessive memory usage
REM These settings ensure smooth screen sharing while leaving CPU for other tasks

java --module-path "%LIB_DIR%" ^
    --add-modules javafx.controls,javafx.fxml ^
    -cp "%OUT_DIR%;%LIB_DIR%\*" ^
    -XX:ActiveProcessorCount=2 ^
    -XX:+UseParallelGC ^
    -XX:ParallelGCThreads=2 ^
    -Xms256m ^
    -Xmx512m ^
    %MAIN_CLASS%

endlocal
pause
