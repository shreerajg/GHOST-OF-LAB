package com.ghost.net;

import com.ghost.util.Config;
import com.ghost.util.ScreenCapture;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GhostServer {
    private ServerSocket serverSocket;
    private boolean running = false;
    // Use a cached thread pool: each student connection gets its own thread.
    // The old fixed pool of 4 meant only 4 students could connect at once —
    // any additional students would queue indefinitely with no screen updates.
    // Student handler threads are I/O-blocked (waiting for packets), not CPU-bound,
    // so creating one thread per student is the correct approach.
    private ExecutorService pool = Executors.newCachedThreadPool();
    private List<ClientHandler> clients = new ArrayList<>();
    private Map<String, ClientHandler> clientsByName = new HashMap<>();
    private Gson gson = new Gson();
    private ScreenUpdateListener screenListener;
    private ClientStatusListener statusListener;

    public interface ScreenUpdateListener {
        void onScreenUpdate(String clientName, String base64Image);

        default void onShellOutput(String clientName, String output) {
        }
    }

    public interface ClientStatusListener {
        void onClientConnected(String clientName);

        void onClientDisconnected(String clientName);
    }

    public void setScreenListener(ScreenUpdateListener listener) {
        this.screenListener = listener;
    }

    public void setStatusListener(ClientStatusListener listener) {
        this.statusListener = listener;
    }

    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(Config.SERVER_PORT);
                running = true;
                System.out.println("Ghost Server started on port " + Config.SERVER_PORT);

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);
                    pool.execute(handler);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void broadcast(CommandPacket packet) {
        String json = gson.toJson(packet);
        for (ClientHandler c : clients) {
            c.send(json);
        }
    }

    /**
     * Broadcast to all clients except the sender (for LAN chat)
     */
    private void broadcastExcept(CommandPacket packet, ClientHandler sender) {
        String json = gson.toJson(packet);
        for (ClientHandler c : clients) {
            if (c != sender) {
                c.send(json);
            }
        }
    }

    public void sendToClient(String clientName, CommandPacket packet) {
        ClientHandler handler = clientsByName.get(clientName);
        if (handler != null) {
            handler.send(gson.toJson(packet));
        }
    }

    public List<String> getConnectedClients() {
        return new ArrayList<>(clientsByName.keySet());
    }

    public int getClientCount() {
        return clients.size();
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    try {
                        CommandPacket packet = gson.fromJson(inputLine, CommandPacket.class);
                        handlePacket(packet);
                    } catch (Exception e) {
                        // Silently ignore parse errors
                    }
                }
            } catch (IOException e) {
                // Client disconnected
            } finally {
                System.out.println("Client disconnected: " + clientName);
                clients.remove(this);
                clientsByName.remove(clientName);

                // Notify listener of disconnect
                if (statusListener != null && !"Unknown".equals(clientName)) {
                    statusListener.onClientDisconnected(clientName);
                }

                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private void handlePacket(CommandPacket packet) {
            switch (packet.getType()) {
                case CONNECT:
                    clientName = packet.getSender();
                    clientsByName.put(clientName, this);

                    // Parse student info from payload JSON
                    try {
                        com.google.gson.JsonObject studentInfo = gson.fromJson(packet.getPayload(),
                                com.google.gson.JsonObject.class);
                        int roll = studentInfo.has("roll") ? studentInfo.get("roll").getAsInt() : 0;
                        String className = studentInfo.has("class") ? studentInfo.get("class").getAsString() : "";
                        String division = studentInfo.has("division") ? studentInfo.get("division").getAsString() : "";

                        // Record attendance
                        com.ghost.util.AttendanceTracker.recordConnection(clientName, roll, className, division);

                        System.out.println("Client connected: " + clientName + " (Roll: " + roll + ", " + className
                                + division + ")");
                    } catch (Exception e) {
                        // Fallback for old clients without student info
                        System.out.println("Client connected: " + clientName);
                    }

                    // Notify listener of connection
                    if (statusListener != null) {
                        statusListener.onClientConnected(clientName);
                    }
                    break;
                case SCREEN_DATA:
                    if (screenListener != null) {
                        screenListener.onScreenUpdate(clientName, packet.getPayload());
                    }
                    break;
                case SHELL_OUTPUT:
                    // Forward command output to admin terminal
                    if (screenListener != null) {
                        screenListener.onShellOutput(clientName, packet.getPayload());
                    }
                    break;
                case MSG:
                    // Forward student messages to all other students (LAN chat)
                    broadcastExcept(packet, this);
                    break;
                default:
                    break;
            }
        }

        public void send(String msg) {
            if (out != null)
                out.println(msg);
        }

        public String getClientName() {
            return clientName;
        }
    }
}
