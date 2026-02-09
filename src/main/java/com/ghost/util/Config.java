package com.ghost.util;

public class Config {
    public static final String APP_NAME = "Ghost";
    public static final String DB_URL = "jdbc:sqlite:ghost.db";
    public static final int SERVER_PORT = 5555;

    // ===== NETWORK CONFIGURATION =====
    // Set this to the IP address of the ADMIN PC
    // For same-machine testing: "127.0.0.1"
    // For network testing: Admin PC's actual IP (e.g., "192.168.1.208")
    public static final String ADMIN_IP = "192.168.1.208";
}
