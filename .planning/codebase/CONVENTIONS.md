# Coding Conventions

## Naming
- **Classes**: PascalCase (e.g., `DatabaseManager`)
- **Methods/Variables**: camelCase (e.g., `migrateSchema`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DB_URL`)
- **Packages**: lowercase, reverse domain notation (`com.ghost.*`)

## Java Standards
- **Java Version**: Targeted for Java 11+ (using `var` is likely supported, though not seen yet).
- **Error Handling**: Uses try-with-resources for JDBC connections and I/O.
- **UI Framework**: JavaFX with code-based UI definition (FXML usage not detected in root list).

## Documentation
- Minimal Javadoc seen in current files.
- Inline comments used for explaining logic (e.g., in `Main.java`).

## Networking Protocol
- Uses a `CommandPacket` object for communication.
- Packet-based serialization over Sockets.
