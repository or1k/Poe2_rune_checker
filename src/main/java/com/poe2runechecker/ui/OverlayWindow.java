package com.poe2runechecker.ui;

import com.poe2runechecker.model.RecipeRow;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Comparator;
import java.util.List;

/**
 * Полупрозрачный оверлей поверх игры на AWT/Swing (per-pixel translucency).
 * Надёжнее прозрачных окон JavaFX в упакованном виде и не зависит от Prism-пайплайна.
 * Окно click-through — клики проходят в игру.
 */
public class OverlayWindow {

    private JWindow window;
    private OverlayPanel panel;

    public void show() {
        try {
            SwingUtilities.invokeAndWait(this::build);
        } catch (Exception e) {
            System.err.println("[overlay] " + e.getMessage());
        }
    }

    private void build() {
        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0)); // per-pixel прозрачность
        panel = new OverlayPanel();
        window.setContentPane(panel);

        Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        window.setBounds(b);
        window.setVisible(true);
        ClickThrough.apply(window); // после показа окна (нужен нативный hwnd)
    }

    /** Обновить отрисовку под текущие строки. Можно из любого потока. */
    public void render(List<RecipeRow> rows) {
        if (panel == null) return;
        panel.rows = rows;
        panel.repaint();
    }

    public void hide() {
        if (window != null) SwingUtilities.invokeLater(() -> window.setVisible(false));
    }

    /** Панель с отрисовкой цен и плашек. */
    private static final class OverlayPanel extends JComponent {
        volatile List<RecipeRow> rows = List.of();
        private final Font font = new Font("Segoe UI", Font.BOLD, 22);

        OverlayPanel() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics g0) {
            List<RecipeRow> rs = rows;
            if (rs.isEmpty()) return;

            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int asc = fm.getAscent(), desc = fm.getDescent();

            RecipeRow best = rs.stream().filter(r -> !r.priceUnknown)
                    .max(Comparator.comparingDouble(r -> r.totalValue)).orElse(null);

            for (RecipeRow r : rs) {
                String label = r.priceUnknown ? "N/A"
                        : (r.quantity > 1
                            ? String.format("%.1f ex (%.1f each)", r.totalValue, r.unitPrice)
                            : String.format("%.1f ex", r.totalValue));

                int x = r.screenXEnd + 20;
                int y = r.screenY + 7;
                int w = fm.stringWidth(label);
                int pad = 6;

                // плашка-подложка
                RoundRectangle2D plate = new RoundRectangle2D.Float(
                        x - pad, y - asc - pad / 2f, w + 2 * pad, asc + desc + pad, 11, 11);
                g.setColor(new Color(0, 0, 0, 160));
                g.fill(plate);

                boolean isBest = r == best;
                if (isBest) {
                    g.setColor(new Color(60, 220, 60, 230));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(plate);
                }

                // текст
                g.setColor(r.priceUnknown ? new Color(0xD0, 0xD0, 0xD0)
                        : (isBest ? new Color(0x66, 0xDD, 0x66) : new Color(0xE7, 0xC8, 0x73)));
                g.drawString(label, x, y);
            }
            g.dispose();
        }
    }
}
