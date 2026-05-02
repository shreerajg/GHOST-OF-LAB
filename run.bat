@echo off
SETLOCAL EnableDelayedExpansion

:: Configuration
set "SRC_DIR=src\main\java"
set "RES_DIR=src\main\resources"
set "LIB_DIR=lib"
set "OUT_DIR=out"
set "MAIN_CLASS=com.ghost.Main"

echo ========================================
echo   Ghost of Lab - Build ^& Run
echo ========================================

:: 1. Clean and Setup
echo [1/3] Preparing output directory...
if exist "%OUT_DIR%" rd /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

:: 2. Compile
echo [2/3] Compiling source code...
:: Create a list of all java files to compile
dir /s /b "%SRC_DIR%\*.java" > sources.txt
javac --module-path "%LIB_DIR%" --add-modules javafx.controls,javafx.fxml -d "%OUT_DIR%" -cp "%LIB_DIR%\*" @sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Compilation failed!
    if exist sources.txt del sources.txt
    pause
    exit /b %ERRORLEVEL%
)
del sources.txt

:: 3. Copy Resources
if exist "%RES_DIR%" (
    echo [3/3] Copying resources...
    xcopy /s /e /y "%RES_DIR%\*" "%OUT_DIR%\" > nul
)

:: 4. Run
echo.
echo Launching Application...
echo ----------------------------------------
java --module-path "%LIB_DIR%" --add-modules javafx.controls,javafx.fxml -cp "%OUT_DIR%;%LIB_DIR%\*" %MAIN_CLASS%

echo.
echo Application terminated.
pause
