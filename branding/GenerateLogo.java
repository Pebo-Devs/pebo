import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Renders Pebo's brand mark to raster + icon assets from the SAME geometry as
 * {@code composeApp/.../ui/PeboLogo.kt} and {@code branding/pebo-logo.svg}, so the
 * exported files never drift from the in-app vector.
 *
 * Usage (from repo root):
 *   javac -d branding/out branding/GenerateLogo.java
 *   java  -cp branding/out GenerateLogo branding
 *
 * Emits into the given output dir:
 *   pebo-logo-256.png, pebo-logo-512.png, pebo-logo-1024.png   (square, transparent)
 *   pebo-icon.ico                                               (16/32/48/64/128/256, PNG-compressed)
 *   pebo-logo.icns                                              (macOS, PNG-backed entries)
 *
 * Pure JDK (java.awt + javax.imageio) — no external dependencies.
 */
public final class GenerateLogo {

    // Brand colours — fixed so the mark stays consistent across every theme/surface.
    private static final Color GRADIENT_TOP = new Color(0x5B, 0x8C, 0xFF);
    private static final Color GRADIENT_BOTTOM = new Color(0x7C, 0x5C, 0xFF);
    private static final Color MARK = Color.WHITE;
    private static final Color FOLD_SHADE = new Color(0, 0, 0, 51); // 0x33 alpha

    private static final double VP = 48.0;   // viewport
    private static final double R = 11.0;     // tile corner radius
    private static final double FOLD = 13.0;  // dog-ear fold size
    private static final double K = R * 0.5522847498307936; // 90-degree arc control distance

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "branding");
        outDir.mkdirs();

        for (int size : new int[]{256, 512, 1024}) {
            BufferedImage img = render(size);
            File f = new File(outDir, "pebo-logo-" + size + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("WROTE " + f + " (" + size + "x" + size + ")");
        }

        int[] iconSizes = {16, 32, 48, 64, 128, 256};
        byte[][] frames = new byte[iconSizes.length][];
        for (int i = 0; i < iconSizes.length; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(render(iconSizes[i]), "png", baos);
            frames[i] = baos.toByteArray();
        }
        File ico = new File(outDir, "pebo-icon.ico");
        writeIco(ico, iconSizes, frames);
        System.out.println("WROTE " + ico + " (" + iconSizes.length + " frames)");

        // macOS .icns — PNG-backed entries for the standard OSTypes.
        String[] icnsTypes = {"icp4", "icp5", "ic07", "ic08", "ic09", "ic10"};
        int[] icnsSizes = {16, 32, 128, 256, 512, 1024};
        byte[][] icnsFrames = new byte[icnsSizes.length][];
        for (int i = 0; i < icnsSizes.length; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(render(icnsSizes[i]), "png", baos);
            icnsFrames[i] = baos.toByteArray();
        }
        File icns = new File(outDir, "pebo-logo.icns");
        writeIcns(icns, icnsTypes, icnsFrames);
        System.out.println("WROTE " + icns + " (" + icnsTypes.length + " frames)");
    }

    private static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, size, size);
        g.setComposite(AlphaComposite.SrcOver);

        double s = size / VP;
        g.scale(s, s);

        // Tile: rounded square whose top-right corner is sliced into a page fold.
        Path2D.Double tile = new Path2D.Double();
        tile.moveTo(R, 0);
        tile.lineTo(VP - FOLD, 0);            // top edge, stopping before the fold
        tile.lineTo(VP, FOLD);                // diagonal cut of the dog-ear
        tile.lineTo(VP, VP - R);
        tile.curveTo(VP, VP - R + K, VP - R + K, VP, VP - R, VP); // bottom-right corner
        tile.lineTo(R, VP);
        tile.curveTo(R - K, VP, 0, VP - R + K, 0, VP - R);        // bottom-left corner
        tile.lineTo(0, R);
        tile.curveTo(0, R - K, R - K, 0, R, 0);                   // top-left corner
        tile.closePath();
        g.setPaint(new GradientPaint(2f, 2f, GRADIENT_TOP, 46f, 46f, GRADIENT_BOTTOM));
        g.fill(tile);

        // Folded-over flap: a small triangle giving the dog-ear depth.
        Path2D.Double flap = new Path2D.Double();
        flap.moveTo(VP - FOLD, 0);
        flap.lineTo(VP, FOLD);
        flap.lineTo(VP - FOLD, FOLD);
        flap.closePath();
        g.setColor(FOLD_SHADE);
        g.fill(flap);

        // The "P": a stem rising from the baseline into a bowl on its upper-right, drawn as one
        // continuous round-stroked subpath so the stem and bowl fuse into a single solid letter.
        // Matches branding/pebo-logo.svg ("M18 37 V11 A7 7 0 1 1 18 25") and the in-app PeboLogo.
        g.setColor(MARK);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double glyph = new Path2D.Double();
        glyph.moveTo(18, 37);                                              // bottom of the stem
        glyph.lineTo(18, 11);                                              // stem up to the top
        glyph.append(new Arc2D.Double(11, 11, 14, 14, 90, -180, Arc2D.OPEN), true); // right-hand bowl
        g.draw(glyph);

        g.dispose();
        return img;
    }

    /** Assembles a PNG-compressed .ico (valid on Windows Vista+). */
    private static void writeIco(File out, int[] sizes, byte[][] frames) throws IOException {
        final int entryStart = 6;            // ICONDIR header
        final int dirSize = 16 * sizes.length;
        int offset = entryStart + dirSize;

        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            // ICONDIR
            le16(buf, 0);                    // reserved
            le16(buf, 1);                    // type 1 = icon
            le16(buf, sizes.length);         // image count

            int[] offsets = new int[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                offsets[i] = offset;
                offset += frames[i].length;
            }

            // ICONDIRENTRY[]
            for (int i = 0; i < sizes.length; i++) {
                int dim = sizes[i] >= 256 ? 0 : sizes[i]; // 0 means 256
                buf.write(dim & 0xFF);       // width
                buf.write(dim & 0xFF);       // height
                buf.write(0);                // colour count (0 = truecolour)
                buf.write(0);                // reserved
                le16(buf, 1);                // colour planes
                le16(buf, 32);               // bits per pixel
                le32(buf, frames[i].length); // bytes in resource
                le32(buf, offsets[i]);       // offset to image data
            }

            // payloads
            for (byte[] frame : frames) buf.write(frame);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(buf.toByteArray());
            }
        }
    }

    /** Assembles a macOS .icns from PNG-backed OSType entries (big-endian container). */
    private static void writeIcns(File out, String[] types, byte[][] frames) throws IOException {
        int total = 8; // 'icns' magic + length
        for (byte[] f : frames) total += 8 + f.length;

        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            buf.write('i'); buf.write('c'); buf.write('n'); buf.write('s');
            be32(buf, total);
            for (int i = 0; i < types.length; i++) {
                byte[] t = types[i].getBytes("US-ASCII");
                buf.write(t, 0, 4);
                be32(buf, 8 + frames[i].length); // entry length includes the 8-byte header
                buf.write(frames[i]);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(buf.toByteArray());
            }
        }
    }

    private static void le16(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
    }

    private static void be32(OutputStream o, int v) throws IOException {
        o.write((v >>> 24) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write(v & 0xFF);
    }

    private static void le32(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }
}
