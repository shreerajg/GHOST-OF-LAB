# Setup Instructions for Ghost of Lab

Follow these steps to set up and run the Ghost of Lab application on your Windows machine.

## 1. Prerequisites

Before starting, ensure that you have a compatible Java Development Kit (JDK) installed:
- **JDK 17 or higher** is recommended.
- Ensure that the `java` and `javac` commands are available in your system's `PATH`.

You can verify your installation by opening a command prompt and typing:
```cmd
java -version
javac -version
```

## 2. Download Dependencies

The project relies on a few external libraries (JavaFX, Gson, and SQLite JDBC). To automatically download these into the `lib` folder:

1. Open **PowerShell**.
2. Navigate to the `scripts` directory of the project.
3. Run the dependency setup script:
   ```powershell
   cd path\to\GHOST\scripts
   .\setup_dependencies.ps1
   ```
4. Verify that a `lib` folder has been created in the root directory and contains `.jar` files.

## 3. Configure Windows Firewall (Important for WiFi)

Ghost of Lab uses TCP and UDP ports to communicate between the Admin and Student PCs. If you are on a WiFi network, Windows usually blocks this traffic by default.

1. Navigate to the `scripts` folder in File Explorer.
2. Right-click on **`fix_wifi_firewall.bat`**.
3. Select **"Run as administrator"**.
4. The script will open a terminal window, add the necessary inbound/outbound rules (TCP 5555, UDP 5556), and allow Java through the firewall. Press any key to close it once done.

*(Note: Both the Teacher/Admin PC and the Student PCs need to run this script before launching the app.)*

## 4. Run the Application

Once dependencies are downloaded and the firewall is configured, you are ready to launch the app!

1. Go to the root directory of the project (`GHOST`).
2. Double-click on **`run.bat`**.

This batch script will:
- Clean any previous builds.
- Compile all Java source files from `src/main/java`.
- Copy necessary UI assets from `src/main/resources`.
- Launch the Ghost of Lab application.

## Troubleshooting

- **Compilation Failed**: Ensure `setup_dependencies.ps1` successfully downloaded all `.jar` files into the `lib` folder.
- **Cannot Connect / Auto-Discovery Fails**: 
  - Ensure all PCs are on the exact same WiFi network.
  - Make sure your WiFi router does not have "AP Isolation" or "Client Isolation" turned on in its settings.
  - Verify that you ran the `fix_wifi_firewall.bat` script as an **Administrator** on both the host and client computers.
