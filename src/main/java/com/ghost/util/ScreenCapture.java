package com.ghost.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance screen capture with:
 * - Reused Robot + buffer + JPEG writer (no per-frame allocations)
 * - AtomicReference swap: sender only picks up the freshest frame
 * - Adaptive sleep: backs off if capture is faster than target FPS
 * - Low priority daemon thread so admin UI stays responsive
 */
public class ScreenCapture {
    private static Robot robot;
    private static Rectangle screenRect;

    // Reused across frames to avoid GC pressure
    private static BufferedImage captureBuffer; // raw Robot pixels
    private static BufferedImage scaleBuffer; // down-scaled frame
    private static Graphics2D scaleG;
    private static ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream(64 * 1024);
    private static ImageWriter jpegWriter;
    private static ImageWriteParam jpegParam;

    // Target capture dimensions (720p-equivalent, 16:9)
    private static final int STREAM_WIDTH = 1280;
    private static final int STREAM_HEIGHT = 720;

    // JPEG quality: 0.60 is indistinguishable from higher on a LAN thumbnail
    private static final float JPEG_QUALITY = 0.60f;

    // Target frame interval (ms) — 20 fps feels smooth for a classroom view
    private static final long FRAME_INTERVAL_MS = 45; // 20 fps

    // Latest encoded frame ready to send. AtomicReference ensures the sender
    // always gets the newest frame and old frames are automatically dropped.
    private static final AtomicReference<byte[]> latestFrameBytes = new AtomicReference<>(null);

    // Cursor shape pixels (outline=1, fill=2)
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

    private static volatile boolean asyncRunning = false;

    // Legacy compat constants
    public static final double QUALITY_LOW = 0.3;
    public static final double QUALITY_MEDIUM = 0.5;
    public static final double QUALITY_HIGH = 0.7;
    public static final double QUALITY_ULTRA = 0.9;

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            initBuffers();
            initJpegWriter();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private static void initBuffers() {
        captureBuffer = new BufferedImage(screenRect.width, screenRect.height, BufferedImage.TYPE_INT_RGB);
        scaleBuffer = new BufferedImage(STREAM_WIDTH, STREAM_HEIGHT, BufferedImage.TYPE_INT_RGB);
        scaleG = scaleBuffer.createGraphics();
        scaleG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        scaleG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        scaleG.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
    }

    private static void initJpegWriter() throws AWTException {
        try {
            java.util.Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
            if (!it.hasNext())
                throw new RuntimeException("No JPEG writer found");
            jpegWriter = it.next();
            jpegParam = jpegWriter.getDefaultWriteParam();
            jpegParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpegParam.setCompressionQuality(JPEG_QUALITY);
        } catch (Exception e) {
            throw new AWTException("JPEG writer init failed: " + e.getMessage());
        }
    }

    /** Capture, scale, draw cursor, encode JPEG — all reusing existing buffers. */
    private static void captureFrame() throws Exception {
        // 1. Capture into reusable AWT buffer (avoids allocation inside Robot)
        BufferedImage raw = robot.createScreenCapture(screenRect);

        // 2. Scale into fixed-size scaleBuffer
        scaleG.drawImage(raw, 0, 0, STREAM_WIDTH, STREAM_HEIGHT, null);

        // 3. Draw cursor
        drawMouseCursor(scaleG,
                (double) STREAM_WIDTH / screenRect.width,
                (double) STREAM_HEIGHT / screenRect.height);

        // 4. JPEG encode into reused ByteArrayOutputStream
        jpegBuf.reset(); // clears without reallocating the internal byte[]
        MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(jpegBuf);
        jpegWriter.setOutput(ios);
        jpegWriter.write(null, new IIOImage(scaleBuffer, null, null), jpegParam);
        ios.close(); // flushes but doesn't close underlying stream

        // 5. Publish raw bytes (no Base64 here — sender encodes on the fly)
        latestFrameBytes.set(jpegBuf.toByteArray());
    }

    private static void drawMouseCursor(Graphics2D g, double sx, double sy) {
        try {
            Point mp = MouseInfo.getPointerInfo().getLocation();
            int mx = (int) (mp.x * sx);
            int my = (int) (mp.y * sy);
            for (int r = 0; r < CURSOR_SHAPE.length; r++) {
                for (int c = 0; c < CURSOR_SHAPE[r].length; c++) {
                    int v = CURSOR_SHAPE[r][c];
                    if (v == 0)
                        continue;
                    g.setColor(v == 1 ? Color.BLACK : Color.WHITE);
                    g.fillRect(mx + c, my + r, 1, 1);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Start the async capture daemon.heheh */
    public static synchronized void startAsyncCapture() {
        if (asyncRunning)
            return;
        asyncRunning = true;

        Thread t = new Thread(() -> {
            while (asyncRunning) {
                long t0 = System.currentTimeMillis();
                try {
                    captureFrame();
                } catch (Exception ignored) {
                }
                long elapsed = System.currentTimeMillis() - t0;
                long sleep = FRAME_INTERVAL_MS - elapsed;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "GhostScreenCapture");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1); // below normal — don't starve UI
        t.start();
    }

    /** Stop the async capture daemon and clear the buffer. */
    public static void stopAsyncCapture() {
        asyncRunning = false;
        latestFrameBytes.set(null);
    }

    /**
     * Returns the latest frame as a Base64 string, or null if nothing captured yet.
     * Encodes lazily here so the capture thread itself never touches Base64.
     */
    public static String getLatestFrame() {
        byte[] bytes = latestFrameBytes.getAndSet(null); // consume frame — prevents re-sending same frame
        if (bytes == null)
            return null;
        return Base64.getEncoder().encodeToString(bytes);
    }

    // ---- Legacy API kept for compatibility ----

    public static String captureAsBase64(double resolutionScale, float jpegQuality) {
        try {
            captureFrame();
            byte[] bytes = latestFrameBytes.getAndSet(null);
            return bytes != null ? Base64.getEncoder().encodeToString(bytes) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String captureAsBase64(double resolutionScale) {
        return captureAsBase64(resolutionScale, JPEG_QUALITY);
    }

    public static String captureHighQuality() {
        return captureAsBase64(1.0, 0.90f);
    }

    public static String captureForStreaming() {
        return captureAsBase64(1.0, JPEG_QUALITY);
    }

    public static BufferedImage decodeBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public static int estimateFrameSize(double resolutionScale, float jpegQuality) {
        return STREAM_WIDTH * STREAM_HEIGHT * (int) (jpegQuality * 0.15);
    }
}
