package com.ghost;

import com.ghost.database.DatabaseManager;
import com.ghost.ui.LoginView;
import com.ghost.util.NetworkManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Prevent JavaFX from exiting when all windows are hidden (needed for system
        // tray)
        Platform.setImplicitExit(false);

        // Initialize Database
        DatabaseManager.init();

        // Startup crash-recovery: if previous session crashed while blocking,
        // Python will detect the backup and restore the hosts file automatically.
        NetworkManager.recoverOnStartup();

        // Register JVM shutdown hook to ALWAYS restore hosts file on exit.
        // This handles: System.exit(), Ctrl+C, Task Manager kill, crashes.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Shutdown hook: Restoring hosts file via Python...");
            NetworkManager.restoreHostsFile();
        }, "GhostShutdownHook"));

        // Always show login screen - no auto-login
        LoginView.show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
