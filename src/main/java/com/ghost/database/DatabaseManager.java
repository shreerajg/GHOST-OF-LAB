package com.ghost.database;

import com.ghost.util.Config;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    public static void init() {
        System.out.println("[DB] Connecting to: " + Config.DB_URL);
        try (Connection conn = DriverManager.getConnection(Config.DB_URL)) {
            if (conn != null) {
                System.out.println("[DB] Connected successfully.");

                // Set WAL mode: allows multiple readers + 1 writer concurrently.
                // This is important on shared drives (OneDrive) where multiple
                // processes might open the same file — WAL reduces lock contention.
                try (Statement wal = conn.createStatement()) {
                    wal.execute("PRAGMA journal_mode=WAL;");
                    wal.execute("PRAGMA busy_timeout=5000;"); // wait up to 5s for locks
                }

                String sql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL, " +
                        "password TEXT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "meta TEXT, " +
                        "roll_number INTEGER DEFAULT 0, " +
                        "class_name TEXT DEFAULT '', " +
                        "division TEXT DEFAULT ''" +
                        ");";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Migration: Add new columns if they don't exist (for existing databases)
                migrateSchema(conn);

                // Create default admin if not exists
                createDefaultAdmin(conn);
                System.out.println("[DB] Initialization complete.");
            }
        } catch (SQLException e) {
            // Print full stack trace so it's visible in console/logs
            System.err.println("[DB] FATAL: Database initialization failed: " + e.getMessage());
            System.err.println("[DB] DB URL was: " + Config.DB_URL);
            e.printStackTrace();
        }
    }

    private static void migrateSchema(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if columns exist and add if missing
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN roll_number INTEGER DEFAULT 0");
                System.out.println("Added roll_number column to users table");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN class_name TEXT DEFAULT ''");
                System.out.println("Added class_name column to users table");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN division TEXT DEFAULT ''");
                System.out.println("Added division column to users table");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
        } catch (SQLException e) {
            System.err.println("Schema migration error: " + e.getMessage());
        }
    }

    private static void createDefaultAdmin(Connection conn) throws SQLException {
        String checkSql = "SELECT count(*) FROM users WHERE role = 'ADMIN'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertSql = "INSERT INTO users(username, password, role, meta) VALUES(?, ?, ?, ?)";
                try (PreparedStatement desc = conn.prepareStatement(insertSql)) {
                    desc.setString(1, "admin");
                    desc.setString(2, "admin123"); // Default password
                    desc.setString(3, "ADMIN");
                    desc.setString(4, "{}");
                    desc.executeUpdate();
                    System.out.println("Default admin created.");
                }
            }
        }
    }

    public static User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(Config.DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role"),
                            rs.getString("meta"),
                            rs.getInt("roll_number"),
                            rs.getString("class_name"),
                            rs.getString("division"));
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
        }
        return null; // Login failed
    }

    public static boolean registerStudent(String username, String password, String meta,
            int rollNumber, String className, String division) {
        String sql = "INSERT INTO users(username, password, role, meta, roll_number, class_name, division) " +
                "VALUES(?, ?, 'STUDENT', ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(Config.DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, meta);
            pstmt.setInt(4, rollNumber);
            pstmt.setString(5, className);
            pstmt.setString(6, division);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }
}
