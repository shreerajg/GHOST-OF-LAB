@echo off
setlocal

set "LIB_DIR=..\lib"
set "OUT_DIR=..\out"
set "MAIN_CLASS=com.ghost.Main"

echo Running Ghost...

java --module-path "%LIB_DIR%" ^
    --add-modules javafx.controls,javafx.fxml ^
    -cp "%OUT_DIR%;%LIB_DIR%\*" ^
    %MAIN_CLASS%

endlocal
pause
