package com.ghost.net;

import com.ghost.database.User;
import com.ghost.util.Config;
import com.ghost.util.HostsFileManager;
import com.ghost.util.ScreenCapture;
import com.ghost.util.PythonBridge;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

public class GhostClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Gson gson = new Gson();
    private String adminIp;
    private User studentUser; // Store student info
    private CommandListener listener;
    private ScheduledExecutorService screenScheduler;
    private boolean sendingScreens = true;
    private volatile boolean running = true;

    public interface CommandListener {
        void onCommand(CommandPacket packet);
    }

    public GhostClient(String adminIp, User studentUser) {
        this.adminIp = adminIp;
        this.studentUser = studentUser;
    }

    /**
     * Update the admin IP address when the network changes.
     * Forces a reconnect if the IP is different.
     */
    public void updateAdminIp(String newIp) {
        if (!newIp.equals(this.adminIp)) {
            System.out.println("[GhostClient] Admin IP changed: " + this.adminIp + " -> " + newIp);
            this.adminIp = newIp;
            // Force reconnect by closing current socket
            try {
                if (socket != null)
                    socket.close();
            } catch (Exception e) {
            }
        }
    }

    public void setListener(CommandListener listener) {
        this.listener = listener;
    }

    public void connect() {
        new Thread(() -> {
            while (running) {
                try {
                    System.out.println("Connecting to Admin at " + adminIp + ":" + Config.SERVER_PORT + "...");
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(adminIp, Config.SERVER_PORT), 3000);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    System.out.println("Connected to Admin!");

                    // Notify UI of connection
                    if (listener != null) {
                        listener.onCommand(new CommandPacket(CommandPacket.Type.NOTIFICATION, "SYSTEM", "CONNECTED"));
                    }

                    // Send Initial Handshake with student info JSON
                    String studentInfo = String.format(
                            "{\"username\":\"%s\",\"roll\":%d,\"class\":\"%s\",\"division\":\"%s\"}",
                            studentUser.getUsername(),
                            studentUser.getRollNumber(),
                            studentUser.getClassName(),
                            studentUser.getDivision());
                    CommandPacket verify = new CommandPacket(CommandPacket.Type.CONNECT,
                            studentUser.getUsername(), studentInfo);
                    out.println(gson.toJson(verify));

                    // Start screen capture thread
                    startScreenCapture();

                    // Listen loop
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        handleCommand(inputLine);
                    }

                    // Connection closed - notify UI
                    if (listener != null) {
                        listener.onCommand(
                                new CommandPacket(CommandPacket.Type.NOTIFICATION, "SYSTEM", "⚠️ Admin disconnected"));
                    }

                } catch (IOException e) {
                    System.out.println("Waiting for Admin... (retry in 3s)");
                    // Notify UI if was previously connected
                    if (listener != null && socket != null && socket.isConnected()) {
                        listener.onCommand(new CommandPacket(CommandPacket.Type.NOTIFICATION, "SYSTEM",
                                "⚠️ Connection to Admin lost"));
                    }
                }

                // Cleanup and wait before retry
                stopScreenCapture();
                try {
                    if (socket != null)
                        socket.close();
                } catch (Exception ex) {
                }
                socket = null;
                out = null;
                in = null;

                if (running) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }).start();
    }

    private void startScreenCapture() {
        if (screenScheduler != null && !screenScheduler.isShutdown())
            return;

        screenScheduler = Executors.newSingleThreadScheduledExecutor();
        screenScheduler.scheduleAtFixedRate(() -> {
            if (sendingScreens && out != null) {
                try {
                    String base64 = ScreenCapture.captureForStreaming();
                    if (base64 != null) {
                        CommandPacket screenPacket = new CommandPacket(
                                CommandPacket.Type.SCREEN_DATA,
                                studentUser.getUsername(),
                                base64);
                        out.println(gson.toJson(screenPacket));
                    }
                } catch (Exception e) {
                }
            }
        }, 50, 50, TimeUnit.MILLISECONDS); // 20fps for smoother display
    }

    private void stopScreenCapture() {
        if (screenScheduler != null) {
            screenScheduler.shutdown();
            screenScheduler = null;
        }
    }

    public void setScreenSending(boolean enabled) {
        this.sendingScreens = enabled;
    }

    private void handleCommand(String json) {
        try {
            CommandPacket packet = gson.fromJson(json, CommandPacket.class);

            // Notify UI listener
            if (listener != null) {
                listener.onCommand(packet);
            }

            // Execute commands - use direct Java Runtime for critical operations
            // to avoid Python dependency on student PCs
            switch (packet.getType()) {
                case LOCK:
                    // Lock workstation directly without Python
                    executeDirectCommand("rundll32.exe user32.dll,LockWorkStation");
                    break;
                case UNLOCK:
                    // Note: can't really unlock, just unblock input if Python available
                    try {
                        PythonBridge.execute("unblock_input");
                    } catch (Exception e) {
                        System.out.println("Could not unblock input: " + e.getMessage());
                    }
                    break;
                case SHUTDOWN:
                    // Shutdown
                    Runtime.getRuntime().exec("shutdown /s /t 5");
                    break;
                case RESTART:
                    Runtime.getRuntime().exec("shutdown /r /t 5");
                    break;
                case OPEN_URL:
                    // Handled by UI listener (StudentDashboard) via Desktop.browse()
                    // Do NOT open here — would cause double browser tab
                    break;
                case INTERNET:
                    // Use HostsFileManager (no Python dependency, keeps LAN alive)
                    if ("DISABLE".equals(packet.getPayload())) {
                        HostsFileManager.blockSites();
                    } else {
                        HostsFileManager.restoreHostsFile();
                    }
                    break;
                case SHELL:
                    // Execute shell command directly without Python
                    String cmd = packet.getPayload();
                    if (cmd != null && !cmd.isEmpty()) {
                        executeShellWithOutput(cmd);
                    }
                    break;
                case MSG:
                case ADMIN_SCREEN:
                case NOTIFICATION:
                case FILE_DATA:
                case STOP_SCREEN_SHARE:
                    // Handled by UI listener only
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(CommandPacket packet) {
        if (out != null) {
            out.println(gson.toJson(packet));
        }
    }

    public void disconnect() {
        running = false;
        stopScreenCapture();
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
        }
    }

    /**
     * Execute a command directly using Java Runtime (no Python dependency)
     */
    private void executeDirectCommand(String command) {
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                System.out.println("[DirectCmd] Executed: " + command);
            } catch (Exception e) {
                System.err.println("[DirectCmd] Error executing '" + command + "': " + e.getMessage());
            }
        });
    }

    /**
     * Execute a shell command and send output back to admin.
     * Handles built-in shortcut commands (e.g. /shutdown, /restart, /lock)
     * and strips leading '/' from regular commands for convenience.
     */
    private void executeShellWithOutput(String command) {
        String clientName = studentUser.getUsername();

        // Handle built-in shortcut commands
        String cmdLower = command.trim().toLowerCase();
        if (cmdLower.equals("/shutdown") || cmdLower.startsWith("/shutdown ")) {
            // Convert /shutdown to proper Windows shutdown command
            String args = command.trim().substring("/shutdown".length()).trim();
            String shutdownCmd = "shutdown " + (args.isEmpty() ? "/s /t 0" : args);
            executeDirectCommand(shutdownCmd);
            sendShellOutput(clientName, command, "Executing: " + shutdownCmd);
            return;
        } else if (cmdLower.equals("/restart") || cmdLower.startsWith("/restart ")) {
            executeDirectCommand("shutdown /r /t 0");
            sendShellOutput(clientName, command, "Executing: shutdown /r /t 0");
            return;
        } else if (cmdLower.equals("/lock")) {
            executeDirectCommand("rundll32.exe user32.dll,LockWorkStation");
            sendShellOutput(clientName, command, "Workstation locked");
            return;
        } else if (cmdLower.equals("/logoff")) {
            executeDirectCommand("shutdown /l");
            sendShellOutput(clientName, command, "Executing: shutdown /l");
            return;
        }

        // Strip leading '/' from commands (common mistake: /ipconfig instead of
        // ipconfig)
        String cleanCmd = command.trim();
        if (cleanCmd.startsWith("/")) {
            cleanCmd = cleanCmd.substring(1);
        }

        final String finalCmd = cleanCmd;
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", finalCmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                p.waitFor();

                // Send output back to admin
                sendShellOutput(clientName, command, output.toString());
            } catch (Exception e) {
                // Send error back to admin
                sendShellOutput(clientName, command, "Error: " + e.getMessage());
            }
        });
    }

    /**
     * Helper to send shell output back to admin
     */
    private void sendShellOutput(String clientName, String command, String output) {
        if (out != null) {
            String response = clientName + " > " + command + "\n" + output;
            CommandPacket outputPacket = new CommandPacket(
                    CommandPacket.Type.SHELL_OUTPUT,
                    clientName,
                    response);
            out.println(gson.toJson(outputPacket));
        }
    }
}
