package com.ghost.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance screen capture utility with mouse cursor rendering
 * and async double-buffering for smooth streaming.
 */
public class ScreenCapture {
    private static Robot robot;
    private static Rectangle screenRect;
    private static BufferedImage reusableBuffer;
    private static Graphics2D reusableGraphics;

    // Async double-buffer: capture happens in background, latest frame always ready
    private static final AtomicReference<String> latestFrame = new AtomicReference<>(null);
    private static volatile boolean asyncRunning = false;

    // Quality presets
    public static final double QUALITY_LOW = 0.3;
    public static final double QUALITY_MEDIUM = 0.5;
    public static final double QUALITY_HIGH = 0.7;
    public static final double QUALITY_ULTRA = 0.9;

    // Mouse cursor image (drawn manually since Robot doesn't capture it)
    private static final int[][] CURSOR_SHAPE = {
            { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 1, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 1, 2, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 1, 2, 2, 2, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 1, 2, 2, 2, 2, 1, 0, 0, 0, 0, 0, 0 },
            { 1, 2, 2, 2, 2, 2, 1, 0, 0, 0, 0, 0 },
            { 1, 2, 2, 2, 2, 2, 2, 1, 0, 0, 0, 0 },
            { 1, 2, 2, 2, 2, 2, 2, 2, 1, 0, 0, 0 },
            { 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0, 0 },
            { 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0 },
            { 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1 },
            { 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1 },
            { 1, 2, 2, 2, 1, 2, 2, 1, 0, 0, 0, 0 },
            { 1, 2, 2, 1, 0, 1, 2, 2, 1, 0, 0, 0 },
            { 1, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 0 },
            { 1, 1, 0, 0, 0, 0, 1, 2, 2, 1, 0, 0 },
            { 1, 0, 0, 0, 0, 0, 1, 2, 2, 1, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0 },
    };

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    /**
     * Draw the mouse cursor onto the captured image.
     */
    private static void drawMouseCursor(Graphics2D g, double scaleX, double scaleY) {
        try {
            Point mousePos = MouseInfo.getPointerInfo().getLocation();
            int mx = (int) (mousePos.x * scaleX);
            int my = (int) (mousePos.y * scaleY);

            for (int row = 0; row < CURSOR_SHAPE.length; row++) {
                for (int col = 0; col < CURSOR_SHAPE[row].length; col++) {
                    int val = CURSOR_SHAPE[row][col];
                    if (val == 1) {
                        g.setColor(Color.BLACK);
                        g.fillRect(mx + col, my + row, 1, 1);
                    } else if (val == 2) {
                        g.setColor(Color.WHITE);
                        g.fillRect(mx + col, my + row, 1, 1);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cursor drawing errors (e.g. no mouse info available)
        }
    }

    /**
     * Captures the screen with configurable resolution and JPEG quality.
     * Now includes the mouse cursor in the capture.
     */
    public static String captureAsBase64(double resolutionScale, float jpegQuality) {
        try {
            BufferedImage capture = robot.createScreenCapture(screenRect);

            int newWidth = (int) (capture.getWidth() * resolutionScale);
            int newHeight = (int) (capture.getHeight() * resolutionScale);

            if (reusableBuffer == null || reusableBuffer.getWidth() != newWidth
                    || reusableBuffer.getHeight() != newHeight) {
                if (reusableGraphics != null)
                    reusableGraphics.dispose();
                reusableBuffer = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                reusableGraphics = reusableBuffer.createGraphics();
                reusableGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                reusableGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            }

            // Scale into reusable buffer
            reusableGraphics.drawImage(capture, 0, 0, newWidth, newHeight, null);

            // Draw mouse cursor on top
            drawMouseCursor(reusableGraphics, resolutionScale, resolutionScale);

            // Encode with quality control
            ByteArrayOutputStream baos = new ByteArrayOutputStream(50000);

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);

                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);
                writer.write(null, new IIOImage(reusableBuffer, null, null), param);
                writer.dispose();
                ios.close();
            } else {
                ImageIO.write(reusableBuffer, "jpg", baos);
            }

            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String captureAsBase64(double resolutionScale) {
        return captureAsBase64(resolutionScale, 0.85f);
    }

    public static String captureHighQuality() {
        return captureAsBase64(1.0, 0.95f);
    }

    /**
     * Captures optimized for LAN streaming (100% resolution, 80% quality)
     */
    public static String captureForStreaming() {
        // Optimized for 40fps streaming (reduced from 0.80 to 0.75 for better
        // performance)
        return captureAsBase64(1.0, 0.75f);
    }

    /**
     * Start async capture loop. Captures frames in the background continuously.
     * Call getLatestFrame() to get the most recent captured frame.
     * This provides smooth streaming by decoupling capture from send.
     */
    public static void startAsyncCapture() {
        if (asyncRunning)
            return;
        asyncRunning = true;

        Thread captureThread = new Thread(() -> {
            while (asyncRunning) {
                try {
                    String frame = captureForStreaming();
                    if (frame != null) {
                        latestFrame.set(frame);
                    }
                    // Target 40fps (25ms per frame) for smoother, stable streaming
                    // Reduced from 60fps to lower CPU usage and allow side tasks
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // Keep running on errors
                }
            }
        }, "AsyncScreenCapture");
        captureThread.setDaemon(true);
        // Set lower priority to leave CPU for user's side tasks
        captureThread.setPriority(Thread.NORM_PRIORITY - 1);
        captureThread.start();
    }

    /**
     * Stop async capture loop.
     */
    public static void stopAsyncCapture() {
        asyncRunning = false;
        latestFrame.set(null);
    }

    /**
     * Get the latest captured frame (from async capture).
     * Returns null if no frame is captured yet.
     */
    public static String getLatestFrame() {
        return latestFrame.get();
    }

    /**
     * Decodes Base64 to BufferedImage for display
     */
    public static BufferedImage decodeBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int estimateFrameSize(double resolutionScale, float jpegQuality) {
        int baseSize = screenRect.width * screenRect.height;
        return (int) (baseSize * resolutionScale * resolutionScale * jpegQuality * 0.15);
    }
}
