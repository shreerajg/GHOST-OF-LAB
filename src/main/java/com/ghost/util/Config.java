package com.ghost.util;

import java.io.File;

public class Config {
    public static final String APP_NAME = "Ghost";
    public static final int SERVER_PORT = 5555;

    // ===== DATABASE =====
    // CRITICAL: Use an absolute local path, NOT a relative path.
    //
    // Why: A relative path like "ghost.db" resolves to the JVM's
    // current working directory, which differs between IDEs, EXE
    // wrappers, and different machines. Worse, if the project is
    // stored on OneDrive, multiple PCs share the same physical file
    // and SQLite's write-lock causes "database is locked" errors on
    // all but the first machine, silently failing init and leaving
    // zero users in the DB — so nobody can log in.
    //
    // Fix: each PC writes its own ghost.db to %APPDATA%\Ghost\
    // This path is always writable, always local (OneDrive does not
    // sync AppData by default), and is the same regardless of
    // where the JAR/EXE lives.
    public static final String DB_URL;

    static {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            // Fallback for non-Windows or missing env var
            appData = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming";
        }
        File ghostDir = new File(appData, "Ghost");
        ghostDir.mkdirs(); // Create %APPDATA%\Ghost\ if it doesn't exist
        File dbFile = new File(ghostDir, "ghost.db");
        DB_URL = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        System.out.println("[Config] Database path: " + dbFile.getAbsolutePath());
    }

    // ===== NETWORK CONFIGURATION =====
    // IP discovery is now automatic via UDP broadcast (DiscoveryService)
    // No need to set ADMIN_IP manually anymore!
}
