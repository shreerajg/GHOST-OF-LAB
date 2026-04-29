"""
network_manager.py — Ghost of Lab Network Controller
=====================================================
Handles internet blocking/unblocking for Ghost Lab Management.

Usage:
    python network_manager.py block
    python network_manager.py unblock

Responsibilities:
    - Modify the Windows hosts file to block distracting sites
    - Back up the original hosts file before any modification
    - Restore on unblock or on startup if a backup exists (crash recovery)
    - Set/restore DNS to enforce blocking (optional DNS override)
    - Tamper guard: re-applies block if hosts file is modified while active
    - Must be run as Administrator; exits with code 2 if not elevated
"""

import sys
import os
import shutil
import ctypes
import subprocess
import time
import threading
import json
import signal

# ──────────────────────────────────────────────
#  PATHS & CONSTANTS
# ──────────────────────────────────────────────
HOSTS_PATH   = r"C:\Windows\System32\drivers\etc\hosts"
BACKUP_PATH  = r"C:\Windows\System32\drivers\etc\hosts.ghost.bak"
STATE_FILE   = os.path.join(os.path.dirname(os.path.abspath(__file__)), "nm_state.json")

MARKER_START = "# ===== GHOST LAB BLOCKER START ====="
MARKER_END   = "# ===== GHOST LAB BLOCKER END ====="

BLOCKED_DOMAINS = [
    # Social Media
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
    # Video / Streaming
    "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
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
    # Gaming
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
    # Chat / Messaging
    "discord.com", "www.discord.com",
    "web.whatsapp.com",
    "web.telegram.org",
    "messenger.com", "www.messenger.com",
    # Shopping
    "amazon.in", "www.amazon.in",
    "amazon.com", "www.amazon.com",
    "flipkart.com", "www.flipkart.com",
    "myntra.com", "www.myntra.com",
    "ajio.com", "www.ajio.com",
    "meesho.com", "www.meesho.com",
    "ebay.com", "www.ebay.com",
    # Entertainment
    "9gag.com", "www.9gag.com",
    "imgur.com", "www.imgur.com",
    "buzzfeed.com", "www.buzzfeed.com",
    # Betting / Gambling
    "dream11.com", "www.dream11.com",
    "bet365.com", "www.bet365.com",
    # Music streaming
    "spotify.com", "www.spotify.com", "open.spotify.com",
    "gaana.com", "www.gaana.com",
    "jiosaavn.com", "www.jiosaavn.com",
    "wynk.in", "www.wynk.in",
    "soundcloud.com", "www.soundcloud.com",
]

# ──────────────────────────────────────────────
#  PRIVILEGE CHECK
# ──────────────────────────────────────────────
def is_admin() -> bool:
    """Return True if the current process has administrator privileges."""
    try:
        return ctypes.windll.shell32.IsUserAnAdmin() != 0
    except Exception:
        return False


# ──────────────────────────────────────────────
#  HOSTS FILE UTILITIES
# ──────────────────────────────────────────────
def _build_block_entries() -> str:
    lines = [f"\n{MARKER_START}", "# Blocked by Ghost Lab Management - DO NOT EDIT"]
    for domain in BLOCKED_DOMAINS:
        lines.append(f"127.0.0.1 {domain}")
    lines.append(MARKER_END + "\n")
    return "\n".join(lines)


def _hosts_has_blocks() -> bool:
    try:
        with open(HOSTS_PATH, "r", encoding="utf-8", errors="replace") as f:
            return MARKER_START in f.read()
    except Exception:
        return False


def _flush_dns():
    try:
        subprocess.run(["ipconfig", "/flushdns"], capture_output=True, timeout=10)
        print("[NetworkManager] DNS cache flushed.")
    except Exception as e:
        print(f"[NetworkManager] Warning: Could not flush DNS: {e}")


def _set_dns_loopback():
    """
    Set DNS on all active network adapters to 127.0.0.1 (loopback).
    This is a secondary enforcement layer on top of the hosts file.
    """
    try:
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-NetAdapter | Where-Object {$_.Status -eq 'Up'} | Select-Object -ExpandProperty Name"],
            capture_output=True, text=True, timeout=10
        )
        adapters = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        for adapter in adapters:
            subprocess.run(
                ["netsh", "interface", "ip", "set", "dns",
                 f"name={adapter}", "static", "127.0.0.1"],
                capture_output=True, timeout=10
            )
        if adapters:
            print(f"[NetworkManager] DNS set to loopback on: {', '.join(adapters)}")
    except Exception as e:
        print(f"[NetworkManager] Warning: Could not set DNS: {e}")


