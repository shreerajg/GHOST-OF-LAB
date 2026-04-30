# Integrations

## Internal Integrations

### Python Bridge (`com.ghost.util.PythonBridge`)
- **Purpose**: Bridge between Java application and Python scripts for system-level operations.
- **Workflow**: Java calls Python scripts using `ProcessBuilder` or similar.
- **Modules**:
    - `executor.py`: Executes specific system commands.
    - `watchdog.py`: Monitors system state or ensures certain processes remain active/inactive.
    - `ai_interface.py`: Likely a placeholder or minimal interface for future AI capabilities.

### Networking Services
- **Discovery Service**: Uses UDP broadcast on a specific port to allow Students and Admins to find each other on a LAN without manual IP entry.
- **Server/Client**: TCP-based communication for sending commands (block, unblock, capture screen, etc.).

## External Integrations

### OS Hosts File (`com.ghost.util.HostsFileManager`)
- **Integration**: Directly modifies `C:\Windows\System32\drivers\etc\hosts` (on Windows).
- **Function**: Used to redirect domains to `127.0.0.1`, effectively blocking internet access or specific sites during lab sessions.

### File System
- **Database**: Local SQLite file (`ghost.db`).
- **Media**: `ScreenCapture` saves or transmits images.
