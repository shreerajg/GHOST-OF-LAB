package com.ghost.util;

import com.ghost.model.StudentAttendance;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks student attendance and generates CSV reports.
 *
 * Key design decisions:
 * - attendanceMap is keyed by username — every unique student gets one record.
 * - generateAttendanceCSV() groups students by class-division and writes one
 *   file per group named Attendance_<Class>-<Div>_<date>.csv
 * - If a file for today already exists, existing records are read first and
 *   merged with the in-memory map so re-running never loses data and never
 *   creates duplicate rows.
 */
public class AttendanceTracker {

    // One record per student (username is the unique key for a session).
    private static final Map<String, StudentAttendance> attendanceMap = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Record a student connection (or update last-seen on reconnect).
     */
    public static void recordConnection(String username, int rollNumber,
                                        String className, String division) {
        if (username == null || username.isEmpty())
            return;

        StudentAttendance attendance = attendanceMap.get(username);
        if (attendance == null) {
            attendance = new StudentAttendance(username, rollNumber, className, division);
            attendanceMap.put(username, attendance);
            System.out.println("[Attendance] Recorded: " + username
                    + " (Roll: " + rollNumber + ", " + className + "-" + division + ")");
        } else {
            // Already seen — just refresh the last-seen timestamp.
            attendance.updateLastSeen();
            System.out.println("[Attendance] Reconnected: " + username);
        }
    }

    /**
     * Stamp the disconnect time as Last Seen so the sheet shows real duration.
     */
    public static void notifyDisconnect(String username) {
        if (username == null || username.isEmpty() || "Unknown".equals(username))
            return;
        StudentAttendance attendance = attendanceMap.get(username);
        if (attendance != null) {
            attendance.updateLastSeen();
            System.out.println("[Attendance] Last seen updated for: " + username
                    + " at " + attendance.getLastSeenFormatted());
        }
    }

    /**
     * Generate (or update) attendance CSV files grouped by class-division.
     *
     * If a file for today already exists it is read first; any student
     * already in the file is kept (merged), and new students are appended.
     * This means calling this method multiple times on the same day is safe.
     *
     * @return list of absolute paths of all written files
     */
    public static List<String> generateAttendanceCSV() {
        // Create attendance directory once.
        File attendanceDir = new File(System.getProperty("user.home"), "Ghost Attendance");
        attendanceDir.mkdirs();

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // ----- Group in-memory students by class-division -----
        Map<String, List<StudentAttendance>> groupedByClass = new LinkedHashMap<>();
        for (StudentAttendance a : attendanceMap.values()) {
            String key = a.getClassDivision();
            groupedByClass.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

        if (groupedByClass.isEmpty()) {
            System.out.println("[Attendance] No students connected, skipping CSV generation");
            return Collections.emptyList();
        }

        List<String> generatedFiles = new ArrayList<>();

        for (Map.Entry<String, List<StudentAttendance>> entry : groupedByClass.entrySet()) {
            String classDivision = entry.getKey();
            List<StudentAttendance> newStudents = entry.getValue();

            String filename = "Attendance_" + classDivision + "_" + date + ".csv";
            File csvFile = new File(attendanceDir, filename);

            // ----- Merge with any existing file for today -----
            // Map: username -> row-line (existing records we don't want to duplicate)
            Map<String, String> existingRows = readExistingRows(csvFile);

            // Merge: overwrite existing row for each in-memory student
            // (their Last Seen will be fresher than what was in the file).
            for (StudentAttendance s : newStudents) {
                existingRows.put(s.getUsername(), buildCsvRow(s));
            }

            // Sort all records (existing + new) by roll number numerically.
            List<Map.Entry<String, String>> allRows = new ArrayList<>(existingRows.entrySet());
            allRows.sort(Comparator.comparingInt(e -> extractRoll(e.getValue())));

            // ----- Write the merged file -----
            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, false /* overwrite */))) {
                writer.println("Roll Number,Username,Class,Division,Status,First Connected,Last Seen,Time Connected");
                for (Map.Entry<String, String> row : allRows) {
                    writer.println(row.getValue());
                }
                generatedFiles.add(csvFile.getAbsolutePath());
                System.out.println("[Attendance] Generated: " + csvFile.getAbsolutePath()
                        + " (" + allRows.size() + " students)");
            } catch (IOException e) {
                System.err.println("[Attendance] Failed to write CSV: " + e.getMessage());
            }
        }

        return generatedFiles;
    }

    /** Clear all in-memory records (call when starting a new session). */
    public static void clearRecords() {
        attendanceMap.clear();
    }

    /** Total unique students who connected this session. */
    public static int getTotalStudents() {
        return attendanceMap.size();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Build a single CSV data row for a student. */
    private static String buildCsvRow(StudentAttendance s) {
        return String.format("%d,\"%s\",\"%s\",\"%s\",\"Present\",\"%s\",\"%s\",\"%s\"",
                s.getRollNumber(),
                escapeCsv(s.getUsername()),
                escapeCsv(s.getClassName()),
                escapeCsv(s.getDivision()),
                s.getFirstConnectedFormatted(),
                s.getLastSeenFormatted(),
                s.getTotalConnectedDuration());
    }

    /**
     * Read an existing CSV file and return a map of username -> csv-line.
     * Returns an empty map if the file doesn't exist or cannot be parsed.
     */
    private static Map<String, String> readExistingRows(File csvFile) {
        Map<String, String> rows = new LinkedHashMap<>();
        if (!csvFile.exists())
            return rows;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String header = reader.readLine(); // skip header
            if (header == null) return rows;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Username is the second field (index 1)
                String username = extractField(line, 1);
                if (username != null && !username.isEmpty()) {
                    rows.put(username, line);
                }
            }
        } catch (IOException e) {
            System.err.println("[Attendance] Could not read existing CSV: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Extract a quoted or unquoted CSV field by index (0-based).
     * Handles simple single-level quoting.
     */
    private static String extractField(String line, int fieldIndex) {
        int idx = 0;
        int field = 0;
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        while (idx < line.length()) {
            char c = line.charAt(idx);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                if (field == fieldIndex) return current.toString().trim();
                field++;
                current = new StringBuilder();
            } else {
                current.append(c);
            }
            idx++;
        }
        if (field == fieldIndex) return current.toString().trim();
        return null;
    }

    /** Parse the roll number (first field) from a CSV row for sorting. */
    private static int extractRoll(String csvLine) {
        String rollStr = extractField(csvLine, 0);
        if (rollStr == null) return Integer.MAX_VALUE;
        try { return Integer.parseInt(rollStr.trim()); }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    /** Escape double-quotes inside a CSV field value. */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
