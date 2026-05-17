package com.ghost.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class to track student attendance
 */
public class StudentAttendance {
    private String username;
    private int rollNumber;
    private String className;
    private String division;
    private LocalDateTime firstConnected;
    private LocalDateTime lastSeen;

    public StudentAttendance(String username, int rollNumber, String className, String division) {
        this.username = username;
        this.rollNumber = rollNumber;
        this.className = className;
        this.division = division;
        this.firstConnected = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public int getRollNumber() {
        return rollNumber;
    }

    public String getClassName() {
        return className;
    }

    public String getDivision() {
        return division;
    }

    public LocalDateTime getFirstConnected() {
        return firstConnected;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    /** Time-only format: fits in Excel's default column width without showing ######. */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String getFirstConnectedFormatted() {
        return firstConnected.format(TIME_FMT);
    }

    public String getLastSeenFormatted() {
        return lastSeen.format(TIME_FMT);
    }

    /**
     * Returns lastSeen formatted, but uses `effectiveNow` instead when lastSeen
     * still equals firstConnected (student still connected — never disconnected yet).
     * This prevents 0:00:00 duration in the CSV when a student is still online.
     */
    public String getLastSeenEffective(LocalDateTime effectiveNow) {
        if (!lastSeen.isAfter(firstConnected)) {
            return effectiveNow.format(TIME_FMT);
        }
        return lastSeen.format(TIME_FMT);
    }

    /**
     * Returns duration between firstConnected and the effective last-seen time.
     * Uses effectiveNow when the student hasn't disconnected yet (lastSeen == firstConnected).
     */
    public String getDurationEffective(LocalDateTime effectiveNow) {
        LocalDateTime end = lastSeen.isAfter(firstConnected) ? lastSeen : effectiveNow;
        long totalSeconds = java.time.Duration.between(firstConnected, end).getSeconds();
        if (totalSeconds < 0) totalSeconds = 0;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs    = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Returns total connection time formatted as HH:mm:ss.
     * Uses the gap between firstConnected and lastSeen.
     */
    public String getTotalConnectedDuration() {
        Duration d = Duration.between(firstConnected, lastSeen);
        long totalSeconds = d.getSeconds();
        if (totalSeconds < 0) totalSeconds = 0;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs    = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public String getClassDivision() {
        String cls = (className == null) ? "" : className.trim();
        String div = (division == null) ? "" : division.trim();
        if (cls.isEmpty() && div.isEmpty()) return "General";
        if (cls.isEmpty()) return div;
        if (div.isEmpty()) return cls;
        return cls + "-" + div;
    }
}
