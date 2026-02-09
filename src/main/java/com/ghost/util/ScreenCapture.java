package com.ghost.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import java.util.Base64;
import java.util.Iterator;

/**
 * High-performance screen capture utility with quality control
 */
public class ScreenCapture {
    private static Robot robot;
    private static Rectangle screenRect;
    private static BufferedImage reusableBuffer;
    private static Graphics2D reusableGraphics;

    // Quality presets
    public static final double QUALITY_LOW = 0.3; // Fast, small files
    public static final double QUALITY_MEDIUM = 0.5; // Balanced
    public static final double QUALITY_HIGH = 0.7; // Good quality
    public static final double QUALITY_ULTRA = 0.9; // Best quality

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(0); // No delay for faster captures
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    /**
     * Captures the screen with configurable resolution scale
     * 
     * @param resolutionScale Scale factor (0.1 to 1.0) for resolution
     * @return Base64 encoded JPEG string
     */
    public static String captureAsBase64(double resolutionScale) {
        return captureAsBase64(resolutionScale, 0.85f); // Default 85% JPEG quality
    }

    /**
     * Captures the screen with configurable resolution and JPEG quality
     * 
     * @param resolutionScale Scale factor (0.1 to 1.0) for resolution
     * @param jpegQuality     JPEG compression quality (0.0 to 1.0)
     * @return Base64 encoded JPEG string
     */
    public static String captureAsBase64(double resolutionScale, float jpegQuality) {
        try {
            // Capture screen
            BufferedImage capture = robot.createScreenCapture(screenRect);

            // Calculate target dimensions
            int newWidth = (int) (capture.getWidth() * resolutionScale);
            int newHeight = (int) (capture.getHeight() * resolutionScale);

            // Reuse buffer if same size, otherwise create new
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

            // Encode with quality control
            ByteArrayOutputStream baos = new ByteArrayOutputStream(50000); // Pre-allocate

            // Use ImageWriter for quality control
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
                // Fallback
                ImageIO.write(reusableBuffer, "jpg", baos);
            }

            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Captures at maximum quality (full resolution, high JPEG quality)
     */
    public static String captureHighQuality() {
        return captureAsBase64(1.0, 0.95f);
    }

    /**
     * Captures optimized for streaming (balanced quality/size)
     */
    public static String captureForStreaming() {
        return captureAsBase64(0.6, 0.80f);
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

    /**
     * Returns estimated bytes per frame at given settings
     */
    public static int estimateFrameSize(double resolutionScale, float jpegQuality) {
        // Rough estimate: base resolution * scale^2 * quality factor
        int baseSize = screenRect.width * screenRect.height;
        return (int) (baseSize * resolutionScale * resolutionScale * jpegQuality * 0.15);
    }
}
