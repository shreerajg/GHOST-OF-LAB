# Ghost of Lab 👻

**Ghost of Lab** is a robust Java-based network application designed for computer labs, classrooms, and focused environments. It provides a seamless way for teachers (Admins) to manage, monitor, and maintain focus among students during lab sessions over a local area network (LAN/WiFi).

## 🌟 Features

* **Role-Based Modes**: 
  * **Admin (Teacher) Mode**: Start a server session, manage student connections, and control the lab environment.
  * **Student Mode**: Auto-discover the admin server and connect seamlessly without needing to type IP addresses.
* **Smart Network Discovery**: Uses UDP broadcast for auto-discovery and TCP for reliable client-server communication.
* **Distraction Blocker**: Includes a self-elevating mechanism that modifies the Windows `hosts` file to block non-educational sites (social media, streaming, gaming, etc.) while keeping the LAN connection intact.
* **User Authentication**: Built-in SQLite database for student registration and secure login.
* **Firewall Configuration Tool**: Includes a dedicated batch script to automatically configure Windows Firewall rules for seamless PC-to-PC communication over WiFi.

## 🛠️ Tech Stack

* **Language**: Java
* **UI Framework**: JavaFX
* **Database**: SQLite
* **Networking**: Native Java Sockets (TCP/UDP)
* **OS Integration**: Windows Batch & PowerShell (for hosts file management and firewall configuration)

## 📂 Project Structure

```text
GHOST/
├── lib/               # Dependencies and JavaFX libraries
├── scripts/           # Utility scripts (Firewall fix, SQLite database)
├── src/               
│   └── main/          
│       ├── java/      # Java source code (com.ghost.*)
│       └── resources/ # UI styles, icons, and static assets
├── out/               # Compiled class files (auto-generated)
└── run.bat            # Main build and execution script
```

## 🚀 Getting Started

### Prerequisites
* **Java Development Kit (JDK)**: JDK 11, 17, or 21 installed and added to your system's PATH.
* **Windows OS**: Recommended (required for the hosts file blocker and automated firewall scripts).

### Installation & Setup

1. **Configure Firewall** (Crucial for WiFi networks):
   Navigate to the `scripts` folder, right-click on `fix_wifi_firewall.bat`, and select **Run as administrator**. This will open the necessary ports (TCP 5555, UDP 5556) for the Ghost server and discovery service.
   
2. **Build and Run**:
   Double-click the `run.bat` file in the root directory. This script will automatically:
   - Clean previous builds
   - Compile the Java source code
   - Copy resources
   - Launch the application

### Usage

1. **Admin/Teacher PC**:
   * Run the application and select Admin mode.
   * Start the session and wait for students to connect.
2. **Student PC**:
   * Ensure the firewall script is run first.
   * Run the application and log in as a Student (register if it's the first time).
   * The app will automatically discover the Admin's server and connect.

## ⚠️ Important Notes
* The distraction blocker feature requires elevated privileges to modify the Windows `hosts` file. It uses PowerShell to self-elevate securely only when necessary.
* Make sure all PCs (Admin and Students) are connected to the **same WiFi network** (same SSID).
* Ensure your WiFi router does not have "AP Isolation" or "Client Isolation" enabled, as this blocks direct PC-to-PC communication.

## 🤝 Contributing
Feel free to fork this project, submit pull requests, or report issues to help improve Ghost of Lab!
