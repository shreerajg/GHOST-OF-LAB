package com.ghost.database;

public class User {
    private int id;
    private String username;
    private String password; // In a real app, hash this!
    private String role; // "ADMIN" or "STUDENT"
    private String meta; // JSON string for extra data

    public User(int id, String username, String password, String role, String meta) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.meta = meta;
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.meta = "{}";
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
}
