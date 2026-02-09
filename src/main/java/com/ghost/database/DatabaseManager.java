package com.ghost.database;

import com.ghost.util.Config;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    public static void init() {
        try (Connection conn = DriverManager.getConnection(Config.DB_URL)) {
            if (conn != null) {
                String sql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL, " +
                        "password TEXT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "meta TEXT" +
                        ");";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Create default admin if not exists
                createDefaultAdmin(conn);
            }
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
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
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role"),
                            rs.getString("meta"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
        }
        return null; // Login failed
    }

    public static boolean registerStudent(String username, String password, String meta) {
        String sql = "INSERT INTO users(username, password, role, meta) VALUES(?, ?, 'STUDENT', ?)";
        try (Connection conn = DriverManager.getConnection(Config.DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, meta);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }
}
