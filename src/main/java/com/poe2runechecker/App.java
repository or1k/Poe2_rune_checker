package com.poe2runechecker;

import com.poe2runechecker.capture.RuneWindowOcr;
import com.poe2runechecker.model.RecipeRow;
import com.poe2runechecker.price.PoeNinjaClient;
import com.poe2runechecker.ui.ControlWindow;
import com.poe2runechecker.ui.OverlayWindow;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;
import java.util.List;

/**
 * Точка входа. По хоткею (пока — по таймеру) делает OCR окна рун,
 * проставляет цены с poe.ninja и рисует оверлей.
 *
 * MVP-поток: запусти, открой в игре окно "Runeshape Combinations",
 * раз в 2 сек оверлей пересчитывает строки.
 *
 * TODO:
 *  - заменить таймер на глобальный хоткей (JNativeHook), чтобы не дёргать OCR постоянно;
 *  - сузить область OCR под реальные координаты окна рун;
 *  - подтвердить эндпоинты poe.ninja для PoE2.
 */
public class App extends Application {

    // Путь к tessdata (eng.traineddata). Скачать: github.com/tesseract-ocr/tessdata_fast
    private static final String TESSDATA = System.getProperty("tessdata", "tessdata");
    // Лиги PoE2 на poe.ninja (точные id для API — с пробелами).
    private static final String[] LEAGUES = {
            "Runes of Aldur",     // softcore — основная
            "HC Runes of Aldur",  // hardcore
            "Standard"
    };
    private static final String LEAGUE = System.getProperty("league", LEAGUES[0]);

    private final PoeNinjaClient prices =
            new PoeNinjaClient(LEAGUE, PoeNinjaClient.DisplayUnit.EXALTED);
    private OverlayWindow overlay;
    private ControlWindow control;
    private RuneWindowOcr ocr;
    private TrayIcon trayIcon;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Platform.setImplicitExit(false);
        overlay = new OverlayWindow();
        overlay.show();
        ocr = new RuneWindowOcr(TESSDATA);

        control = new ControlWindow(prices, LEAGUES, this::exitApp, getHostServices());
        control.show();

        installTray();

