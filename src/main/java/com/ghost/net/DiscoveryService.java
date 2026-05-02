package com.ghost.net;

import com.ghost.util.Config;
import java.net.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP-based server discovery service.
 * Admin broadcasts its presence on ALL network interfaces.
 * Students listen continuously and auto-connect, even if IP changes.
 */
public class DiscoveryService {
    private static final int DISCOVERY_PORT = 5556;
    private static final String BROADCAST_MESSAGE = "GHOST_SERVER";
    private static final int BROADCAST_INTERVAL_MS = 2000;

    private DatagramSocket socket;
    private AtomicBoolean running = new AtomicBoolean(false);
    private DiscoveryListener listener;

    public interface DiscoveryListener {
        void onServerFound(String serverIp, int port);
    }

    public void setListener(DiscoveryListener listener) {
        this.listener = listener;
    }

    /**
     * Start broadcasting server presence (called by Admin).
     * Broadcasts on ALL active network interfaces for maximum compatibility
     * (works with Ethernet, Wi-Fi, and mobile hotspot simultaneously).
     */
    public void startBroadcasting() {
        running.set(true);
        new Thread(() -> {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

                String localIp = getLocalIp();
                System.out.println("Discovery: Broadcasting on port " + DISCOVERY_PORT + " (IP: " + localIp + ")");

                while (running.get()) {
                    try {
                        // Get current local IP (may change with hotspot/network switches)
                        localIp = getLocalIp();
                        String message = BROADCAST_MESSAGE + ":" + Config.SERVER_PORT + ":" + localIp;
                        byte[] data = message.getBytes();

                        // Broadcast to 255.255.255.255 (global broadcast)
                        DatagramPacket packet = new DatagramPacket(
                                data, data.length,
                                InetAddress.getByName("255.255.255.255"),
                                DISCOVERY_PORT);
                        socket.send(packet);

                        // Broadcast to ALL subnet broadcast addresses (covers all interfaces)
                        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface iface = interfaces.nextElement();
                            if (iface.isLoopback() || !iface.isUp())
                                continue;

                            for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                                InetAddress broadcast = ifAddr.getBroadcast();
                                if (broadcast != null) {
                                    packet = new DatagramPacket(data, data.length, broadcast, DISCOVERY_PORT);
                                    socket.send(packet);
                                }
                            }
                        }


                        Thread.sleep(BROADCAST_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    } catch (IOException e) {
                        System.err.println("Discovery broadcast send error: " + e.getMessage());
                        try {
                            Thread.sleep(BROADCAST_INTERVAL_MS);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Discovery broadcast error: " + e.getMessage());
            } finally {
                if (socket != null)
                    socket.close();
            }
        }, "DiscoveryBroadcaster").start();
    }

    /**
     * Start listening for server broadcasts (called by Student).
     * Runs CONTINUOUSLY - keeps listening even after finding the server
     * so it can detect IP changes when admin switches networks.
     */
    public void startListening() {
        running.set(true);

        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(5000); // 5 second timeout per attempt
        } catch (IOException e) {
            // Port already bound — this machine is likely the Admin itself.
            // Do NOT fall back to 127.0.0.1; student should never auto-connect to localhost
            // on a real LAN. Log and bail so the student UI stays in "searching" state.
            System.err.println("Discovery: Cannot bind to port " + DISCOVERY_PORT + " (" + e.getMessage() + ") — discovery disabled.");
            running.set(false);
            return;
        }

        new Thread(() -> {
            byte[] buffer = new byte[256];
            String lastFoundIp = null;

            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    if (message.startsWith(BROADCAST_MESSAGE)) {
                        String[] parts = message.split(":");
                        if (parts.length >= 3) {
                            int port = Integer.parseInt(parts[1]);
                            String serverIp = parts[2];

                            // Use the LAN IP embedded in the broadcast packet as-is.
                            // Never override with 127.0.0.1 — students connect over the
                            // real network, not loopback, even when tested on the same machine.

                            // Only log when IP changes or first discovery
                            if (!serverIp.equals(lastFoundIp)) {
                                System.out.println("Discovery: Found Admin at " + serverIp + ":" + port);
                                lastFoundIp = serverIp;
                            }

                            if (listener != null) {
                                listener.onServerFound(serverIp, port);
                            }

                            // Keep listening but sleep a bit to avoid spamming
                            Thread.sleep(BROADCAST_INTERVAL_MS);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Normal timeout - keep listening silently
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    // BUG FIX: Do NOT break on transient network errors.
                    // A momentary blip must not kill discovery permanently.
                    if (running.get()) {
                        System.err.println("Discovery: receive error (will retry): " + e.getMessage());
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                    }
                    // continue the while loop — keep listening
                }
            }
        }, "DiscoveryListener").start();
    }

    public void stop() {
        running.set(false);
        if (socket != null) {
            socket.close();
        }
    }

    /**
     * Get the best local LAN IP address for admin broadcasting.
     *
     * Problem: PCs in a lab often have many network interfaces:
     * - VirtualBox Host-Only (192.168.56.x)
     * - VMware (192.168.xxx.x)
     * - Hyper-V vSwitch
     * - Bluetooth
     * - Wi-Fi / Ethernet (the REAL LAN adapter)
     *
     * The old code just returned the FIRST non-loopback IPv4 address,
     * which could be a virtual adapter — causing all students to get
     * an unreachable IP and fail to connect.
     *
     * Fix: Score each interface. Skip known virtual/software adapters by
     * name. Prefer the highest-scored real physical NIC.
     */
    public static String getLocalIp() {
        String bestIp = null;
        int bestScore = -1;

        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                String name = iface.getName().toLowerCase();
                String displayName = iface.getDisplayName() != null ? iface.getDisplayName().toLowerCase() : "";

                // Score: higher = more likely to be the real LAN adapter.
                // Virtual / software adapters get a penalty.
                int score = 10;
                if (name.contains("vbox") || displayName.contains("virtualbox")) score -= 8;
                if (name.contains("vmnet") || displayName.contains("vmware"))    score -= 8;
                if (name.contains("hyper") || displayName.contains("hyper-v"))   score -= 8;
                if (name.contains("docker") || displayName.contains("docker"))   score -= 8;
                if (displayName.contains("bluetooth"))                           score -= 8;
                if (displayName.contains("virtual") || displayName.contains("pseudo")) score -= 6;
                // Physical Ethernet and Wi-Fi get a bonus
                if (displayName.contains("ethernet") || displayName.contains("lan")) score += 3;
                if (displayName.contains("wi-fi") || displayName.contains("wireless")) score += 2;

                for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                    InetAddress addr = ifAddr.getAddress();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (score > bestScore) {
                            bestScore = score;
                            bestIp = addr.getHostAddress();
                            System.out.println("[Discovery] Interface candidate: " + displayName
                                    + " (" + bestIp + ") score=" + score);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (bestIp != null) {
            return bestIp;
        }

        // Last-resort fallback via UDP trick (connects to 8.8.8.8 without sending data
        // — OS picks the right source interface automatically)
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 80);
            String ip = s.getLocalAddress().getHostAddress();
            if (ip != null && !ip.startsWith("0.")) {
                System.out.println("[Discovery] Fallback UDP trick IP: " + ip);
                return ip;
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }
}
