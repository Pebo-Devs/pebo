import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Renders the iOS app-icon PNG from the Pebo brand mark.
 *
 * iOS app icons must be a fully opaque square (the system applies its own rounded mask and rejects
 * alpha). This draws the transparent brand logo ({@code pebo-logo-1024.png}) centered on an opaque
 * white card with a small margin, so the document tile + dog-ear + "P" read exactly like the in-app
 * logo.
 *
 * Usage (from repo root):
 *   javac -d branding/out branding/GenerateIosIcon.java
 *   java  -cp branding/out GenerateIosIcon branding/pebo-logo-1024.png \
 *         iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
 *
 * Pure JDK — no external dependencies.
 */
public final class GenerateIosIcon {
    /** Fraction of the icon left as margin on each side around the logo. */
    private static final double MARGIN = 0.15;

    public static void main(String[] args) throws Exception {
        File src = new File(args[0]);
        File out = new File(args[1]);

        BufferedImage logo = ImageIO.read(src);
        int n = logo.getWidth();

        // TYPE_INT_RGB => no alpha channel (App Store / iOS require an opaque icon).
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, n, n);

        int pad = (int) Math.round(n * MARGIN);
        int size = n - 2 * pad;
        g.drawImage(logo, pad, pad, size, size, null);
        g.dispose();

        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("WROTE " + out + " (" + n + "x" + n + ", opaque)");
    }
}
