package com.ghost.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

public class IconUtil {
    public static void setIcon(Stage stage) {
        try {
            stage.getIcons().add(new Image(IconUtil.class.getResourceAsStream("/ghost_icon.png")));
        } catch (Exception e) {
            System.err.println("Could not load icon: " + e.getMessage());
        }
    }
}
