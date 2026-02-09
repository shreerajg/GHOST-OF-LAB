package com.ghost.ui;

import com.ghost.database.User;
import com.ghost.net.CommandPacket;
import com.ghost.net.GhostClient;
import com.ghost.util.Config;
import com.ghost.util.PythonBridge;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

public class StudentDashboard {
    private static GhostClient client;
    private static StackPane root;
    private static VBox lockOverlay;
    private static ImageView streamView;
    private static TextArea chatArea;
    private static VBox chatPanel;
    private static VBox aiPanel;
    private static VBox settingsPanel;
    private static String currentTheme = "cyberpunk";
    private static String downloadFolder = System.getProperty("user.home") + "/Downloads/Ghost";
    private static Label notificationLabel;
    private static Circle statusDot;
    private static Label statusLabel;
    private static String currentUsername;

    public static void show(Stage stage, User user) {
        currentUsername = user.getUsername();
        // Create downloads folder
        new File(downloadFolder).mkdirs();

        if (client == null) {
            client = new GhostClient(Config.ADMIN_IP);
            client.setListener(packet -> handleCommand(packet));
            client.connect();
        }

        root = new StackPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f0f1f, #1a1a3e);");

        // ========== MAIN CONTENT ==========
        BorderPane mainContent = new BorderPane();
        mainContent.setPadding(new Insets(20));

        // Header
        HBox header = createHeader(user, stage);
        mainContent.setTop(header);

        // Center - Stream Viewer
        VBox streamContainer = new VBox(15);
        streamContainer.setAlignment(Pos.CENTER);
        streamContainer.setPadding(new Insets(10));

        Label streamTitle = new Label("📺 ADMIN LIVE STREAM (Click to Fullscreen)");
        streamTitle.setStyle("-fx-font-size: 16px; -fx-text-fill: #00ffaa; -fx-font-weight: bold;");

        // Stream view - larger size
        StackPane streamBox = new StackPane();
        streamBox.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-background-radius: 15; -fx-cursor: hand;");
        streamBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        streamBox.setEffect(new DropShadow(20, Color.rgb(0, 255, 170, 0.3)));

        streamView = new ImageView();
        streamView.setPreserveRatio(true);
        streamView.fitWidthProperty().bind(streamBox.widthProperty().subtract(20));
        streamView.fitHeightProperty().bind(streamBox.heightProperty().subtract(20));

        Label waitingLabel = new Label("⏳ Waiting for Admin to start screen share...");
        waitingLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 18px;");
        waitingLabel.setId("waitingLabel");

        // Click to fullscreen
        streamBox.setOnMouseClicked(e -> openFullScreenStream());

        streamBox.getChildren().addAll(waitingLabel, streamView);
        streamContainer.getChildren().addAll(streamTitle, streamBox);
        VBox.setVgrow(streamContainer, Priority.ALWAYS);
        VBox.setVgrow(streamBox, Priority.ALWAYS);
        mainContent.setCenter(streamContainer);

        // Bottom toolbar
        HBox toolbar = createToolbar(stage);
        mainContent.setBottom(toolbar);

        root.getChildren().add(mainContent);

        // ========== NOTIFICATION POPUP ==========
        notificationLabel = new Label();
        notificationLabel.setStyle("-fx-background-color: rgba(0,255,170,0.9); -fx-text-fill: #1a1a2e; " +
                "-fx-padding: 15 25; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 14px;");
        notificationLabel.setVisible(false);
        StackPane.setAlignment(notificationLabel, Pos.TOP_CENTER);
        StackPane.setMargin(notificationLabel, new Insets(20, 0, 0, 0));
        root.getChildren().add(notificationLabel);

        // ========== LOCK OVERLAY ==========
        lockOverlay = new VBox(20);
        lockOverlay.setAlignment(Pos.CENTER);
        lockOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.95);");
        lockOverlay.setVisible(false);

        Label lockIcon = new Label("🔒");
        lockIcon.setStyle("-fx-font-size: 80px;");

        Label lockText = new Label("SYSTEM LOCKED");
        lockText.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 48px; -fx-font-weight: bold;");

        Label lockSubtext = new Label("Pay attention to your instructor");
        lockSubtext.setStyle("-fx-text-fill: #888; -fx-font-size: 18px;");

