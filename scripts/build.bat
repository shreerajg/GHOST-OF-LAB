@echo off
setlocal

set "SRC_DIR=..\src\main\java"
set "LIB_DIR=..\lib"
set "OUT_DIR=..\out"

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

echo Cleaning old build...
del /S /Q "%OUT_DIR%\*" >nul 2>&1

echo Compiling...
dir /s /B "%SRC_DIR%\*.java" > sources.txt

javac -d "%OUT_DIR%" ^
    --module-path "%LIB_DIR%" ^
    --add-modules javafx.controls,javafx.fxml ^
    -cp "%LIB_DIR%\*" ^
    @sources.txt

if %errorlevel% neq 0 (
    echo Compilation Failed!
    del sources.txt
    pause
    exit /b %errorlevel%
)

echo Copying resources...
xcopy /S /Y /I "..\src\main\resources" "%OUT_DIR%" >nul

echo Compilation Successful.
del sources.txt
endlocal
pause
