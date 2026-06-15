package com.poe2runechecker.ui;

import com.poe2runechecker.price.PoeNinjaClient;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Небольшое окно управления в стиле PoE: переключение лиги + место под будущий донат.
 * Окно без системной рамки, перетаскивается за заголовок, закрытие прячет его в трей.
 */
public class ControlWindow {

    // палитра PoE
    private static final String GOLD      = "#c8a24b";
    private static final String GOLD_HI   = "#e7c873";
    private static final String GOLD_DIM  = "#8a6d2e";
    private static final String BG_TOP    = "#211810";
    private static final String BG_BOT    = "#0c0907";
    private static final String PANEL     = "#1a130b";
    private static final String SERIF     = "Georgia, 'Times New Roman', serif";

    private static final String DONATE_URL = "https://donatello.to/Or1on4ik";

    private final PoeNinjaClient prices;
    private final String[] leagues;
    private final Runnable onExit;
    private final HostServices host;
    private Stage stage;
    private double dx, dy;

    public ControlWindow(PoeNinjaClient prices, String[] leagues, Runnable onExit, HostServices host) {
        this.prices = prices;
        this.leagues = leagues;
        this.onExit = onExit;
        this.host = host;
    }

    public void show() {
        if (stage != null) { stage.show(); stage.toFront(); return; }

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setTitle("PoE2 Rune Checker");
        try (var in = ControlWindow.class.getResourceAsStream("/icon.png")) {
            if (in != null) stage.getIcons().add(new Image(in));
        } catch (Exception ignored) {}

        VBox root = new VBox();
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom, " + BG_TOP + ", " + BG_BOT + ");"
                + "-fx-border-color: " + GOLD_DIM + "; -fx-border-width: 2;");
        root.setPrefWidth(330);

        root.getChildren().addAll(titleBar(), body());

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    // ---- заголовок с иконкой и кнопкой закрытия ----
    private HBox titleBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(9, 12, 9, 12));
        bar.setStyle("-fx-background-color: linear-gradient(to bottom, #3a2c14, #241a0c);"
                + "-fx-border-color: " + GOLD_DIM + "; -fx-border-width: 0 0 1 0;");

        try (var in = ControlWindow.class.getResourceAsStream("/icon.png")) {
            if (in != null) {
                ImageView iv = new ImageView(new Image(in));
                iv.setFitWidth(26); iv.setFitHeight(26);
                bar.getChildren().add(iv);
            }
        } catch (Exception ignored) {}

        Label title = new Label("PoE2 Rune Checker");
        title.setStyle("-fx-text-fill: " + GOLD_HI + "; -fx-font-family: " + SERIF + ";"
                + "-fx-font-size: 15px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label close = new Label("✕");
        close.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 0 4;");
        close.setOnMouseClicked(e -> stage.hide()); // прячем в трей
        close.setOnMouseEntered(e -> close.setStyle("-fx-text-fill: #ff6a5a; -fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 0 4;"));
        close.setOnMouseExited(e -> close.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 0 4;"));

        bar.getChildren().addAll(title, spacer, close);

        // перетаскивание окна за заголовок
        bar.setOnMousePressed(e -> { dx = e.getSceneX(); dy = e.getSceneY(); });
        bar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dx); stage.setY(e.getScreenY() - dy); });
        return bar;
    }

    // ---- тело: лига + донат ----
    private VBox body() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(16, 18, 16, 18));

        box.getChildren().add(sectionLabel("LEAGUE"));

        ToggleGroup grp = new ToggleGroup();
        VBox leagueBox = new VBox(8);
        for (String lg : leagues) {
            ToggleButton tb = new ToggleButton(lg);
            tb.setToggleGroup(grp);
            tb.setMaxWidth(Double.MAX_VALUE);
            tb.setUserData(lg);
            styleToggle(tb, lg.equals(prices.league()));
            if (lg.equals(prices.league())) tb.setSelected(true);
            tb.setOnAction(e -> {
                if (!tb.isSelected()) { tb.setSelected(true); return; }
                prices.setLeague(lg);
                for (Toggle t : grp.getToggles()) styleToggle((ToggleButton) t, t == tb);
            });
            leagueBox.getChildren().add(tb);
        }
        box.getChildren().add(leagueBox);

        box.getChildren().add(divider());

        // секция доната (заглушка под будущий PayPal)
        box.getChildren().add(sectionLabel("SUPPORT"));
        Label hint = new Label("Enjoying the tool? You can treat me to a coffee — totally optional.");
        hint.setStyle("-fx-text-fill: #9a875f; -fx-font-family: " + SERIF + "; -fx-font-size: 12px;");
        hint.setWrapText(true);

        Button donate = new Button("☕  Buy me a coffee");
        donate.setMaxWidth(Double.MAX_VALUE);
        donate.setStyle(donateStyle(false));
        donate.setOnMouseEntered(e -> donate.setStyle(donateStyle(true)));
        donate.setOnMouseExited(e -> donate.setStyle(donateStyle(false)));
        donate.setOnAction(e -> {
            if (host != null && DONATE_URL != null && !DONATE_URL.isBlank())
                host.showDocument(DONATE_URL);
        });

        box.getChildren().addAll(hint, donate);

        box.getChildren().add(divider());

        Label footer = new Label("Open the rune window in game → prices appear over it.");
        footer.setWrapText(true);
        footer.setStyle("-fx-text-fill: #7d6f4e; -fx-font-family: " + SERIF + "; -fx-font-size: 11px;");
        box.getChildren().add(footer);

        Button exit = new Button("Exit");
        exit.setStyle("-fx-background-color: transparent; -fx-text-fill: #9a875f; -fx-border-color: " + GOLD_DIM + ";"
                + "-fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3; -fx-padding: 5 14;"
                + "-fx-font-family: " + SERIF + "; -fx-cursor: hand;");
        exit.setOnAction(e -> { if (onExit != null) onExit.run(); });
        HBox exitRow = new HBox(exit);
        exitRow.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(exitRow);

        return box;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-family: " + SERIF + ";"
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-letter-spacing: 2px;");
        return l;
    }

    private Region divider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setStyle("-fx-background-color: linear-gradient(to right, transparent, " + GOLD_DIM + ", transparent);");
        return r;
    }

    private void styleToggle(ToggleButton tb, boolean selected) {
        if (selected) {
            tb.setStyle("-fx-background-color: linear-gradient(to bottom, " + GOLD + ", #a07f30);"
                    + "-fx-text-fill: #1a1208; -fx-font-weight: bold; -fx-font-family: " + SERIF + ";"
                    + "-fx-border-color: " + GOLD_HI + "; -fx-border-width: 1; -fx-background-radius: 3;"
                    + "-fx-border-radius: 3; -fx-padding: 8 12; -fx-cursor: hand; -fx-font-size: 13px;");
        } else {
            tb.setStyle("-fx-background-color: " + PANEL + "; -fx-text-fill: " + GOLD + ";"
                    + "-fx-border-color: " + GOLD_DIM + "; -fx-border-width: 1; -fx-background-radius: 3;"
                    + "-fx-border-radius: 3; -fx-padding: 8 12; -fx-cursor: hand; -fx-font-family: " + SERIF + ";"
                    + "-fx-font-size: 13px;");
        }
    }

    private String donateStyle(boolean hover) {
        String bg = hover ? "linear-gradient(to bottom, #3a2c14, #2a1f0e)" : PANEL;
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + GOLD_HI + ";"
                + "-fx-border-color: " + GOLD + "; -fx-border-width: 1; -fx-background-radius: 3;"
                + "-fx-border-radius: 3; -fx-padding: 9 12; -fx-cursor: hand; -fx-font-family: " + SERIF + ";"
                + "-fx-font-size: 13px;";
    }
}