        lockOverlay.getChildren().addAll(lockIcon, lockText, lockSubtext);
        root.getChildren().add(lockOverlay);

        // ========== CHAT PANEL (Slide-in) ==========
        chatPanel = createChatPanel();
        chatPanel.setVisible(false);
        StackPane.setAlignment(chatPanel, Pos.CENTER_RIGHT);
        root.getChildren().add(chatPanel);

        // ========== AI PANEL (Slide-in) ==========
        aiPanel = createAIPanel();
        aiPanel.setVisible(false);
        StackPane.setAlignment(aiPanel, Pos.CENTER_LEFT);
        root.getChildren().add(aiPanel);

        // ========== SETTINGS PANEL ==========
        settingsPanel = createSettingsPanel(stage);
        settingsPanel.setVisible(false);
        StackPane.setAlignment(settingsPanel, Pos.CENTER);
        root.getChildren().add(settingsPanel);

        Scene scene = new Scene(root, 1100, 750);
        applyTheme(scene, currentTheme);
        stage.setScene(scene);
        stage.setTitle("Ghost - Student Interface");
        stage.setOnCloseRequest(e -> System.exit(0));
    }

    private static HBox createHeader(User user, Stage stage) {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 15;");

        // Logo
        Label logo = new Label("👻 GHOST");
        logo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #00ffaa;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status (dynamic)
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);
        statusDot = new Circle(6, Color.web("#f39c12")); // Start as searching
        statusLabel = new Label("Searching for Admin...");
        statusLabel.setStyle("-fx-text-fill: #888;");
        statusBox.getChildren().addAll(statusDot, statusLabel);

        // User info
        Label userName = new Label("👤 " + user.getUsername());
        userName.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // Theme selector with better styling
        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("🌃 Cyberpunk", "🌊 Ocean", "🌙 Midnight");
        themeBox.setValue("🌃 Cyberpunk");
        themeBox.setStyle("-fx-background-color: #2a2a4e; -fx-font-size: 12px;");
        // Apply cell factory for better visibility
        themeBox.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: white; -fx-background-color: #2a2a4e; -fx-padding: 8;");
                }
            }
        });
        themeBox.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                setStyle("-fx-text-fill: white;");
            }
        });
        themeBox.setOnAction(e -> {
            String val = themeBox.getValue();
            if (val.contains("Cyberpunk"))
                currentTheme = "cyberpunk";
            else if (val.contains("Ocean"))
                currentTheme = "ocean";
            else if (val.contains("Midnight"))
                currentTheme = "midnight";
            applyTheme(header.getScene(), currentTheme);
        });

        header.getChildren().addAll(logo, spacer, statusBox, userName, themeBox);
        return header;
    }

    private static HBox createToolbar(Stage stage) {
        HBox toolbar = new HBox(15);
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setPadding(new Insets(15));
        toolbar.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-background-radius: 15 15 0 0;");

        Button chatBtn = createToolbarButton("💬 CHAT", "#3498db", () -> {
            chatPanel.setVisible(!chatPanel.isVisible());
            if (aiPanel.isVisible())
                aiPanel.setVisible(false);
            if (settingsPanel.isVisible())
                settingsPanel.setVisible(false);
        });

        Button aiBtn = createToolbarButton("🤖 GHOST AI", "#9b59b6", () -> {
            aiPanel.setVisible(!aiPanel.isVisible());
            if (chatPanel.isVisible())
                chatPanel.setVisible(false);
            if (settingsPanel.isVisible())
                settingsPanel.setVisible(false);
        });

        Button filesBtn = createToolbarButton("📁 FILES", "#e67e22", () -> {
            // Open downloads folder
            try {
                java.awt.Desktop.getDesktop().open(new File(downloadFolder));
            } catch (Exception ex) {
                showNotification("Could not open folder: " + downloadFolder);
            }
        });

        Button settingsBtn = createToolbarButton("⚙️ SETTINGS", "#666", () -> {
            settingsPanel.setVisible(!settingsPanel.isVisible());
            if (chatPanel.isVisible())
                chatPanel.setVisible(false);
            if (aiPanel.isVisible())
                aiPanel.setVisible(false);
        });

        toolbar.getChildren().addAll(chatBtn, aiBtn, filesBtn, settingsBtn);
        return toolbar;
    }

    private static Button createToolbarButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 12 25; -fx-cursor: hand;");
        btn.setOnAction(e -> action.run());
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    private static VBox createChatPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(300);
        panel.setMaxWidth(300);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(20,20,40,0.95); -fx-background-radius: 15 0 0 15;");

        Label title = new Label("💬 LAN CHAT");
        title.setStyle("-fx-font-size: 16px; -fx-text-fill: #00ffaa; -fx-font-weight: bold;");

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-size: 16px;");
        closeBtn.setOnAction(e -> panel.setVisible(false));

        HBox header = new HBox();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, closeBtn);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-control-inner-background: #1a1a2e; -fx-text-fill: #ccc;");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        HBox inputBox = new HBox(10);
        TextField input = new TextField();
        input.setPromptText("Type message...");
        input.setStyle("-fx-background-color: #2a2a4e; -fx-text-fill: white; -fx-prompt-text-fill: #666;");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button sendBtn = new Button("→");
        sendBtn.setStyle("-fx-background-color: #00ffaa; -fx-text-fill: #1a1a2e; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> {
            String msg = input.getText();
            if (!msg.isEmpty()) {
                chatArea.appendText("[YOU]: " + msg + "\n");
                client.sendMessage(new CommandPacket(CommandPacket.Type.MSG, currentUsername, msg));
                input.clear();
            }
        });

        inputBox.getChildren().addAll(input, sendBtn);
        panel.getChildren().addAll(header, chatArea, inputBox);
        return panel;
    }

    private static VBox createAIPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(350);
        panel.setMaxWidth(350);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(20,20,40,0.95); -fx-background-radius: 0 15 15 0;");

        Label title = new Label("🤖 GHOST AI ASSISTANT");
        title.setStyle("-fx-font-size: 14px; -fx-text-fill: #9b59b6; -fx-font-weight: bold;");

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888;");
        closeBtn.setOnAction(e -> panel.setVisible(false));

        HBox header = new HBox();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, closeBtn);

        TextArea aiChat = new TextArea();
        aiChat.setEditable(false);
        aiChat.setWrapText(true);
        aiChat.setStyle("-fx-control-inner-background: #1a1a2e; -fx-text-fill: #ccc;");
        aiChat.appendText("👻 Hello! I'm Ghost AI. Ask me anything about your code or lessons.\n\n");
        VBox.setVgrow(aiChat, Priority.ALWAYS);

        HBox inputBox = new HBox(10);
        TextField input = new TextField();
        input.setPromptText("Ask Ghost AI...");
        input.setStyle("-fx-background-color: #2a2a4e; -fx-text-fill: white; -fx-prompt-text-fill: #666;");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button askBtn = new Button("ASK");
        askBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        askBtn.setOnAction(e -> {
            String q = input.getText();
            if (!q.isEmpty()) {
                aiChat.appendText("[YOU]: " + q + "\n");
                aiChat.appendText("[AI]: Thinking...\n");
                PythonBridge.askAI(q, response -> {
                    Platform.runLater(() -> {
                        // Replace "Thinking..." with actual response
                        String text = aiChat.getText();
                        text = text.replace("[AI]: Thinking...\n", "[AI]: " + response + "\n\n");
                        aiChat.setText(text);
                        aiChat.setScrollTop(Double.MAX_VALUE);
                    });
                });
                input.clear();
            }
        });

        inputBox.getChildren().addAll(input, askBtn);
        panel.getChildren().addAll(header, aiChat, inputBox);
        return panel;
    }

    private static VBox createSettingsPanel(Stage stage) {
        VBox panel = new VBox(20);
        panel.setPrefWidth(400);
        panel.setMaxWidth(400);
        panel.setPadding(new Insets(30));
        panel.setStyle("-fx-background-color: rgba(20,20,40,0.98); -fx-background-radius: 20;");
        panel.setEffect(new DropShadow(30, Color.rgb(0, 0, 0, 0.5)));
        panel.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("⚙️ SETTINGS");
        title.setStyle("-fx-font-size: 20px; -fx-text-fill: #00ffaa; -fx-font-weight: bold;");

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-size: 18px;");
        closeBtn.setOnAction(e -> panel.setVisible(false));

        HBox header = new HBox();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, closeBtn);

        // Download Folder Setting
        VBox downloadSection = new VBox(10);
        Label downloadLabel = new Label("📁 Download Folder");
        downloadLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        Label currentFolder = new Label(downloadFolder);
        currentFolder.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        currentFolder.setWrapText(true);

        Button browseBtn = new Button("Change Folder");
        browseBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Download Folder");
            File dir = dc.showDialog(stage);
            if (dir != null) {
                downloadFolder = dir.getAbsolutePath();
                currentFolder.setText(downloadFolder);
            }
        });
        downloadSection.getChildren().addAll(downloadLabel, currentFolder, browseBtn);

        // Screen Sending Toggle
        VBox screenSection = new VBox(10);
        Label screenLabel = new Label("📺 Allow Screen Capture");
        screenLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        CheckBox screenToggle = new CheckBox("Send my screen to Admin");
        screenToggle.setSelected(true);
        screenToggle.setStyle("-fx-text-fill: #888;");
        screenToggle.setOnAction(e -> {
            client.setScreenSending(screenToggle.isSelected());
        });
        screenSection.getChildren().addAll(screenLabel, screenToggle);

        // Theme Preview
        VBox themeSection = new VBox(10);
        Label themeLabel = new Label("🎨 Theme");
        themeLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        HBox themeBtns = new HBox(10);
        Button cyberpunkBtn = new Button("🌃 Cyberpunk");
        cyberpunkBtn.setStyle("-fx-background-color: #00ffaa; -fx-text-fill: #1a1a2e;");
        cyberpunkBtn.setOnAction(e -> {
            currentTheme = "cyberpunk";
            applyTheme(panel.getScene(), currentTheme);
        });

        Button oceanBtn = new Button("🌊 Ocean");
        oceanBtn.setStyle("-fx-background-color: #1e3799; -fx-text-fill: white;");
        oceanBtn.setOnAction(e -> {
            currentTheme = "ocean";
            applyTheme(panel.getScene(), currentTheme);
        });

        Button midnightBtn = new Button("🌙 Midnight");
        midnightBtn.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: white; -fx-border-color: #444;");
        midnightBtn.setOnAction(e -> {
            currentTheme = "midnight";
            applyTheme(panel.getScene(), currentTheme);
        });

        themeBtns.getChildren().addAll(cyberpunkBtn, oceanBtn, midnightBtn);
        themeSection.getChildren().addAll(themeLabel, themeBtns);

        // About
        Label aboutLabel = new Label("Ghost v2.0 - Classroom Management System");
        aboutLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        panel.getChildren().addAll(header, new Separator(), downloadSection, new Separator(),
                screenSection, new Separator(), themeSection, new Region(), aboutLabel);
        VBox.setVgrow(panel.getChildren().get(panel.getChildren().size() - 2), Priority.ALWAYS);

        return panel;
    }

    private static void showNotification(String message) {
        notificationLabel.setText(message);
        notificationLabel.setVisible(true);

        // Auto-hide after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                Platform.runLater(() -> notificationLabel.setVisible(false));
            } catch (InterruptedException e) {
            }
        }).start();
    }

    private static void applyTheme(Scene scene, String theme) {
        root.getStyleClass().clear();
        switch (theme.toLowerCase()) {
            case "cyberpunk":
                root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f0f1f, #1a1a3e);");
                break;
            case "ocean":
                root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0c2461, #1e3799);");
                break;
            case "midnight":
                root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0a0a0a, #1a1a1a);");
                break;
        }
    }

    private static void handleCommand(CommandPacket packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case LOCK:
                    lockOverlay.setVisible(true);
                    lockOverlay.toFront();
                    showNotification("🔒 Screen locked by Admin");
                    break;
                case UNLOCK:
                    lockOverlay.setVisible(false);
                    showNotification("🔓 Screen unlocked");
                    break;
                case MSG:
                    if (chatArea != null) {
                        String sender = packet.getSender();
                        String prefix = "ADMIN".equalsIgnoreCase(sender) ? "[ADMIN]" : "[" + sender + "]";
                        chatArea.appendText(prefix + ": " + packet.getPayload() + "\n");
                    }
                    // Show notification if chat panel is closed
                    if (!chatPanel.isVisible()) {
                        showNotification("💬 New message from " + packet.getSender());
                    }
                    break;
                case ADMIN_SCREEN:
                    // Update stream view with Admin's screen (must be on FX thread)
                    Platform.runLater(() -> {
                        try {
                            byte[] imageBytes = Base64.getDecoder().decode(packet.getPayload());
                            Image image = new Image(new ByteArrayInputStream(imageBytes));
                            if (streamView != null) {
                                streamView.setImage(image);
                                // Hide waiting label
                                javafx.scene.Node waitLabel = streamView.getParent().lookup("#waitingLabel");
                                if (waitLabel != null)
                                    waitLabel.setVisible(false);
                            }
                        } catch (Exception ex) {
                            System.err.println("Failed to decode admin screen: " + ex.getMessage());
                        }
                    });
                    break;
                case FILE_DATA:
                    // Receive and save file
                    try {
                        String payload = packet.getPayload();
                        // Format: filename|base64data
                        int sep = payload.indexOf('|');
                        String filename = payload.substring(0, sep);
                        String data = payload.substring(sep + 1);
                        byte[] fileBytes = Base64.getDecoder().decode(data);

                        File outFile = new File(downloadFolder, filename);
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            fos.write(fileBytes);
                        }
                        showNotification("📁 File received: " + filename);
                    } catch (Exception e) {
                        System.err.println("Failed to save file: " + e.getMessage());
                    }
                    break;
                case INTERNET:
                    if ("DISABLE".equals(packet.getPayload())) {
                        PythonBridge.execute("kill_net");
                        showNotification("🌐 Internet disabled by Admin");
                    } else {
                        PythonBridge.execute("restore_net");
                        showNotification("🌐 Internet restored");
                    }
                    break;
                case MUTE:
                    PythonBridge.execute("mute");
                    showNotification("🔇 Audio muted by Admin");
                    break;
                case BLOCK_INPUT:
                    if ("BLOCK".equals(packet.getPayload())) {
                        PythonBridge.execute("block_input");
                        showNotification("🖐️ Input blocked by Admin");
                    } else {
                        PythonBridge.execute("unblock_input");
                        showNotification("✋ Input unblocked");
                    }
                    break;
                case NOTIFICATION:
                    String notifPayload = packet.getPayload();
                    if ("CONNECTED".equals(notifPayload)) {
                        // Update status to connected
                        if (statusDot != null)
                            statusDot.setFill(Color.web("#00ff00"));
                        if (statusLabel != null)
                            statusLabel.setText("● Connected to Admin");
                        showNotification("✅ Connected to Admin!");
                    } else if (notifPayload != null && notifPayload.contains("disconnected")) {
                        // Update status to disconnected
                        if (statusDot != null)
                            statusDot.setFill(Color.web("#e74c3c"));
                        if (statusLabel != null)
                            statusLabel.setText("● Disconnected");
                        showNotification(notifPayload);
                        // Clear stream view and show waiting label
                        if (streamView != null)
                            streamView.setImage(null);
                    } else {
                        showNotification(notifPayload);
                    }
                    break;
                default:
                    break;
            }
        });
    }

    private static void openFullScreenStream() {
        if (streamView == null || streamView.getImage() == null) {
            showNotification("⏳ No stream to display yet");
            return;
        }

        Stage fullscreenStage = new Stage();
        fullscreenStage.setTitle("📺 Admin Live Stream - Fullscreen (ESC to close)");

        ImageView fullView = new ImageView(streamView.getImage());
        fullView.setPreserveRatio(true);
        fullView.fitWidthProperty().bind(fullscreenStage.widthProperty());
        fullView.fitHeightProperty().bind(fullscreenStage.heightProperty().subtract(40));

        Label infoLabel = new Label("🔴 LIVE - Press ESC or click to close");
        infoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10;");

        VBox fsRoot = new VBox(infoLabel, fullView);
        fsRoot.setStyle("-fx-background-color: #0f0f1f;");
        fsRoot.setAlignment(Pos.CENTER);

        Scene scene = new Scene(fsRoot, 1280, 720);
        scene.setOnMouseClicked(e -> fullscreenStage.close());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                fullscreenStage.close();
            }
        });

        fullscreenStage.setScene(scene);
        fullscreenStage.setMaximized(true);
        fullscreenStage.show();

        // Live update thread
        Thread updateThread = new Thread(() -> {
            while (fullscreenStage.isShowing()) {
                try {
                    Thread.sleep(50);
                    if (streamView != null && streamView.getImage() != null) {
                        Image latest = streamView.getImage();
                        Platform.runLater(() -> fullView.setImage(latest));
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
