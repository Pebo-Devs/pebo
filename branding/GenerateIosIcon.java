import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Renders the iOS app-icon PNG from the Pebo brand mark.
 *
 * iOS app icons must be a full-bleed, fully opaque square (the system applies its own rounded mask and
 * rejects alpha). This composites {@code pebo-logo-1024.png} (the transparent brand tile) onto a
 * full-bleed background painted with the SAME brand gradient as the tile, so the rounded corners blend
 * seamlessly into a solid icon.
 *
 * Usage (from repo root):
 *   javac -d branding/out branding/GenerateIosIcon.java
 *   java  -cp branding/out GenerateIosIcon branding/pebo-logo-1024.png \
 *         iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
 *
 * Pure JDK — no external dependencies.
 */
public final class GenerateIosIcon {
    // Same brand gradient + geometry as GenerateLogo / pebo-logo.svg (48-unit viewport).
    private static final Color GRADIENT_TOP = new Color(0x5B, 0x8C, 0xFF);
    private static final Color GRADIENT_BOTTOM = new Color(0x7C, 0x5C, 0xFF);
    private static final double VP = 48.0;

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

        double s = n / VP;
        g.setPaint(new GradientPaint(
            (float) (2 * s), (float) (2 * s), GRADIENT_TOP,
            (float) (46 * s), (float) (46 * s), GRADIENT_BOTTOM
        ));
        g.fillRect(0, 0, n, n);
        g.drawImage(logo, 0, 0, null);
        g.dispose();

        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("WROTE " + out + " (" + n + "x" + n + ", opaque)");
    }
}
