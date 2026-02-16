package com.ghost.util;

public class Config {
    public static final String APP_NAME = "Ghost";
    public static final String DB_URL = "jdbc:sqlite:ghost.db";
    public static final int SERVER_PORT = 5555;

    // ===== NETWORK CONFIGURATION =====
    // IP discovery is now automatic via UDP broadcast (DiscoveryService)
    // No need to set ADMIN_IP manually anymore!
}
