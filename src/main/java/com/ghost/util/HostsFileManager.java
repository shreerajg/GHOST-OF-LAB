package com.ghost.util;

import java.io.*;
import java.nio.file.*;

/**
 * Manages the Windows hosts file to block/unblock websites.
 * Uses hosts file redirects (127.0.0.1) instead of disabling network adapters,
 * so the LAN connection between admin and students stays intact.
 * 
 * Includes a comprehensive blocklist of non-educational sites.
 * Automatically backs up and restores the original hosts file.
 */
public class HostsFileManager {
    private static final String HOSTS_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts";
    private static final String BACKUP_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts.ghost.bak";
    private static final String GHOST_MARKER_START = "# ===== GHOST LAB BLOCKER START =====";
    private static final String GHOST_MARKER_END = "# ===== GHOST LAB BLOCKER END =====";

    private static volatile boolean blocked = false;

    /**
     * Comprehensive list of non-educational/distracting sites to block.
     * Each domain is blocked along with its www. variant.
     */
    private static final String[] BLOCKED_DOMAINS = {
            // Social Media
            "facebook.com", "www.facebook.com",
            "instagram.com", "www.instagram.com",
            "twitter.com", "www.twitter.com",
            "x.com", "www.x.com",
            "tiktok.com", "www.tiktok.com",
            "snapchat.com", "www.snapchat.com",
            "reddit.com", "www.reddit.com",
            "pinterest.com", "www.pinterest.com",
            "tumblr.com", "www.tumblr.com",
            "linkedin.com", "www.linkedin.com",

            // Video/Streaming
            "youtube.com", "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
            "netflix.com", "www.netflix.com",
            "twitch.tv", "www.twitch.tv",
            "hotstar.com", "www.hotstar.com",
            "primevideo.com", "www.primevideo.com",
            "disneyplus.com", "www.disneyplus.com",
            "hulu.com", "www.hulu.com",
            "vimeo.com", "www.vimeo.com",
            "dailymotion.com", "www.dailymotion.com",
            "jiocinema.com", "www.jiocinema.com",
            "sonyliv.com", "www.sonyliv.com",
            "zee5.com", "www.zee5.com",
            "mxplayer.in", "www.mxplayer.in",

            // Gaming
            "store.steampowered.com", "steampowered.com",
            "epicgames.com", "www.epicgames.com",
            "roblox.com", "www.roblox.com",
            "miniclip.com", "www.miniclip.com",
            "poki.com", "www.poki.com",
            "crazygames.com", "www.crazygames.com",
            "y8.com", "www.y8.com",
            "friv.com", "www.friv.com",
            "itch.io", "www.itch.io",
            "chess.com", "www.chess.com",

            // Chat/Messaging
            "discord.com", "www.discord.com",
            "web.whatsapp.com",
            "web.telegram.org",
            "messenger.com", "www.messenger.com",

            // Shopping
            "amazon.in", "www.amazon.in",
            "amazon.com", "www.amazon.com",
            "flipkart.com", "www.flipkart.com",
            "myntra.com", "www.myntra.com",
            "ajio.com", "www.ajio.com",
            "meesho.com", "www.meesho.com",
            "ebay.com", "www.ebay.com",

            // Entertainment / Memes
            "9gag.com", "www.9gag.com",
            "imgur.com", "www.imgur.com",
            "buzzfeed.com", "www.buzzfeed.com",

            // Betting / Gambling
            "dream11.com", "www.dream11.com",
            "bet365.com", "www.bet365.com",

            // Adult content (basic blocks)
            "pornhub.com", "www.pornhub.com",
            "xvideos.com", "www.xvideos.com",
            "xnxx.com", "www.xnxx.com",
            "xhamster.com", "www.xhamster.com",

            // Music streaming (optional - distracting)
            "spotify.com", "www.spotify.com", "open.spotify.com",
            "gaana.com", "www.gaana.com",
            "jiosaavn.com", "www.jiosaavn.com",
            "wynk.in", "www.wynk.in",
            "soundcloud.com", "www.soundcloud.com"
    };

