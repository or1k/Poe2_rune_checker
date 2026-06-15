import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Генерит иконку в стиле PoE: золотая руна на тёмном алтарном фоне.
 *  Выход: src/main/resources/icon.png (256) и app.ico (мультиразмер). */
public class IconGen {

    static BufferedImage render(int S) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double c = S / 2.0, r = S * 0.46;

        // фон-круг: тёмный радиальный градиент (сине-чёрный камень)
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c, c * 0.8), (float) r,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(0x2a3550), new Color(0x141a28), new Color(0x080b12)}));
        g.fill(new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));

        // внешнее золотое кольцо
        Color gold = new Color(0xC8A24B), goldHi = new Color(0xF0D480);
        g.setStroke(new BasicStroke((float) (S * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(0, 0, goldHi, S, S, gold));
        g.draw(new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));

        // диамант-рамка внутри
        double d = S * 0.30;
        Path2D dia = new Path2D.Double();
        dia.moveTo(c, c - d); dia.lineTo(c + d, c); dia.lineTo(c, c + d); dia.lineTo(c - d, c); dia.closePath();
        g.setStroke(new BasicStroke((float) (S * 0.018)));
        g.setColor(new Color(0x8a6d2e));
        g.draw(dia);

        // руна (стилизованный Algiz/«жизнь»): свечение + золотые штрихи
        List<Line2D> strokes = new ArrayList<>();
        double h = S * 0.22, w = S * 0.16;
        strokes.add(new Line2D.Double(c, c - h, c, c + h));               // вертикаль
        strokes.add(new Line2D.Double(c, c - h * 0.15, c - w, c - h));    // ветвь влево-вверх
        strokes.add(new Line2D.Double(c, c - h * 0.15, c + w, c - h));    // ветвь вправо-вверх
        strokes.add(new Line2D.Double(c, c + h * 0.45, c - w * 0.8, c + h * 0.95)); // вниз-влево
        strokes.add(new Line2D.Double(c, c + h * 0.45, c + w * 0.8, c + h * 0.95)); // вниз-вправо

        // мягкое свечение
        g.setColor(new Color(0xFFE9A8));
        g.setStroke(new BasicStroke((float) (S * 0.075), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        for (Line2D l : strokes) g.draw(l);
        g.setComposite(oc);

        // сама руна
        g.setPaint(new GradientPaint(c2f(c), c2f(c - h), goldHi, c2f(c), c2f(c + h), gold));
        g.setStroke(new BasicStroke((float) (S * 0.040), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Line2D l : strokes) g.draw(l);

        g.dispose();
        return img;
    }

    static float c2f(double v) { return (float) v; }

    public static void main(String[] a) throws Exception {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(render(s), "png", b);
            pngs.add(b.toByteArray());
        }
        // PNG для трея (256)
        Path res = Paths.get("src/main/resources");
        Files.createDirectories(res);
        ImageIO.write(render(256), "png", res.resolve("icon.png").toFile());

        // мультиразмерный ICO (каждый кадр — PNG)
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream("app.ico")))) {
            o.writeShort(0); o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) sizes.length));
            int offset = 6 + sizes.length * 16;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                o.writeByte(s >= 256 ? 0 : s);
                o.writeByte(s >= 256 ? 0 : s);
                o.writeByte(0); o.writeByte(0);
                o.writeShort(Short.reverseBytes((short) 1));
                o.writeShort(Short.reverseBytes((short) 32));
                o.writeInt(Integer.reverseBytes(pngs.get(i).length));
                o.writeInt(Integer.reverseBytes(offset));
                offset += pngs.get(i).length;
            }
            for (byte[] p : pngs) o.write(p);
        }
        System.out.println("Wrote src/main/resources/icon.png and app.ico");
    }
}
