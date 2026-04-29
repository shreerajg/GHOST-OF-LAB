package com.ghost.util;

import java.io.*;
import java.nio.file.*;

/**
 * NetworkManager — Java bridge to network_manager.py
 *
 * All internet blocking/unblocking is delegated to the Python script
 * via ProcessBuilder.
 *
 * Elevation strategy (in order):
 *   1. Run Python directly — works if JVM was started as Administrator.
 *   2. If Python exits with code 2 (not admin), re-invoke Python via
 *      PowerShell "Start-Process -Verb RunAs -Wait" to trigger a UAC
 *      prompt for the user.
 *
 * Commands sent to the script:
 *   block   — block distracting sites
 *   unblock — restore hosts file and DNS
 *   recover — startup crash-recovery check
 */
public class NetworkManager {

    // Path to the Python script — resolved at startup against multiple roots
    private static final String[] SCRIPT_CANDIDATES = {
        "python_modules/network_manager.py",          // run from project root
        "../python_modules/network_manager.py",        // run from scripts/
        resolveRelativeToJar("python_modules/network_manager.py")
    };

    private static final String SCRIPT_PATH = resolveScriptPath();
    private static final String PYTHON_CMD  = "python";

    // In-memory state flag
    private static volatile boolean blocked = false;

    // ──────────────────────────────────────────────────────────
    //  PATH RESOLUTION
    // ──────────────────────────────────────────────────────────

    /** Best-effort path relative to the running class/jar location */
    private static String resolveRelativeToJar(String rel) {
        try {
            File jarDir = new File(NetworkManager.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jarDir.isDirectory()) jarDir = jarDir.getParentFile();
            return new File(jarDir, rel).getAbsolutePath();
        } catch (Exception e) {
            return rel;
        }
    }

    private static String resolveScriptPath() {
        for (String candidate : SCRIPT_CANDIDATES) {
            if (candidate != null && new File(candidate).exists()) {
                System.out.println("[NetworkManager] Using script: " + candidate);
                return candidate;
            }
        }
        System.err.println("[NetworkManager] WARNING: network_manager.py not found. " +
                           "Searched: python_modules/, ../python_modules/");
        return "python_modules/network_manager.py"; // best guess
    }

    // ──────────────────────────────────────────────────────────
    //  PUBLIC API  (matches old HostsFileManager surface)
    // ──────────────────────────────────────────────────────────

    /**
     * Block all distracting sites by invoking the Python network manager.
     *
     * @return true on success, false on failure
     */
    public static synchronized boolean blockSites() {
        System.out.println("[NetworkManager] Calling Python: block");
        int code = runScript("block");
        if (code == 0) {
            blocked = true;
            System.out.println("[NetworkManager] Sites blocked successfully.");
            return true;
        }
        if (code == 2) {
            // Not admin — try again via PowerShell elevation (UAC prompt)
            System.out.println("[NetworkManager] Not admin, re-trying with UAC elevation...");
            code = runScriptElevated("block");
            if (code == 0) {
                blocked = true;
                System.out.println("[NetworkManager] Sites blocked successfully (elevated).");
                return true;
            }
        }
        System.err.println("[NetworkManager] blockSites failed (exit code " + code + ").");
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
        }
        if (code == 2) {
            System.out.println("[NetworkManager] Not admin, re-trying with UAC elevation...");
            code = runScriptElevated("unblock");
            if (code == 0) {
                blocked = false;
                System.out.println("[NetworkManager] Sites unblocked successfully (elevated).");
                return true;
            }
        }
        System.err.println("[NetworkManager] restoreHostsFile failed (exit code " + code + ").");
        blocked = false; // reset so app doesn't think it's still blocked
        return false;
    }

    /**
     * Startup crash-recovery: if a backup exists from a crashed blocked session,
     * Python will restore the hosts file automatically.
     * Call once at application startup.
     */
    public static void recoverOnStartup() {
        System.out.println("[NetworkManager] Running startup crash-recovery check...");
        int code = runScript("recover");
        if (code == 2) {
            code = runScriptElevated("recover");
        }
        if (code == 0) {
            blocked = hasLeftoverBlocks();
        }
    }

    /**
     * Check whether Ghost's block entries are present in the hosts file.
     * Readable without admin rights.
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

    /** @return true if sites are currently blocked (in-memory flag) */
    public static boolean isBlocked() {
        return blocked;
    }

    // ──────────────────────────────────────────────────────────
    //  INTERNAL: direct Python invocation
    // ──────────────────────────────────────────────────────────

    /**
     * Run "python network_manager.py {@code command}" directly.
     * Works when the JVM itself is already elevated.
     *
     * @return exit code (0=OK, 1=error, 2=not-admin)
     */
    private static int runScript(String command) {
        try {
            // Use absolute path if available so working-dir doesn't matter
            String script = new File(SCRIPT_PATH).getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, script, command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            drainOutput(process.getInputStream());
            return process.waitFor();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[NetworkManager] Interrupted: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("[NetworkManager] Cannot launch Python: " + e.getMessage());
            System.err.println("[NetworkManager] Is Python installed and on PATH?");
            return 1;
        }
    }

    // ──────────────────────────────────────────────────────────
    //  INTERNAL: elevated Python invocation via PowerShell UAC
    // ──────────────────────────────────────────────────────────

    /**
     * Re-run the Python script elevated via PowerShell "Start-Process -Verb RunAs".
     * Uses the FULL path to powershell.exe so it works even when not on PATH.
     *
     * @return 0 on success, 1 on failure
     */
    private static int runScriptElevated(String command) {
        try {
            File script  = new File(SCRIPT_PATH).getAbsoluteFile();
            File tempOut = File.createTempFile("ghost_nm_", ".txt");
            String outPath = tempOut.getAbsolutePath();

            // Full path to powershell.exe — always present on Windows even if not on PATH
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) systemRoot = "C:\\Windows";
            String psExe = systemRoot + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
            if (!new File(psExe).exists()) {
                // 32-bit JVM on 64-bit OS uses SysNative
                psExe = systemRoot + "\\SysNative\\WindowsPowerShell\\v1.0\\powershell.exe";
            }

            String psCmd = String.format(
                "Start-Process cmd -Verb RunAs -Wait -WindowStyle Hidden " +
                "-ArgumentList '/c python \"%s\" %s > \"%s\" 2>&1'",
                script.getAbsolutePath(), command, outPath
            );

            ProcessBuilder pb = new ProcessBuilder(
                psExe, "-NoProfile", "-NonInteractive", "-Command", psCmd
            );
            pb.redirectErrorStream(true);
            Process ps = pb.start();
            drainOutput(ps.getInputStream());
            int psExit = ps.waitFor();

            // Print what the Python script wrote to the temp file
            if (tempOut.exists()) {
                try {
                    String output = new String(Files.readAllBytes(tempOut.toPath())).trim();
                    if (!output.isEmpty()) {
                        for (String line : output.split("\\r?\\n")) {
                            System.out.println("[Python/NetworkManager] " + line);
                        }
                    }
                } finally {
                    tempOut.delete();
                }
            }

            // psExit 0 = PowerShell launched and waited OK.
            // We trust that Python succeeded if psExit is 0 and the hosts file changed.
            return psExit == 0 ? 0 : 1;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[NetworkManager] Elevation interrupted: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("[NetworkManager] Elevation error: " + e.getMessage());
            return 1;
        }
    }

    // ──────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────

    /** Drain an InputStream to the Java console (non-blocking consumer). */
    private static void drainOutput(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Python/NetworkManager] " + line);
            }
        } catch (IOException e) {
            // ignore — stream closed
        }
    }
}
