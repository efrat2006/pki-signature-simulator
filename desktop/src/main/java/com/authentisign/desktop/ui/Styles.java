package com.authentisign.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
public final class Styles {

    private Styles() {}

    public static final String C_PRIMARY        = "#1e3a5f";
    public static final String C_PRIMARY_DARK   = "#162d4a";
    public static final String C_PRIMARY_LIGHT  = "#ebf0f8";
    public static final String C_BG             = "#f5f6fa";
    public static final String C_WHITE          = "#ffffff";
    public static final String C_BORDER         = "#e2e6ed";
    public static final String C_TEXT_MAIN      = "#1e3a5f";
    public static final String C_TEXT_MUTED     = "#718096";
    public static final String C_TEXT_LIGHT     = "#a0aec0";
    public static final String C_GREEN          = "#38a169";
    public static final String C_GREEN_BG       = "#f0faf4";
    public static final String C_GREEN_BORDER   = "#c6f6d5";
    public static final String C_BLUE_BG        = "#ebf0f8";
    public static final String C_SIDEBAR_HOVER  = "#f0f4f8";
    public static final String C_SIDEBAR_ACTIVE = "#ebf0f8";
    public static final String C_AMBER          = "#d97706";
    public static final String C_AMBER_BG       = "#fffbeb";
    public static final String C_RED            = "#c53030";
    public static final String C_RED_BG         = "#fff0f0";
    public static final String C_RED_BORDER     = "#fed7d7";


    /** כפתור ראשי — רקע כחול כהה */
    public static Button primaryButton(String text) {
        Button btn = new Button(text);
        String base  = btnStyle(C_PRIMARY, "white", C_PRIMARY, false);
        String hover = btnStyle(C_PRIMARY_DARK, "white", C_PRIMARY_DARK, false);
        applyHover(btn, base, hover);
        return btn;
    }

    /** כפתור משני — שקוף */
    public static Button secondaryButton(String text) {
        Button btn = new Button(text);
        String base  = "-fx-background-color: transparent; -fx-text-fill: " + C_TEXT_MUTED + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand; -fx-border-width: 0;";
        String hover = "-fx-background-color: " + C_BLUE_BG + "; -fx-text-fill: " + C_TEXT_MAIN + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand; -fx-border-width: 0;";
        applyHover(btn, base, hover);
        return btn;
    }

    /** כפתור מסוכן — אדום עם גבול */
    public static Button dangerButton(String text) {
        Button btn = new Button(text);
        String base  = "-fx-background-color: transparent; -fx-text-fill: " + C_RED + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;" +
                "-fx-border-color: " + C_RED_BORDER + "; -fx-border-radius: 6; -fx-border-width: 1;";
        String hover = "-fx-background-color: " + C_RED_BG + "; -fx-text-fill: " + C_RED + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;" +
                "-fx-border-color: " + C_RED + "; -fx-border-radius: 6; -fx-border-width: 1;";
        applyHover(btn, base, hover);
        return btn;
    }

    // ══════════════════════════════════════════
    // LABELS
    // ══════════════════════════════════════════

    /** כותרת ראשית של דף */
    public static Label pageTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 20; -fx-font-weight: bold;");
        return lbl;
    }

    /** תת-כותרת */
    public static Label subTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        return lbl;
    }

    /** כותרת סקשן (אותיות קטנות, אפור בהיר) */
    public static Label sectionTitle(String text) {
        Label lbl = new Label(text.toUpperCase());
        lbl.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 11; -fx-font-weight: bold;");
        return lbl;
    }

    /** pill / badge צבעוני */
    public static Label pill(String text, String bg, String fg) {
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                        "-fx-background-radius: 10; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 3 10;"
        );
        return lbl;
    }

    // ══════════════════════════════════════════
    // CARDS
    // ══════════════════════════════════════════

    /** כרטיס מטריקה (מספר + תווית) */
    public static HBox metricCard(String label, String value, String valueColor, String bg) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setPrefWidth(180);
        card.setStyle(
                "-fx-background-color: " + bg + "; -fx-background-radius: 10;" +
                        "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 10; -fx-border-width: 1;"
        );
        VBox textBox = new VBox(3);
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-size: 26; -fx-font-weight: bold;");
        Label labelLbl = new Label(label);
        labelLbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 12;");
        textBox.getChildren().addAll(valueLbl, labelLbl);
        card.getChildren().add(textBox);
        return card;
    }

    /** כרטיס לבן סטנדרטי עם גבול */
    public static VBox whiteCard(double radius) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle(
                "-fx-background-color: " + C_WHITE + "; -fx-background-radius: " + radius + ";" +
                        "-fx-border-color: " + C_BORDER + "; -fx-border-radius: " + radius + "; -fx-border-width: 1;"
        );
        return card;
    }

    // ══════════════════════════════════════════
    // LAYOUT HELPERS
    // ══════════════════════════════════════════

    /** ScrollPane אחיד לכל המסכים */
    public static javafx.scene.control.ScrollPane pageScroll(javafx.scene.Node content) {
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + ";");
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** Region שמתרחב (flex-spacer) */
    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region vSpacer() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    // ══════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════

    private static String btnStyle(String bg, String fg, String border, boolean hasBorder) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                "-fx-font-size: 12; -fx-font-weight: bold; -fx-background-radius: 6;" +
                "-fx-padding: 6 14; -fx-cursor: hand;";
    }

    private static void applyHover(Button btn, String base, String hover) {
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
    }
}
