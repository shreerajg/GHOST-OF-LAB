package com.ghost.ui;

import com.ghost.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class StudentRegistrationView {

    public static void show(Stage stage) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #222;");

        Label title = new Label("STUDENT REGISTRATION");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name / Username");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Create Password");

        Button registerBtn = new Button("Register");
        registerBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-text-fill: red;");

        Button backBtn = new Button("Back to Login");
        backBtn.setOnAction(e -> LoginView.show(stage));

        registerBtn.setOnAction(e -> {
            String name = nameField.getText();
            String pass = passField.getText();

            if (name.isEmpty() || pass.isEmpty()) {
                statusLbl.setText("All fields required");
                return;
            }

            if (DatabaseManager.registerStudent(name, pass, "{}")) {
                statusLbl.setText("Registration Successful!");
                statusLbl.setStyle("-fx-text-fill: green;");
                // Auto login or go back
                LoginView.show(stage);
            } else {
                statusLbl.setText("Registration Failed (Username taken?)");
                statusLbl.setStyle("-fx-text-fill: red;");
            }
        });

        root.getChildren().addAll(title, nameField, passField, registerBtn, statusLbl, backBtn);

        Scene scene = new Scene(root, 400, 400);
        stage.setScene(scene);
        stage.setTitle("Ghost - Registration");
    }
}
