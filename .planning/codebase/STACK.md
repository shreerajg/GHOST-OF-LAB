# Technology Stack

## Core
- **Language**: Java 17+ (assumed from modern syntax and record-like usage)
- **Framework**: JavaFX (for GUI)
- **Runtime**: Java Virtual Machine (JVM)

## Data Storage
- **Database**: SQLite
- **Connection**: JDBC (`sqlite-jdbc`)
- **Persistence**: Local file `ghost.db`

## Networking
- **Discovery**: UDP Broadcast (via `DiscoveryService`)
- **Communication**: TCP Sockets (via `GhostServer` and `GhostClient`)
- **Protocol**: Custom `CommandPacket` based serialization/deserialization

## Integration
- **Python**: Interfaced via `PythonBridge`. Used for system-level controls (e.g., networking blocking, watchdog).
- **External Scripts**: `python_modules/` directory containing `ai_interface.py`, `executor.py`, and `watchdog.py`.

## Utilities
- **System Control**: `HostsFileManager` for modifying the OS hosts file (likely for blocking distractions).
- **UI Components**: `SystemTrayManager`, `IconUtil`.
- **Media**: `ScreenCapture` for monitoring student screens.
- **Session Management**: `SessionManager`, `AttendanceTracker`.