    /**
     * Block all distracting sites by modifying the hosts file.
     * Backs up the original hosts file first.
     */
    public static synchronized boolean blockSites() {
        try {
            Path hostsPath = Paths.get(HOSTS_PATH);
            Path backupPath = Paths.get(BACKUP_PATH);

            // Read current hosts file
            String currentContent = new String(Files.readAllBytes(hostsPath));

            // Don't add duplicates - if our markers already exist, skip
            if (currentContent.contains(GHOST_MARKER_START)) {
                System.out.println("[HostsManager] Sites already blocked");
                blocked = true;
                return true;
            }

            // Backup original hosts file (only if backup doesn't exist)
            if (!Files.exists(backupPath)) {
                Files.copy(hostsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[HostsManager] Backed up original hosts file");
            }

            // Build block entries
            StringBuilder blockEntries = new StringBuilder();
            blockEntries.append("\n").append(GHOST_MARKER_START).append("\n");
            blockEntries.append("# Blocked by Ghost Lab Management - DO NOT EDIT\n");
            for (String domain : BLOCKED_DOMAINS) {
                blockEntries.append("127.0.0.1 ").append(domain).append("\n");
            }
            blockEntries.append(GHOST_MARKER_END).append("\n");

            // Append to hosts file
            Files.write(hostsPath, blockEntries.toString().getBytes(),
                    StandardOpenOption.APPEND);

            // Flush DNS cache
            flushDns();

            blocked = true;
            System.out.println("[HostsManager] Blocked " + BLOCKED_DOMAINS.length + " domains");
            return true;

        } catch (IOException e) {
            System.err.println("[HostsManager] Error blocking sites: " + e.getMessage());
            System.err.println("[HostsManager] Make sure app is running as Administrator!");
            return false;
        }
    }

    /**
     * Restore the original hosts file by removing Ghost's block entries.
     * This is safe to call even if no blocking was active.
     */
    public static synchronized boolean restoreHostsFile() {
        try {
            Path hostsPath = Paths.get(HOSTS_PATH);
            Path backupPath = Paths.get(BACKUP_PATH);

            // Method 1: If backup exists, restore from backup
            if (Files.exists(backupPath)) {
                Files.copy(backupPath, hostsPath, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(backupPath);
                System.out.println("[HostsManager] Restored hosts file from backup");
            } else {
                // Method 2: Remove Ghost markers from hosts file
                String content = new String(Files.readAllBytes(hostsPath));
                if (content.contains(GHOST_MARKER_START)) {
                    int startIdx = content.indexOf(GHOST_MARKER_START);
                    int endIdx = content.indexOf(GHOST_MARKER_END);
                    if (endIdx > startIdx) {
                        String cleaned = content.substring(0, startIdx)
                                + content.substring(endIdx + GHOST_MARKER_END.length());
                        // Remove trailing newlines left behind
                        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
                        Files.write(hostsPath, cleaned.getBytes());
                        System.out.println("[HostsManager] Removed Ghost block entries from hosts file");
                    }
                } else {
                    System.out.println("[HostsManager] No Ghost entries found in hosts file - nothing to restore");
                }
            }

            // Flush DNS cache
            flushDns();

            blocked = false;
            return true;

        } catch (IOException e) {
            System.err.println("[HostsManager] Error restoring hosts file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Flush DNS cache so hosts file changes take effect immediately.
     */
    private static void flushDns() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "ipconfig /flushdns");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            System.out.println("[HostsManager] DNS cache flushed");
        } catch (Exception e) {
            System.err.println("[HostsManager] Failed to flush DNS: " + e.getMessage());
        }
    }

    /**
     * Returns whether sites are currently blocked.
     */
    public static boolean isBlocked() {
        return blocked;
    }

    /**
     * Check if the hosts file currently has Ghost entries.
     * Useful on startup to detect leftover blocks from a crash.
     */
    public static boolean hasLeftoverBlocks() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(HOSTS_PATH)));
            return content.contains(GHOST_MARKER_START);
        } catch (IOException e) {
            return false;
        }
    }
}
