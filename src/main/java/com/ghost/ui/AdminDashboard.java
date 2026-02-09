package com.ghost.ui;

import com.ghost.database.User;
import com.ghost.net.CommandPacket;
import com.ghost.net.GhostServer;
import com.ghost.util.ScreenCapture;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AdminDashboard {
    private static GhostServer server;
    private static FlowPane thumbnailGrid;
    private static VBox chatBox;
    private static TextArea chatArea;
    private static boolean internetKilled = false;
    private static Map<String, VBox> studentCards = new HashMap<>();
    private static Map<String, ImageView> studentImages = new HashMap<>();

    public static void show(Stage stage, User user) {
        if (server == null) {
            server = new GhostServer();
            server.setScreenListener(new GhostServer.ScreenUpdateListener() {
                @Override
                public void onScreenUpdate(String clientName, String base64Image) {
                    Platform.runLater(() -> updateStudentScreen(clientName, base64Image));
                }

                @Override
                public void onShellOutput(String clientName, String output) {
                    Platform.runLater(() -> {
                        if (chatArea != null) {
                            chatArea.appendText("\n--- Output from " + clientName + " ---\n" + output + "\n");
                            chatArea.setScrollTop(Double.MAX_VALUE);
                        }
                    });
                }
            });
            server.setStatusListener(new GhostServer.ClientStatusListener() {
                @Override
                public void onClientConnected(String clientName) {
                    // Card is created on first screen update
                }

                @Override
                public void onClientDisconnected(String clientName) {
                    Platform.runLater(() -> removeStudentCard(clientName));
                }
            });
            server.start();
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1a1a2e, #16213e);");

        // ========== LEFT SIDEBAR ==========
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20, 15, 20, 15));
        sidebar.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 0 20 20 0;");
        sidebar.setPrefWidth(220);
        sidebar.setAlignment(Pos.TOP_CENTER);

        // Logo/Title
        Label logo = new Label("👻 GHOST");
        logo.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #00ffaa;");

        Label subtitle = new Label("COMMANDER");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888; -fx-letter-spacing: 3px;");

        // User info
        VBox userBox = new VBox(5);
        userBox.setAlignment(Pos.CENTER);
        userBox.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 10; -fx-padding: 15;");
        Circle avatar = new Circle(25, Color.web("#00ffaa"));
        Label userName = new Label(user.getUsername().toUpperCase());
        userName.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        Label roleLabel = new Label("Administrator");
        roleLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px;");
        userBox.getChildren().addAll(avatar, userName, roleLabel);

        // Control Sections
        VBox controlSection = createControlSection("POWER CONTROLS",
                createStyledButton("🔒 LOCK ALL", "#e74c3c",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.LOCK, "ADMIN", "{}"))),
                createStyledButton("🔓 UNLOCK ALL", "#2ecc71",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.UNLOCK, "ADMIN", "{}"))),
                createStyledButton("⏻ SHUTDOWN ALL", "#9b59b6",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.SHUTDOWN, "ADMIN", "{}"))));

        VBox networkSection = createControlSection("NETWORK",
                createNetworkToggle());

        VBox streamSection = createControlSection("SCREEN SHARE",
                createScreenShareToggle());

        VBox extraSection = createControlSection("EXTRAS",
                createStyledButton("🔇 MUTE ALL", "#f39c12",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.MUTE, "ADMIN", "{}"))),
                createStyledButton("🖐️ BLOCK INPUT", "#e67e22",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.BLOCK_INPUT, "ADMIN", "BLOCK"))),
                createStyledButton("✋ UNBLOCK INPUT", "#27ae60",
                        () -> server.broadcast(new CommandPacket(CommandPacket.Type.BLOCK_INPUT, "ADMIN", "UNBLOCK"))));

        VBox fileSection = createControlSection("FILE SHARING",
                createStyledButton("📁 SEND FILES", "#3498db", () -> sendFilesToStudents(stage)));

        // Use ScrollPane for sidebar to handle overflow
        VBox sidebarContent = new VBox(10);
        sidebarContent.getChildren().addAll(logo, subtitle, new Separator(), userBox, new Separator(),
                controlSection, networkSection, streamSection, extraSection, fileSection);

        ScrollPane sidebarScroll = new ScrollPane(sidebarContent);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sidebar.getChildren().add(sidebarScroll);
        VBox.setVgrow(sidebarScroll, Priority.ALWAYS);
        root.setLeft(sidebar);

        // ========== CENTER - THUMBNAIL GRID ==========
        VBox centerContainer = new VBox(10);
        centerContainer.setPadding(new Insets(20));

        Label gridTitle = new Label("CONNECTED STUDENTS");
        gridTitle.setStyle("-fx-font-size: 16px; -fx-text-fill: #888; -fx-font-weight: bold;");

        thumbnailGrid = new FlowPane(20, 20);
        thumbnailGrid.setPadding(new Insets(10));
        thumbnailGrid.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(thumbnailGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Cards added dynamically when students connect

        centerContainer.getChildren().addAll(gridTitle, scrollPane);
        root.setCenter(centerContainer);

        // ========== RIGHT - CHAT PANEL ==========
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(20));
        rightPanel.setPrefWidth(280);
        rightPanel.setStyle("-fx-background-color: rgba(0,0,0,0.2);");

        Label chatTitle = new Label("💬 BROADCAST CHAT");
        chatTitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #00ffaa; -fx-font-weight: bold;");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-control-inner-background: #1a1a2e; -fx-text-fill: #ccc; -fx-font-family: 'Consolas';");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        HBox chatInput = new HBox(10);
        TextField msgField = new TextField();
        msgField.setPromptText("Type message...");
        msgField.setStyle("-fx-background-color: #2a2a4e; -fx-text-fill: white; -fx-prompt-text-fill: #666;");
        HBox.setHgrow(msgField, Priority.ALWAYS);

        Button sendBtn = new Button("SEND");
        sendBtn.setStyle("-fx-background-color: #00ffaa; -fx-text-fill: #1a1a2e; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> {
            String msg = msgField.getText();
            if (!msg.isEmpty()) {
                chatArea.appendText("[ADMIN]: " + msg + "\n");
                server.broadcast(new CommandPacket(CommandPacket.Type.MSG, "ADMIN", msg));
                msgField.clear();
            }
        });
        chatInput.getChildren().addAll(msgField, sendBtn);

        rightPanel.getChildren().addAll(chatTitle, chatArea, chatInput);
        root.setRight(rightPanel);

        // ========== BOTTOM - COMMAND CONSOLE ==========
        HBox console = new HBox(15);
        console.setPadding(new Insets(15, 20, 15, 20));
        console.setAlignment(Pos.CENTER_LEFT);
        console.setStyle("-fx-background-color: rgba(0,0,0,0.4);");

        Label prompt = new Label("CMD >");
        prompt.setStyle("-fx-text-fill: #00ff00; -fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        TextField cmdInput = new TextField();
        cmdInput.setPromptText("Enter command to execute on all students...");
        cmdInput.setStyle(
                "-fx-background-color: #1a1a2e; -fx-text-fill: #0f0; -fx-font-family: 'Consolas'; -fx-prompt-text-fill: #555;");
        HBox.setHgrow(cmdInput, Priority.ALWAYS);

        Button execBtn = new Button("EXECUTE");
        execBtn.setStyle(
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        execBtn.setOnAction(e -> {
            String cmd = cmdInput.getText();
            if (!cmd.isEmpty()) {
                server.broadcast(new CommandPacket(CommandPacket.Type.SHELL, "ADMIN", cmd));
                chatArea.appendText("[CMD]: " + cmd + "\n");
                cmdInput.clear();
            }
        });

        console.getChildren().addAll(prompt, cmdInput, execBtn);
        root.setBottom(console);

        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.setTitle("Ghost - Admin Control Center");
        stage.setOnCloseRequest(e -> System.exit(0));
    }

    private static VBox createControlSection(String title, javafx.scene.Node... controls) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 10; -fx-padding: 15;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-font-weight: bold;");
        section.getChildren().add(titleLabel);

        for (javafx.scene.Node ctrl : controls) {
            section.getChildren().add(ctrl);
        }
        return section;
    }

    private static Button createStyledButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 8; -fx-padding: 12 15; -fx-cursor: hand;");
        btn.setOnAction(e -> action.run());

        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + "-fx-opacity: 0.8;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-opacity: 0.8;", "")));

        return btn;
    }

    private static HBox createNetworkToggle() {
        HBox toggleBox = new HBox(10);
        toggleBox.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("● ONLINE");
        statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");

        ToggleButton toggle = new ToggleButton("KILL");
        toggle.setStyle(
                "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");

        toggle.setOnAction(e -> {
            internetKilled = toggle.isSelected();
            if (internetKilled) {
                toggle.setText("RESTORE");
                toggle.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                statusLabel.setText("● OFFLINE");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                server.broadcast(new CommandPacket(CommandPacket.Type.INTERNET, "ADMIN", "DISABLE"));
            } else {
                toggle.setText("KILL");
                toggle.setStyle(
                        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                statusLabel.setText("● ONLINE");
                statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                server.broadcast(new CommandPacket(CommandPacket.Type.INTERNET, "ADMIN", "ENABLE"));
            }
        });

        toggleBox.getChildren().addAll(statusLabel, toggle);
        return toggleBox;
    }

    private static boolean screenSharing = false;
    private static java.util.concurrent.ScheduledExecutorService screenScheduler;

    private static HBox createScreenShareToggle() {
        HBox toggleBox = new HBox(10);
        toggleBox.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("● OFF");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-weight: bold;");

        ToggleButton toggle = new ToggleButton("START");
        toggle.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");

        toggle.setOnAction(e -> {
            screenSharing = toggle.isSelected();
            if (screenSharing) {
                toggle.setText("STOP");
                toggle.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                statusLabel.setText("● LIVE");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                startAdminScreenShare();
            } else {
                toggle.setText("START");
                toggle.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15;");
                statusLabel.setText("● OFF");
                statusLabel.setStyle("-fx-text-fill: #888; -fx-font-weight: bold;");
                stopAdminScreenShare();
            }
        });

        toggleBox.getChildren().addAll(statusLabel, toggle);
        return toggleBox;
    }

    private static void startAdminScreenShare() {
        screenScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        // 60fps = 16ms interval for smoother streaming
        screenScheduler.scheduleAtFixedRate(() -> {
            if (screenSharing) {
                String base64 = ScreenCapture.captureForStreaming(); // 60% res, 80% quality
                if (base64 != null) {
                    server.broadcast(new CommandPacket(CommandPacket.Type.ADMIN_SCREEN, "ADMIN", base64));
                }
            }
        }, 0, 16, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static void stopAdminScreenShare() {
        if (screenScheduler != null) {
            screenScheduler.shutdown();
            screenScheduler = null;
        }
    }

    private static void addStudentCard(String name, Image screenshot) {
        VBox card = new VBox(8);
        card.setPrefWidth(220);
        card.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 15; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5); -fx-padding: 10;");
        card.setAlignment(Pos.CENTER);

        // Screenshot - larger size for better visibility
        ImageView imgView = new ImageView();
        imgView.setFitWidth(280);
        imgView.setFitHeight(180);
        imgView.setPreserveRatio(false);
        imgView.setStyle("-fx-background-color: #333; -fx-cursor: hand;");

        // Click to open fullscreen view
        imgView.setOnMouseClicked(e -> openFullScreenView(name, imgView.getImage()));

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // Status indicator
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER);
        Circle statusDot = new Circle(5, Color.web("#2ecc71"));
        Label statusText = new Label("Connected");
        statusText.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        statusBox.getChildren().addAll(statusDot, statusText);

        // Individual controls
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER);

        Button lockBtn = new Button("🔒");
        lockBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        lockBtn.setOnAction(e -> server.sendToClient(name, new CommandPacket(CommandPacket.Type.LOCK, "ADMIN", "{}")));

        Button msgBtn = new Button("💬");
        msgBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        msgBtn.setOnAction(e -> {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Message to " + name);
            dialog.setHeaderText("Send private message to " + name);
            dialog.setContentText("Message:");
            dialog.showAndWait().ifPresent(msg -> {
                server.sendToClient(name, new CommandPacket(CommandPacket.Type.MSG, "ADMIN", msg));
                chatArea.appendText("[TO " + name + "]: " + msg + "\n");
            });
        });

        Button cmdBtn = new Button("⌨");
        cmdBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-background-radius: 5;");
        cmdBtn.setOnAction(e -> {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Execute on " + name);
            dialog.setHeaderText("Run command on " + name + "'s PC");
            dialog.setContentText("Command:");
            dialog.showAndWait().ifPresent(cmd -> {
                server.sendToClient(name, new CommandPacket(CommandPacket.Type.SHELL, "ADMIN", cmd));
                chatArea.appendText("[CMD->" + name + "]: " + cmd + "\n");
            });
        });

        controls.getChildren().addAll(lockBtn, msgBtn, cmdBtn);

        card.getChildren().addAll(imgView, nameLabel, statusBox, controls);
        thumbnailGrid.getChildren().add(card);
        studentCards.put(name, card);
        studentImages.put(name, imgView);
    }

    private static void updateStudentScreen(String clientName, String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            Image image = new Image(new ByteArrayInputStream(imageBytes));

            if (studentImages.containsKey(clientName)) {
                // Update existing card
                studentImages.get(clientName).setImage(image);
            } else {
                // Create new card for this client
                addStudentCard(clientName, image);
            }
        } catch (Exception e) {
            System.err.println("Failed to decode screen from " + clientName + ": " + e.getMessage());
        }
    }

    private static void sendFilesToStudents(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Send");
        java.util.List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                try {
                    // Read file and encode as Base64
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                    // Format: filename|base64data
                    String payload = file.getName() + "|" + base64Data;

                    chatArea.appendText(
                            "[FILE]: Sending " + file.getName() + " (" + formatSize(fileBytes.length) + ")...\n");
                    server.broadcast(new CommandPacket(CommandPacket.Type.FILE_DATA, "ADMIN", payload));
                    chatArea.appendText("[FILE]: ✓ Sent " + file.getName() + "\n");

                } catch (Exception e) {
                    chatArea.appendText("[ERROR]: Failed to send " + file.getName() + ": " + e.getMessage() + "\n");
                }
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static void removeStudentCard(String clientName) {
        VBox card = studentCards.remove(clientName);
        if (card != null) {
            thumbnailGrid.getChildren().remove(card);
        }
        studentImages.remove(clientName);
        if (chatArea != null) {
            chatArea.appendText("[SYSTEM]: " + clientName + " disconnected\n");
        }
    }

    private static void openFullScreenView(String studentName, Image screenshot) {
        if (screenshot == null)
            return;

        Stage fullscreenStage = new Stage();
        fullscreenStage.setTitle("Viewing: " + studentName);

        ImageView fullView = new ImageView(screenshot);
        fullView.setPreserveRatio(true);
        fullView.fitWidthProperty().bind(fullscreenStage.widthProperty());
        fullView.fitHeightProperty().bind(fullscreenStage.heightProperty().subtract(50));

        Label nameLabel = new Label("📺 " + studentName + " - Click anywhere or press ESC to close");
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 10;");

        VBox root = new VBox(nameLabel, fullView);
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 1200, 800);
        scene.setOnMouseClicked(e -> fullscreenStage.close());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                fullscreenStage.close();
            }
        });

        fullscreenStage.setScene(scene);
        fullscreenStage.show();

        // Keep updating with live screen data
        final ImageView liveView = fullView;
        Thread updateThread = new Thread(() -> {
            while (fullscreenStage.isShowing()) {
                try {
                    Thread.sleep(100);
                    ImageView studentImgView = studentImages.get(studentName);
                    if (studentImgView != null && studentImgView.getImage() != null) {
                        Image latest = studentImgView.getImage();
                        Platform.runLater(() -> liveView.setImage(latest));
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
}
