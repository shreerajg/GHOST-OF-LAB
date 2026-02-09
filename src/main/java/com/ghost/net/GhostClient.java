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
        }, 100, 100, TimeUnit.MILLISECONDS); // 10fps to reduce flicker
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

            // Execute commands
            switch (packet.getType()) {
                case LOCK:
                    PythonBridge.execute("lock");
                    break;
                case UNLOCK:
                    PythonBridge.execute("unblock_input");
                    break;
                case SHUTDOWN:
                    PythonBridge.execute("shutdown");
                    break;
                case RESTART:
                    PythonBridge.execute("restart");
                    break;
                case INTERNET:
                    if ("DISABLE".equals(packet.getPayload())) {
                        PythonBridge.execute("kill_net");
                    } else {
                        PythonBridge.execute("restore_net");
                    }
                    break;
                case MUTE:
                    PythonBridge.execute("mute");
                    break;
                case BLOCK_INPUT:
                    if ("BLOCK".equals(packet.getPayload())) {
                        PythonBridge.execute("block_input");
                    } else {
                        PythonBridge.execute("unblock_input");
                    }
                    break;
                case SHELL:
                    // Execute shell command from admin
                    String cmd = packet.getPayload();
                    if (cmd != null && !cmd.isEmpty()) {
                        PythonBridge.execute(cmd);
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
}
