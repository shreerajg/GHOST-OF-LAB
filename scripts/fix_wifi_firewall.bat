@echo off
SETLOCAL EnableDelayedExpansion
title Ghost Lab - WiFi Firewall Fix
color 0A

echo ============================================
echo   Ghost of Lab - WiFi Connection Fixer
echo ============================================
echo.

:: Must run as admin to change firewall rules
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [!] This script must be run as ADMINISTRATOR.
    echo     Right-click and choose "Run as administrator"
    echo.
    pause
    exit /b 1
)

echo [1/4] Detecting your WiFi IP address...
echo.
:: Show all IPs
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set "ip=%%a"
    set "ip=!ip: =!"
    echo     Found IP: !ip!
)
echo.

echo [2/4] Removing old Ghost firewall rules (if any)...
netsh advfirewall firewall delete rule name="Ghost Lab Server TCP" >nul 2>&1
netsh advfirewall firewall delete rule name="Ghost Lab Discovery UDP" >nul 2>&1
netsh advfirewall firewall delete rule name="Ghost Lab Client TCP" >nul 2>&1
echo     Done.
echo.

echo [3/4] Adding firewall rules for ALL network profiles (Domain, Private, Public)...
echo     This is required because WiFi is often classified as "Public" network.
echo.

:: TCP port 5555 - Ghost Server (Admin listens, students connect)
netsh advfirewall firewall add rule ^
    name="Ghost Lab Server TCP" ^
    protocol=TCP ^
    dir=in ^
    localport=5555 ^
    action=allow ^
    profile=any ^
    description="Ghost of Lab - Admin server port for student connections"
echo     [TCP 5555 Inbound] Added.

:: UDP port 5556 - Discovery broadcast (Admin sends, students receive)
netsh advfirewall firewall add rule ^
    name="Ghost Lab Discovery UDP" ^
    protocol=UDP ^
    dir=in ^
    localport=5556 ^
    action=allow ^
    profile=any ^
    description="Ghost of Lab - Discovery broadcast receiver port"
echo     [UDP 5556 Inbound] Added.

:: Also allow outbound UDP for discovery (some restrictive setups block outbound too)
netsh advfirewall firewall add rule ^
    name="Ghost Lab Discovery UDP OUT" ^
    protocol=UDP ^
    dir=out ^
    localport=5556 ^
    action=allow ^
    profile=any
echo     [UDP 5556 Outbound] Added.

:: Allow Java.exe specifically (catches all ports Java uses)
for %%J in (
    "C:\Program Files\Java\jdk-17\bin\java.exe"
    "C:\Program Files\Java\jdk-21\bin\java.exe"
    "C:\Program Files\Java\jdk-11\bin\java.exe"
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.0\bin\java.exe"
    "C:\Program Files\Microsoft\jdk-17.0.0\bin\java.exe"
) do (
    if exist %%J (
        netsh advfirewall firewall add rule name="Ghost Lab Java" program=%%J action=allow dir=in profile=any >nul 2>&1
        echo     [Java EXE Rule] Added for %%J
    )
)

echo.
echo [4/4] Verifying rules were added...
netsh advfirewall firewall show rule name="Ghost Lab Server TCP" | findstr /i "enabled"
netsh advfirewall firewall show rule name="Ghost Lab Discovery UDP" | findstr /i "enabled"
echo.

echo ============================================
echo   DONE! Now do the following:
echo ============================================
echo.
echo   ON THE ADMIN PC (this machine or the lab teacher's PC):
echo     1. Run Ghost and choose "Admin" / "Teacher" mode
echo     2. Start the session
echo.
echo   ON EACH STUDENT PC:
echo     1. Run this same script first (to open firewall on their side too)
echo     2. Then run Ghost and choose "Student" mode
echo     3. Ghost will auto-discover the Admin within a few seconds
echo.
echo   If students STILL can't connect after this:
echo     - Make sure all PCs are on the SAME WiFi network (same SSID)
echo     - Check if the WiFi router has "AP Isolation" or "Client Isolation"
echo       turned ON - this blocks PC-to-PC traffic and must be DISABLED
echo       in the router settings.
echo     - Try: ping [Admin IP] from a student PC to test basic connectivity
echo.
pause
