# Project Structure

```text
GHOST/
├── .planning/                  # Project planning and codebase mapping
├── lib/                        # Java dependencies (JARs)
├── out/                        # Compiled classes/output
├── python_modules/             # Python-based system utilities
│   ├── ai_interface.py
│   ├── executor.py
│   └── watchdog.py
├── scripts/                    # Maintenance or build scripts
├── src/main/java/com/ghost/    # Source root
│   ├── database/               # Data persistence
│   │   ├── DatabaseManager.java
│   │   └── User.java
│   ├── model/                  # Domain entities
│   │   └── StudentAttendance.java
│   ├── net/                    # Networking and discovery
│   │   ├── CommandPacket.java
│   │   ├── DiscoveryService.java
│   │   ├── GhostClient.java
│   │   └── GhostServer.java
│   ├── ui/                     # JavaFX View components
│   │   ├── AdminDashboard.java
│   │   ├── LoginView.java
│   │   ├── StudentDashboard.java
│   │   └── StudentRegistrationView.java
│   ├── util/                   # Shared utilities and managers
│   │   ├── AttendanceTracker.java
│   │   ├── Config.java
│   │   ├── HostsFileManager.java
│   │   ├── IconUtil.java
│   │   ├── PythonBridge.java
│   │   ├── ScreenCapture.java
│   │   ├── SessionManager.java
│   │   └── SystemTrayManager.java
│   └── Main.java               # Application entry point
└── ghost.db                    # SQLite database file (runtime)
```
