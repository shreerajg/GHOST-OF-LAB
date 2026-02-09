package com.ghost.net;

public class CommandPacket {
    public enum Type {
        CONNECT, DISCONNECT, LOCK, UNLOCK, SHUTDOWN, RESTART, MSG, SCREEN_DATA,
        INTERNET, SHELL, SHELL_OUTPUT, FILE_DATA, ADMIN_SCREEN, NOTIFICATION, MUTE, BLOCK_INPUT
    }

    private Type type;
    private String sender; // Username or IP
    private String payload; // JSON or raw string
    private long timestamp;

    public CommandPacket(Type type, String sender, String payload) {
        this.type = type;
        this.sender = sender;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public Type getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
