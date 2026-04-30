# Codebase Concerns

## Security
- **Plain Text Passwords**: Passwords in the `users` table are stored in plain text. They should be hashed (e.g., using BCrypt).
- **Default Credentials**: `admin/admin123` is hardcoded as the default admin.
- **Privileged Operations**: Modifying the `hosts` file requires the application to run with administrator/root privileges. There is no explicit check or elevation request logic visible in `Main`.

## Reliability
- **Hosts File Restoration**: Relies on a JVM shutdown hook to restore the hosts file. If the process is killed forcefully (SIGKILL) or the OS crashes, the student's hosts file might remain blocked.
- **Network Discovery**: UDP broadcast might be blocked by certain firewalls or across different subnets/VLANs in a university/school network.
- **Database Migrations**: Simple `ALTER TABLE` in try-catch blocks is fragile for long-term schema evolution.

## Maintenance
- **Fat UI Classes**: `AdminDashboard.java` and `StudentDashboard.java` are quite large (29KB and 32KB), suggesting logic is tightly coupled with UI code.
- **Logging**: Project uses `System.out.println`. Switching to a logging framework (SLF4J/Logback) would improve observability.
- **Testing**: Complete lack of automated tests makes refactoring risky.

## Technical Debt
- **Python Bridge**: Dependence on external Python scripts adds a runtime dependency (Python must be installed and in PATH).
- **Swing/JavaFX Mix**: The metadata mentions `StudentRegistrationView.java`, and `Main` uses JavaFX. Need to ensure UI consistency.