def _restore_dns_dhcp():
    """Restore DNS to DHCP (automatic) on all active adapters."""
    try:
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-NetAdapter | Where-Object {$_.Status -eq 'Up'} | Select-Object -ExpandProperty Name"],
            capture_output=True, text=True, timeout=10
        )
        adapters = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        for adapter in adapters:
            subprocess.run(
                ["netsh", "interface", "ip", "set", "dns",
                 f"name={adapter}", "dhcp"],
                capture_output=True, timeout=10
            )
        if adapters:
            print(f"[NetworkManager] DNS restored to DHCP on: {', '.join(adapters)}")
    except Exception as e:
        print(f"[NetworkManager] Warning: Could not restore DNS: {e}")


# ──────────────────────────────────────────────
#  STATE PERSISTENCE
# ──────────────────────────────────────────────
def _save_state(blocked: bool):
    try:
        with open(STATE_FILE, "w") as f:
            json.dump({"blocked": blocked}, f)
    except Exception:
        pass


def _load_state() -> bool:
    try:
        with open(STATE_FILE, "r") as f:
            return json.load(f).get("blocked", False)
    except Exception:
        return False


# ──────────────────────────────────────────────
#  CORE OPERATIONS
# ──────────────────────────────────────────────
def block_internet() -> int:
    """
    Block distracting sites:
      1. Back up original hosts file (if no backup yet)
      2. Append block entries to hosts file
      3. Set DNS to loopback
      4. Flush DNS
    Returns 0 on success, 1 on failure.
    """
    try:
        # Already blocked?
        if _hosts_has_blocks():
            print("[NetworkManager] Sites already blocked.")
            _save_state(True)
            return 0

        # Backup
        if not os.path.exists(BACKUP_PATH):
            shutil.copy2(HOSTS_PATH, BACKUP_PATH)
            print(f"[NetworkManager] Hosts file backed up to {BACKUP_PATH}")

        # Append block entries
        entries = _build_block_entries()
        with open(HOSTS_PATH, "a", encoding="utf-8") as f:
            f.write(entries)

        # DNS override
        _set_dns_loopback()

        # Flush DNS
        _flush_dns()

        _save_state(True)
        print(f"[NetworkManager] Blocked {len(BLOCKED_DOMAINS)} domains.")
        return 0

    except PermissionError:
        print("[NetworkManager] ERROR: Permission denied. Run as Administrator.")
        return 2
    except Exception as e:
        print(f"[NetworkManager] ERROR during block: {e}")
        return 1


