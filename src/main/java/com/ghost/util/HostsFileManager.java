package com.ghost.util;

/**
 * HostsFileManager — DEPRECATED STUB
 *
 * All Java-based hosts file / DNS logic has been REMOVED and replaced
 * by the Python-based NetworkManager (network_manager.py).
 *
 * This class now delegates every call to {@link NetworkManager} so
 * existing callers compile without modification. New code should call
 * NetworkManager directly.
 *
 * @deprecated Use {@link NetworkManager} instead.
 */
@Deprecated
public class HostsFileManager {

    /** @deprecated Use {@link NetworkManager#blockSites()} */
    @Deprecated
    public static synchronized boolean blockSites() {
        return NetworkManager.blockSites();
    }

    /** @deprecated Use {@link NetworkManager#restoreHostsFile()} */
    @Deprecated
    public static synchronized boolean restoreHostsFile() {
        return NetworkManager.restoreHostsFile();
    }

    /** @deprecated Use {@link NetworkManager#hasLeftoverBlocks()} */
    @Deprecated
    public static boolean hasLeftoverBlocks() {
        return NetworkManager.hasLeftoverBlocks();
    }

    /** @deprecated Use {@link NetworkManager#isBlocked()} */
    @Deprecated
    public static boolean isBlocked() {
        return NetworkManager.isBlocked();
    }
}
