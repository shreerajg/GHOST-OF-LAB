package com.ghost.ui;

import com.ghost.database.DatabaseManager;
import com.ghost.database.User;
import com.ghost.util.IconUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginView {

    public static void show(Stage stage) {
        IconUtil.setIcon(stage);
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #2b2b2b;");

        Label title = new Label("GHOST LOGIN");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");

        TextField userField = new TextField();
        userField.setPromptText("Username");

        PasswordField funcField = new PasswordField();
        funcField.setPromptText("Password");

        Button loginBtn = new Button("Login");
        loginBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: white;");

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-text-fill: red;");

        // Add keyboard navigation after all components declared
        userField.setOnAction(e -> funcField.requestFocus()); // Enter moves to password
        funcField.setOnAction(e -> loginBtn.fire()); // Enter submits login

        loginBtn.setOnAction(e -> {
            String user = userField.getText();
            String pass = funcField.getText();

            User u = DatabaseManager.login(user, pass);
            if (u != null) {
                if ("ADMIN".equalsIgnoreCase(u.getRole())) {
                    AdminDashboard.show(stage, u);
                } else {
                    StudentDashboard.show(stage, u);
                }
            } else {
                statusLbl.setText("Invalid credentials");
            }
        });

        Button registerStudentBtn = new Button("Student Registration");
        registerStudentBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-underline: true;");
        registerStudentBtn.setOnAction(e -> StudentRegistrationView.show(stage));

        root.getChildren().addAll(title, userField, funcField, loginBtn, statusLbl, registerStudentBtn);

        Scene scene = new Scene(root, 400, 350);
        stage.setScene(scene);
        stage.setTitle("Ghost - Login");
        stage.show();
    }
}