def unblock_internet() -> int:
    """
    Restore internet:
      1. Restore original hosts file from backup (or strip markers)
      2. Restore DNS to DHCP
      3. Flush DNS
    Returns 0 on success, 1 on failure.
    """
    try:
        restored = False

        # Try restoring from backup first
        if os.path.exists(BACKUP_PATH):
            shutil.copy2(BACKUP_PATH, HOSTS_PATH)
            os.remove(BACKUP_PATH)
            print("[NetworkManager] Hosts file restored from backup.")
            restored = True
        elif _hosts_has_blocks():
            # Strip markers manually
            with open(HOSTS_PATH, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
            start_idx = content.find(MARKER_START)
            end_idx   = content.find(MARKER_END)
            if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
                cleaned = content[:start_idx] + content[end_idx + len(MARKER_END):]
                # Collapse excessive blank lines
                import re
                cleaned = re.sub(r'\n{3,}', '\n\n', cleaned)
                with open(HOSTS_PATH, "w", encoding="utf-8") as f:
                    f.write(cleaned)
                print("[NetworkManager] Block entries removed from hosts file.")
                restored = True

        if not restored:
            print("[NetworkManager] Nothing to restore.")

        # Restore DNS
        _restore_dns_dhcp()

        # Flush DNS
        _flush_dns()

        _save_state(False)
        print("[NetworkManager] Internet unblocked.")
        return 0

    except PermissionError:
        print("[NetworkManager] ERROR: Permission denied. Run as Administrator.")
        return 2
    except Exception as e:
        print(f"[NetworkManager] ERROR during unblock: {e}")
        return 1


def failsafe_recovery() -> int:
    """
    Called on startup: if a backup exists it means the previous session
    crashed while blocked. Restore automatically.
    """
    if os.path.exists(BACKUP_PATH):
        print("[NetworkManager] Crash recovery: backup found — restoring hosts file.")
        return unblock_internet()
    print("[NetworkManager] No crash recovery needed.")
    return 0


# ──────────────────────────────────────────────
#  TAMPER GUARD (background thread)
# ──────────────────────────────────────────────
_guard_thread: threading.Thread | None = None
_guard_stop   = threading.Event()


def _tamper_guard_loop():
    """
    Continuously monitors the hosts file.
    If block entries disappear while we're supposed to be blocking, re-apply.
    """
    print("[NetworkManager] Tamper guard started.")
    while not _guard_stop.is_set():
        if _load_state() and not _hosts_has_blocks():
            print("[NetworkManager] Tamper detected! Re-applying block...")
            block_internet()
        _guard_stop.wait(timeout=5)  # Check every 5 seconds
    print("[NetworkManager] Tamper guard stopped.")


def start_guard():
    global _guard_thread, _guard_stop
    _guard_stop.clear()
    _guard_thread = threading.Thread(target=_tamper_guard_loop, daemon=True, name="GhostTamperGuard")
    _guard_thread.start()


def stop_guard():
    _guard_stop.set()
    if _guard_thread:
        _guard_thread.join(timeout=10)


# ──────────────────────────────────────────────
#  SELF-ELEVATION
# ──────────────────────────────────────────────
def self_elevate_and_run(command: str):
    """
    Re-launch this script as Administrator using Windows ShellExecuteW.
    The elevated process writes its output + exit code to a temp file so
    this (non-elevated) caller can capture the result and forward it.
    Returns the exit code from the elevated run (0=OK, 1=fail).
    """
    import tempfile

    script   = os.path.abspath(__file__)
    tmp_out  = tempfile.mktemp(suffix=".txt", prefix="ghost_nm_")
    tmp_code = tempfile.mktemp(suffix=".txt", prefix="ghost_nm_exit_")

    # We run:  python "<script>" <command>  >tmp_out 2>&1
    # and store exit code in tmp_code.
    # Use cmd.exe as the intermediary so we can redirect stdout to a file.
    cmd_args = f'/c python "{script}" {command} > "{tmp_out}" 2>&1 && echo 0 > "{tmp_code}" || echo 1 > "{tmp_code}"'

    # ShellExecuteW: hwnd, verb, file, params, dir, show
    # show=0 (SW_HIDE) — no console window
    ret = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", "cmd.exe", cmd_args, None, 0
    )

    if ret <= 32:
        print(f"[NetworkManager] UAC elevation failed or was denied (ShellExecute={ret}).")
        return 1

    # Wait for the elevated process to finish (poll temp files)
    for _ in range(60):   # wait up to 30 seconds
        time.sleep(0.5)
        if os.path.exists(tmp_code):
            break

    # Print output
    if os.path.exists(tmp_out):
        try:
            with open(tmp_out, "r", encoding="utf-8", errors="replace") as f:
                content = f.read().strip()
            if content:
                print(content)
        except Exception:
            pass
        try:
            os.remove(tmp_out)
        except Exception:
            pass

    # Read exit code
    exit_code = 1
    if os.path.exists(tmp_code):
        try:
            with open(tmp_code, "r") as f:
                exit_code = int(f.read().strip())
        except Exception:
            pass
        try:
            os.remove(tmp_code)
        except Exception:
            pass

    return exit_code


# ──────────────────────────────────────────────
#  ENTRY POINT
# ──────────────────────────────────────────────
def main():
    if len(sys.argv) < 2:
        print("Usage: python network_manager.py [block|unblock|recover|guard]")
        sys.exit(1)

    command = sys.argv[1].strip().lower()

    # For the "guard" mode we cannot self-elevate (it's long-running),
    # so we just report code 2 and let Java handle elevation.
    if command == "guard":
        if not is_admin():
            print("[NetworkManager] ERROR: guard mode requires Administrator privileges.")
            sys.exit(2)

        def handle_exit(signum, frame):
            print("[NetworkManager] Guard received shutdown signal — unblocking.")
            unblock_internet()
            stop_guard()
            sys.exit(0)

        signal.signal(signal.SIGINT,  handle_exit)
        signal.signal(signal.SIGTERM, handle_exit)

        start_guard()
        while True:
            time.sleep(1)

    # For block/unblock/recover: self-elevate if needed
    if not is_admin():
        print(f"[NetworkManager] Not admin — requesting elevation for '{command}'...")
        code = self_elevate_and_run(command)
        sys.exit(code)

    # Already admin — run directly
    if command == "block":
        sys.exit(block_internet())

    elif command == "unblock":
        sys.exit(unblock_internet())

    elif command == "recover":
        sys.exit(failsafe_recovery())

    else:
        print(f"[NetworkManager] Unknown command: '{command}'")
        print("Valid commands: block, unblock, recover, guard")
        sys.exit(1)


if __name__ == "__main__":
    main()
