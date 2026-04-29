@echo off
setlocal enabledelayedexpansion

set "SRC_DIR=..\src\main\java"
set "LIB_DIR=..\lib"
set "OUT_DIR=..\out"
set "DIST_DIR=..\dist"
set "RES_DIR=..\src\main\resources"
set "PY_DIR=..\python_modules"
set "MAIN_CLASS=com.ghost.Main"

echo ========================================
echo   GHOST DIST GENERATOR
echo ========================================

echo [0/3] Closing running instances...
taskkill /F /IM javaw.exe /T >nul 2>&1
taskkill /F /IM GHOST.exe /T >nul 2>&1

echo [1/3] Cleaning and Rebuilding...
if exist "%DIST_DIR%" rd /s /q "%DIST_DIR%"
if exist "%OUT_DIR%" rd /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"
mkdir "%DIST_DIR%"

REM Compile
dir /s /B "%SRC_DIR%\*.java" > sources.txt
javac -d "%OUT_DIR%" --module-path "%LIB_DIR%" --add-modules javafx.controls,javafx.fxml -cp "%LIB_DIR%\*" @sources.txt
del sources.txt

REM Copy Resources
xcopy /S /Y /I "%RES_DIR%" "%OUT_DIR%" >nul

echo [2/3] Creating GHOST.jar...
echo Main-Class: %MAIN_CLASS% > manifest.txt
echo Class-Path: . >> manifest.txt
jar cvfm "%DIST_DIR%\GHOST.jar" manifest.txt -C "%OUT_DIR%" . >nul
del manifest.txt

echo [3/3] Copying dependencies to dist folder...
REM Copy lib folder
xcopy /S /Y /I "%LIB_DIR%" "%DIST_DIR%\lib" >nul
REM Copy python modules
xcopy /S /Y /I "%PY_DIR%" "%DIST_DIR%\python_modules" >nul

echo.
echo ---------------------------------------------------
echo SUCCESS! Your 'dist' folder is ready.
echo Locations:
echo - JAR: dist\GHOST.jar
echo - LIBS: dist\lib\
echo - PYTHON: dist\python_modules\
echo.
echo NOW: Open Launch4j and build your EXE into this folder.
echo ---------------------------------------------------
pause
