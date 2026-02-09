package com.ghost;

import com.ghost.database.DatabaseManager;
import com.ghost.ui.LoginView;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Initialize Database
        DatabaseManager.init();

        // Always show login screen - no auto-login
        LoginView.show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
