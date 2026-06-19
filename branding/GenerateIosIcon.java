import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Renders the iOS app-icon PNG for Pebo: a full-bleed, opaque version of the brand mark.
 *
 * iOS applies its own rounded mask and rejects alpha, so this draws the brand gradient edge-to-edge
 * (the system rounds the corners), a clean folded "dog-ear" in the top-right, and the white brand
 * glyph centered. Geometry/colours match branding/pebo-logo.svg and the in-app PeboLogo.
 *
 * Usage (from repo root):
 *   javac -d branding/out branding/GenerateIosIcon.java
 *   java  -cp branding/out GenerateIosIcon \
 *         iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
 *
 * Pure JDK — no external dependencies.
 */
public final class GenerateIosIcon {
    private static final Color GRADIENT_TOP = new Color(0x5B, 0x8C, 0xFF);
    private static final Color GRADIENT_BOTTOM = new Color(0x7C, 0x5C, 0xFF);
    private static final double VP = 48.0;   // brand viewport
    private static final double FOLD = 13.0; // dog-ear size
    private static final int SIZE = 1024;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);

        // TYPE_INT_RGB => no alpha channel (App Store / iOS require an opaque icon).
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double s = SIZE / VP;
        g.scale(s, s);

        // Full-bleed brand gradient.
        g.setPaint(new GradientPaint(2f, 2f, GRADIENT_TOP, 46f, 46f, GRADIENT_BOTTOM));
        g.fill(new Rectangle2D.Double(0, 0, VP, VP));

        // Dog-ear: a soft shadow the page casts under the fold, then the folded-back corner.
        Path2D.Double under = new Path2D.Double();
        under.moveTo(VP - FOLD, 0);
        under.lineTo(VP, FOLD);
        under.lineTo(VP - FOLD, FOLD);
        under.closePath();
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(under);

        Path2D.Double corner = new Path2D.Double();
        corner.moveTo(VP - FOLD, 0);
        corner.lineTo(VP, 0);
        corner.lineTo(VP, FOLD);
        corner.closePath();
        g.setColor(new Color(0, 0, 0, 64));
        g.fill(corner);

        // Brand glyph (white): a clean "P" — a stem rising from the baseline into a bowl on its
        // upper-right, drawn as one continuous round-stroked subpath so the stem and bowl fuse into a
        // single solid letter. Geometry is identical to branding/pebo-logo.svg ("M18 37 V11 A7 7 0 1 1
        // 18 25") and the in-app PeboLogo, so the icon never drifts from the brand mark.
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double glyph = new Path2D.Double();
        glyph.moveTo(18, 37);                                              // bottom of the stem
        glyph.lineTo(18, 11);                                              // stem up to the top
        glyph.append(new Arc2D.Double(11, 11, 14, 14, 90, -180, Arc2D.OPEN), true); // right-hand bowl
        g.draw(glyph);

        g.dispose();

        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("WROTE " + out + " (" + SIZE + "x" + SIZE + ", opaque)");
    }
}
