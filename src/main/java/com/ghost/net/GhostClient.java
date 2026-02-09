package com.ghost.net;

import com.ghost.util.Config;
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
    private CommandListener listener;
    private ScheduledExecutorService screenScheduler;
    private boolean sendingScreens = true;
    private volatile boolean running = true;

    public interface CommandListener {
        void onCommand(CommandPacket packet);
    }

    public GhostClient(String adminIp) {
        this.adminIp = adminIp;
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

                    // Send Initial Handshake
                    CommandPacket verify = new CommandPacket(CommandPacket.Type.CONNECT,
                            System.getProperty("user.name"), "{}");
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
                                System.getProperty("user.name"),
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
                    // Shutdown directly without Python
                    executeDirectCommand("shutdown /s /t 0");
                    break;
                case RESTART:
                    // Restart directly without Python
                    executeDirectCommand("shutdown /r /t 0");
                    break;
                case INTERNET:
                    // Network control requires Python for now
                    try {
                        if ("DISABLE".equals(packet.getPayload())) {
                            PythonBridge.execute("kill_net");
                        } else {
                            PythonBridge.execute("restore_net");
                        }
                    } catch (Exception e) {
                        System.out.println("Network control requires Python: " + e.getMessage());
                    }
                    break;
                case MUTE:
                    // Mute audio - try PowerShell directly
                    executeDirectCommand(
                            "powershell -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]173)\"");
                    break;
                case BLOCK_INPUT:
                    // Block input requires Python (admin privileges)
                    try {
                        if ("BLOCK".equals(packet.getPayload())) {
                            PythonBridge.execute("block_input");
                        } else {
                            PythonBridge.execute("unblock_input");
                        }
                    } catch (Exception e) {
                        System.out.println("Input control requires Python with admin: " + e.getMessage());
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
     * Execute a shell command and send output back to admin
     */
    private void executeShellWithOutput(String command) {
        String clientName = System.getProperty("user.name");
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
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
                if (out != null) {
                    String response = clientName + " > " + command + "\n" + output.toString();
                    CommandPacket outputPacket = new CommandPacket(
                            CommandPacket.Type.SHELL_OUTPUT,
                            clientName,
                            response);
                    out.println(gson.toJson(outputPacket));
                }
            } catch (Exception e) {
                // Send error back to admin
                if (out != null) {
                    String errorResponse = clientName + " > " + command + "\nError: " + e.getMessage();
                    CommandPacket errorPacket = new CommandPacket(
                            CommandPacket.Type.SHELL_OUTPUT,
                            clientName,
                            errorResponse);
                    out.println(gson.toJson(errorPacket));
                }
            }
        });
    }
}
