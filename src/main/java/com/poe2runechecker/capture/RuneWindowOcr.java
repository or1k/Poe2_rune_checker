package com.poe2runechecker.capture;

import com.poe2runechecker.model.RecipeRow;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.ITessAPI;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Делает скриншот колонки текста окна рун, прогоняет OCR
 * и достаёт строки вида "2x Gemcutter's Prism" (количество может отсутствовать — тогда 1).
 *
 * Имена приходят кривыми — их потом fuzzy-сопоставляет PoeNinjaClient.bestMatch.
 */
public class RuneWindowOcr {

    // "2x Gemcutter's Prism" / "Uncut Spirit Gem" (qty опционально)
    private static final Pattern ROW = Pattern.compile("(?:(\\d+)\\s*x\\s+)?(.+)");

    private static final int SCALE = 3; // апскейл для точности OCR

    private final Robot robot;
    private final Tesseract tess;
    private final Rectangle screen;

    public RuneWindowOcr(String tessDataPath) throws Exception {
        this.robot = new Robot();
        var d = Toolkit.getDefaultToolkit().getScreenSize();
        this.screen = new Rectangle(0, 0, d.width, d.height);

        this.tess = new Tesseract();
        tess.setDatapath(tessDataPath);
        tess.setLanguage("eng");
        tess.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);
        // только буквы/цифры/апостроф/пробел — меньше мусора
        tess.setVariable("tessedit_char_whitelist",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'0123456789x ");
    }

    /**
     * Колонка с текстом результата (без иконок и фона карты).
     * Координаты относительны к разрешению — окно рун всегда вверху слева.
     */
    private Rectangle defaultRegion() {
        // Узкая правая колонка с названиями: после иконок (≤5 шт) и до правого края панели.
        int x = (int) (screen.width * 0.165);  // правее всех иконок рун
        int w = (int) (screen.width * 0.155);  // до правого края панели
        int y = (int) (screen.height * 0.13);  // ниже заголовка
        int h = (int) (screen.height * 0.55);  // вся высота списка панели (любое число строк)
        return new Rectangle(x, y, w, h);
    }

    /**
     * Виден ли баннер "GAME PAUSED" по центру вверху. Окно рун ставит игру на паузу,
     * поэтому это надёжный признак, что оверлей надо показывать.
     * Текст светлый на тёмном фоне -> инвертированная бинаризация.
     */
    public boolean gamePaused() {
        int x = (int) (screen.width * 0.38);
        int w = (int) (screen.width * 0.24);
        int y = (int) (screen.height * 0.11);
        int h = (int) (screen.height * 0.08);
        BufferedImage raw = robot.createScreenCapture(new Rectangle(x, y, w, h));

        int W = raw.getWidth(), H = raw.getHeight();
        long sum = 0; int[] lum = new int[W * H];
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++) {
                int rgb = raw.getRGB(xx, yy);
                int l = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                lum[yy * W + xx] = l; sum += l;
            }
        int t = (int) (sum / (W * H) * 1.5); // яркий текст выше среднего

        BufferedImage out = new BufferedImage(W * 2, H * 2, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++) {
                int v = lum[yy * W + xx] > t ? 0x000000 : 0xFFFFFF; // инверсия: яркое -> чёрное
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++)
                        out.setRGB(xx * 2 + dx, yy * 2 + dy, v);
            }
        try {
            String s = tess.doOCR(out).toLowerCase().replaceAll("[^a-z]", "");
            return s.contains("paused") || s.contains("aused") || s.contains("gamepau");
        } catch (Exception e) {
            return true; // при сбое OCR не прячем
        }
    }

    public List<RecipeRow> read(Rectangle region) {
        if (region == null) region = defaultRegion();

        BufferedImage raw = robot.createScreenCapture(region);
        BufferedImage shot = preprocess(raw, SCALE);

        List<RecipeRow> rows = new ArrayList<>();
        try {
            List<Word> words = tess.getWords(shot, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
            for (Word w : words) {
                String text = w.getText().trim();
                if (text.length() < 3) continue;
                Matcher m = ROW.matcher(text);
                if (!m.matches()) continue;
                boolean explicit = m.group(1) != null;
                int qty = explicit ? Integer.parseInt(m.group(1)) : 1;
                String name = cleanName(m.group(2));
                if (name.length() < 3) continue;
                int y = region.y + (int) (w.getBoundingBox().getCenterY() / SCALE);
                RecipeRow row = new RecipeRow(qty, name, y);
                row.explicitQty = explicit;
                rows.add(row);
            }
        } catch (Exception e) {
            System.err.println("[OCR] failed: " + e.getMessage());
        }
        return rows;
    }

    /** Серый + бинаризация по среднему + апскейл — убирает текстуру пергамента. */
    private static BufferedImage preprocess(BufferedImage src, int scale) {
        int w = src.getWidth(), h = src.getHeight();
        int[] lum = new int[w * h];
        long sum = 0;
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int rgb = src.getRGB(xx, yy);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int l = (r * 299 + g * 587 + b * 114) / 1000;
                lum[yy * w + xx] = l;
                sum += l;
            }
        }
        // только самый тёмный (жирный) текст имён; каракули/фон светлее — отсекаем
        int threshold = (int) (sum / (w * h) * 0.72);

        BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int v = lum[yy * w + xx] < threshold ? 0x000000 : 0xFFFFFF;
                for (int dy = 0; dy < scale; dy++)
                    for (int dx = 0; dx < scale; dx++)
                        out.setRGB(xx * scale + dx, yy * scale + dy, v);
            }
        }
        return out;
    }

    private static String cleanName(String raw) {
        return raw.replaceAll("[^A-Za-z' ]", " ").replaceAll("\\s+", " ").trim();
    }
}
