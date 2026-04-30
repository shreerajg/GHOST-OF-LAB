# Architecture

## System Overview
GHOST (Ghost of Lab) is a client-server management system designed for computer labs. It allows administrators to monitor and control student machines in real-time.

## Design Patterns
- **Model-View-Controller (MVC)**:
    - **Models**: `User`, `StudentAttendance`.
    - **Views**: JavaFX FXML or code-based views (`LoginView`, `AdminDashboard`, `StudentDashboard`).
    - **Controllers**: Logic is currently embedded in view classes (e.g., `AdminDashboard.java`) or handled by service classes.
- **Singleton / Manager Pattern**: `DatabaseManager`, `SessionManager`, `HostsFileManager`, `SystemTrayManager` act as centralized managers for specific subsystems.
- **Client-Server Architecture**:
    - **Admin**: Acts as a controller/server that sends commands to student nodes.
    - **Student**: Acts as a client that listens for commands and reports status/attendance.

## Key Subsystems

### 1. Persistence Layer
Uses SQLite for storing user credentials, roles, and attendance records. `DatabaseManager` handles schema initialization and migrations.

### 2. Networking & Discovery
Decouples discovery from communication. `DiscoveryService` handles finding nodes on the network, while `GhostServer` and `GhostClient` handle the actual data transmission using a command-packet protocol.

### 3. System Control
A combination of Java logic and Python scripts are used for privileged operations like modifying the hosts file or performing screen captures.

### 4. UI Layer
Built with JavaFX. Employs a dashboard-style interface for both students and admins. Features include a system tray integration for background persistence.