        Thread loop = new Thread(this::scanLoop, "scan-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private volatile boolean exiting = false;

    /** Полное завершение: убрать иконку трея, закрыть JavaFX и форсированно убить процесс. */
    private void exitApp() {
        if (exiting) return;
        exiting = true;
        try {
            if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception ignored) {}
        try { Platform.exit(); } catch (Exception ignored) {}
        // halt вместо System.exit: гарантированно убивает процесс, не дожидаясь
        // нативных потоков Tesseract/JavaFX и shutdown-хуков.
        Runtime.getRuntime().halt(0);
    }

    /** Иконка в трее: показать панель и выход (лига переключается в окне). */
    private void installTray() {
        if (!SystemTray.isSupported()) return;
        try {
            Image icon;
            try (var in = App.class.getResourceAsStream("/icon.png")) {
                icon = in != null ? javax.imageio.ImageIO.read(in)
                                  : Toolkit.getDefaultToolkit().createImage(new byte[0]);
            }
            PopupMenu menu = new PopupMenu();

            MenuItem showPanel = new MenuItem("Show Panel");
            showPanel.addActionListener(e -> Platform.runLater(() -> control.show()));
            menu.add(showPanel);

            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> exitApp());
            menu.add(exit);

            trayIcon = new TrayIcon(icon, "PoE2 Rune Checker", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> control.show())); // двойной клик
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            System.err.println("[tray] " + e.getMessage());
        }
    }

    /** Стабильная строка-слот: позиция фиксируется, цена держится последняя валидная. */
    private static final class Slot {
        int y;            // зафиксированная Y на экране
        int quantity = 1;
        String name = "";
        double unitPrice = 0;
        boolean priceUnknown = false; // предмет с непредсказуемым уровнем (gem) -> N/A
        long lastSeenScan;
    }

    /** Предметы, цену которых нельзя определить из окна (уровень гема неизвестен). */
    private static boolean isUnpriceable(String ocrName) {
        String n = ocrName.toLowerCase();
        return n.contains("uncut") || n.contains("spirit gem") || n.contains("skill gem");
    }

    private final java.util.List<Slot> slots = new java.util.ArrayList<>();
    private long scanId = 0;
    private static final long STALE_SCANS = 2;     // через сколько пустых сканов строка исчезает
    private static final double LOCK_SCORE = 0.62; // порог уверенности для фиксации цены

    /** Ближайший существующий слот по Y в пределах допуска, иначе null. */
    private Slot nearestSlot(int y, double tol) {
        Slot best = null; double bestD = tol;
        for (Slot s : slots) {
            double d = Math.abs(s.y - y);
            if (d <= bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private Slot addSlot(int y) {
        Slot s = new Slot();
        s.y = y;
        slots.add(s);
        return s;
    }

    private void scanLoop() {
        var screen = Toolkit.getDefaultToolkit().getScreenSize();
        int priceX = (int) (screen.width * 0.30);          // фиксированная колонка цен
        double tol = screen.height * 0.022;                // допуск привязки к строке (~24px на 1080p)

        while (true) {
            try {
                // нет паузы -> окно рун закрыто -> мгновенно прячем оверлей
                if (!ocr.gamePaused()) {
                    if (!slots.isEmpty()) {
                        slots.clear();
                        overlay.render(java.util.Collections.emptyList());
                    }
                    Thread.sleep(600);
                    continue;
                }

                scanId++;
                List<RecipeRow> raw = ocr.read(null);
                boolean windowOpen = true; // пауза есть -> окно открыто

                for (RecipeRow r : raw) {
                    Slot near = nearestSlot(r.screenY, tol);

                    // строка зафиксирована и окно открыто -> держим живой по наличию текста рядом
                    // (даже если этот скан прочитал имя слабо). Цену не трогаем.
                    if (windowOpen && near != null && (near.unitPrice > 0 || near.priceUnknown)) {
                        if (r.explicitQty) near.quantity = r.quantity;
                        near.lastSeenScan = scanId;
                        continue;
                    }

                    // gem с неизвестным уровнем -> N/A
                    if (isUnpriceable(r.itemName)) {
                        Slot s = near != null ? near : addSlot(r.screenY);
                        s.priceUnknown = true;
                        s.unitPrice = 0;
                        if (s.name.isEmpty()) s.name = "Uncut Gem";
                        if (r.explicitQty) s.quantity = r.quantity;
                        s.lastSeenScan = scanId;
                        continue;
                    }

                    // ещё не зафиксирована -> фиксируем только при уверенном матче
                    PoeNinjaClient.Match match = prices.bestMatch(r.itemName);
                    if (match == null || match.price() <= 0 || match.score() < LOCK_SCORE) {
                        continue; // шум / слабое чтение — не создаём слот
                    }

                    Slot s = near != null ? near : addSlot(r.screenY);
                    if (r.explicitQty) s.quantity = r.quantity;
                    s.priceUnknown = false;
                    s.name = match.name();
                    s.unitPrice = match.price();
                    s.lastSeenScan = scanId;
                }

                // убираем строки, которых давно не видно (окно закрыли / другой список)
                slots.removeIf(s -> scanId - s.lastSeenScan > STALE_SCANS);
                slots.sort(java.util.Comparator.comparingInt(s -> s.y));

                // строим стабильный список для отрисовки
                List<RecipeRow> rows = new java.util.ArrayList<>();
                for (Slot s : slots) {
                    if (s.unitPrice <= 0 && !s.priceUnknown) continue; // нечего показывать
                    RecipeRow row = new RecipeRow(s.quantity, s.name, s.y);
                    row.screenXEnd = priceX;
                    row.priceUnknown = s.priceUnknown;
                    row.unitPrice = s.unitPrice;
                    row.totalValue = s.quantity * s.unitPrice;
                    rows.add(row);
                }

                System.out.println("[scan] slots=" + slots.size());
                rows.forEach(r -> System.out.println("   " + r));
                overlay.render(rows);
                Thread.sleep(800);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                System.err.println("[loop] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
