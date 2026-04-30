# Testing

## Current State
- No automated test suite (JUnit/TestNG) detected in the project structure.
- Testing appears to be manual, involving running the application and verifying functionality through the UI.

## Testing Strategy (Proposed)
- **Unit Testing**: Implement JUnit tests for logic-heavy classes like `AttendanceTracker` and `HostsFileManager`.
- **Integration Testing**: Test `DatabaseManager` with a temporary in-memory database or a test `.db` file.
- **Network Testing**: Mock `CommandPacket` exchanges to test `GhostServer` and `GhostClient` logic without requiring multiple machines.
