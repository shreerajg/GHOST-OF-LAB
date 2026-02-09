# setup_dependencies.ps1
$libDir = "..\lib"
If (!(Test-Path $libDir)) { New-Item -ItemType Directory -Force -Path $libDir }

$baseUrl = "https://repo1.maven.org/maven2"

# List of URLs to download
$files = @(
    # SQLite
    "$baseUrl/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar",
    
    # Gson
    "$baseUrl/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",

    # JavaFX (Win)
    "$baseUrl/org/openjfx/javafx-controls/20.0.1/javafx-controls-20.0.1.jar",
    "$baseUrl/org/openjfx/javafx-controls/20.0.1/javafx-controls-20.0.1-win.jar",
    "$baseUrl/org/openjfx/javafx-graphics/20.0.1/javafx-graphics-20.0.1.jar",
    "$baseUrl/org/openjfx/javafx-graphics/20.0.1/javafx-graphics-20.0.1-win.jar",
    "$baseUrl/org/openjfx/javafx-base/20.0.1/javafx-base-20.0.1.jar",
    "$baseUrl/org/openjfx/javafx-base/20.0.1/javafx-base-20.0.1-win.jar",
    "$baseUrl/org/openjfx/javafx-fxml/20.0.1/javafx-fxml-20.0.1.jar",
    "$baseUrl/org/openjfx/javafx-fxml/20.0.1/javafx-fxml-20.0.1-win.jar"
)

Write-Host "Downloading dependencies to $libDir..."

foreach ($url in $files) {
    $filename = Split-Path $url -Leaf
    $outputPath = Join-Path $libDir $filename
    Write-Host "Downloading $filename..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $outputPath
    } catch {
        Write-Error "Failed to download $filename. Error: $_"
    }
}

Write-Host "Download complete. Check the lib folder."
