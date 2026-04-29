package com.ghost.util;

import java.io.*;
import java.nio.file.*;

/**
 * NetworkManager — Java bridge to network_manager.py
 *
 * All internet blocking/unblocking is delegated to the Python script
 * via ProcessBuilder. This class provides the same public API that
 * HostsFileManager used to expose, so callers need only change the
 * class name.
 *
 * Python script location (relative to working directory):
 *   python_modules/network_manager.py
 *
 * Commands sent to the script:
 *   block   — block distracting sites
 *   unblock — restore hosts file and DNS
 *   recover — startup crash-recovery check
 */
public class NetworkManager {

    // Path to the Python script (relative to project root / working dir)
    private static final String SCRIPT_REL   = "python_modules/network_manager.py";
    private static final String SCRIPT_ALT   = "../python_modules/network_manager.py";
    private static final String PYTHON_CMD   = "python";

    // Cached absolute path resolved once at class-load time
    private static final String SCRIPT_PATH  = resolveScriptPath();

    // In-memory state flag (mirrored from Python's state file)
    private static volatile boolean blocked  = false;

    // ──────────────────────────────────────────────────────────
    //  PATH RESOLUTION
    // ──────────────────────────────────────────────────────────
    private static String resolveScriptPath() {
        File rel = new File(SCRIPT_REL);
        if (rel.exists()) return SCRIPT_REL;
        File alt = new File(SCRIPT_ALT);
        if (alt.exists()) return SCRIPT_ALT;
        // Fall back to relative; Python error will surface if it fails
        return SCRIPT_REL;
    }

    // ──────────────────────────────────────────────────────────
    //  PUBLIC API  (matches old HostsFileManager surface)
    // ──────────────────────────────────────────────────────────

    /**
     * Block all distracting sites by invoking the Python network manager.
     *
     * @return true on success, false on failure or if Python is not available
     */
    public static synchronized boolean blockSites() {
        System.out.println("[NetworkManager] Calling Python: block");
        int code = runScript("block");
        if (code == 0) {
            blocked = true;
            System.out.println("[NetworkManager] Sites blocked successfully.");
            return true;
        } else if (code == 2) {
            System.err.println("[NetworkManager] Python script requires Administrator privileges.");
        } else {
            System.err.println("[NetworkManager] Block failed (exit code " + code + ").");
        }
        return false;
    }

    /**
     * Restore the original hosts file and DNS settings.
     *
     * @return true on success, false on failure
     */
    public static synchronized boolean restoreHostsFile() {
        System.out.println("[NetworkManager] Calling Python: unblock");
        int code = runScript("unblock");
        if (code == 0) {
            blocked = false;
            System.out.println("[NetworkManager] Sites unblocked successfully.");
            return true;
        } else if (code == 2) {
            System.err.println("[NetworkManager] Python script requires Administrator privileges.");
        } else {
            System.err.println("[NetworkManager] Unblock failed (exit code " + code + ").");
        }
        // Even on error we reset flag so the app doesn't think it's still blocked
        blocked = false;
        return false;
    }

    /**
     * Startup crash-recovery check: if a previous session crashed while
     * blocking, Python will restore the hosts file automatically.
     * Should be called once at application startup.
     */
    public static void recoverOnStartup() {
        System.out.println("[NetworkManager] Running startup crash-recovery check...");
        int code = runScript("recover");
        if (code == 0) {
            // Recovery may have unblocked; sync flag
            blocked = hasLeftoverBlocks();
        }
    }

    /**
     * Check whether leftover block entries are present in the hosts file.
     * Reads the hosts file directly for a quick check.
     */
    public static boolean hasLeftoverBlocks() {
        Path hostsPath = Paths.get("C:\\Windows\\System32\\drivers\\etc\\hosts");
        try {
            String content = new String(Files.readAllBytes(hostsPath));
            return content.contains("# ===== GHOST LAB BLOCKER START =====");
        } catch (IOException e) {
            return false;
        }
    }

    /** @return true if sites are currently blocked (in-memory state) */
    public static boolean isBlocked() {
        return blocked;
    }

    // ──────────────────────────────────────────────────────────
    //  INTERNAL: run the Python script synchronously
    // ──────────────────────────────────────────────────────────

    /**
     * Invoke "python network_manager.py {@code command}" and wait for it to
     * finish. Streams stdout/stderr to the Java console.
     *
     * @param command one of: block, unblock, recover
     * @return process exit code (0 = OK, 1 = error, 2 = no admin rights)
     */
    private static int runScript(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, SCRIPT_PATH, command);
            pb.redirectErrorStream(true); // merge stderr into stdout

            Process process = pb.start();

            // Stream Python output to Java console
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Python/NetworkManager] " + line);
                }
            }

            int exitCode = process.waitFor();
            return exitCode;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[NetworkManager] Script interrupted: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("[NetworkManager] Failed to launch Python script: " + e.getMessage());
            System.err.println("[NetworkManager] Make sure Python is installed and on PATH.");
            return 1;
        }
    }
}
