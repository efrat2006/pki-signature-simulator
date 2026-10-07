package com.authentisign.desktop.ui;

import com.authentisign.desktop.database.entities.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;


public class PersonalDetailsView {

    private static final String C_PRIMARY = "#1e3a5f";
    private static final String C_PRIMARY_DARK = "#162d4a";
    private static final String C_BG = "#f5f6fa";
    private static final String C_WHITE = "#ffffff";
    private static final String C_BORDER = "#e2e6ed";
    private static final String C_TEXT_MAIN = "#1e3a5f";
    private static final String C_TEXT_MUTED = "#718096";
    private static final String C_TEXT_LIGHT = "#a0aec0";
    private static final String C_GREEN = "#38a169";
    private static final String C_GREEN_BG = "#f0faf4";
    private static final String C_AMBER = "#d97706";
    private static final String C_AMBER_BG = "#fffbeb";
    private static final String C_BLUE_BG = "#ebf0f8";

    private final User user;
    private TextField nameEditField;
    private Label nameDisplayLabel;
    private HBox nameEditBox;
    private HBox nameDisplayBox;


    public PersonalDetailsView(User user) {
        this.user = user;
    }

    public ScrollPane build() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");
        content.getChildren().add(buildHeader());
        content.getChildren().add(buildUserInfoCard());


        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + ";");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return scroll;
    }


    private VBox buildHeader() {
        VBox header = new VBox(5);

        Label title = createStyledLabel("פרטים אישיים", C_TEXT_MAIN, 24, true);
        Label subtitle = createStyledLabel("ניהול מידע החשבון שלך", C_TEXT_MUTED, 12, false);

        header.getChildren().addAll(title, subtitle);
        return header;
    }


    private VBox buildUserInfoCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle(cardStyle());


        nameDisplayBox = new HBox(15);
        nameDisplayBox.setAlignment(Pos.CENTER_RIGHT);
        nameDisplayBox.setPadding(new Insets(10, 0, 10, 0));
        nameDisplayBox.setStyle("-fx-border-color: transparent transparent " + C_BORDER + " transparent; " +
                "-fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        Label nameLabel = createStyledLabel("👤 שם:", C_TEXT_MUTED, 11, true);
        nameLabel.setMinWidth(100);

        nameDisplayLabel = createStyledLabel(user.getName(), C_TEXT_MAIN, 12, false);
        Label editHint = createStyledLabel("(לחץ לשינוי)", C_TEXT_LIGHT, 10, false);

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        nameDisplayBox.getChildren().addAll(nameLabel, spacer1, nameDisplayLabel, editHint);

        nameEditBox = new HBox(10);
        nameEditBox.setAlignment(Pos.CENTER_RIGHT);
        nameEditBox.setPadding(new Insets(10, 0, 10, 0));
        nameEditBox.setStyle("-fx-border-color: transparent transparent " + C_BORDER + " transparent; " +
                "-fx-border-width: 0 0 1 0;");
        nameEditBox.setVisible(false);

        nameEditField = new TextField();
        nameEditField.setText(user.getName());
        nameEditField.setPrefWidth(200);
        nameEditField.setStyle(textFieldStyle());

        Button saveBtn = createButton("✓ שמור", C_WHITE, C_GREEN);
        saveBtn.setStyle("-fx-padding: 8 12; -fx-font-size: 10;");

        Button cancelBtn = createButton("✕ בטל", C_WHITE, C_AMBER);
        cancelBtn.setStyle("-fx-padding: 8 12; -fx-font-size: 10;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        nameEditBox.getChildren().addAll(spacer2, nameEditField, saveBtn, cancelBtn);


        card.getChildren().add(nameDisplayBox);
        card.getChildren().add(nameEditBox);
        card.getChildren().add(new Separator());

        card.getChildren().add(createDetailRow("📧 אימייל:", user.getEmail(), false));
        card.getChildren().add(new Separator());

        String createdDate = user.getCreatedAt() != null
                ? user.getCreatedAt().toString()
                : "לא זמין";
        card.getChildren().add(createDetailRow("📅 חברות מ:", createdDate, false));

        return card;
    }


    private HBox createDetailRow(String label, String value, boolean editable) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(10, 0, 10, 0));
        row.setStyle("-fx-border-color: transparent transparent " + C_BORDER + " transparent; " +
                "-fx-border-width: 0 0 1 0;");

        Label labelLbl = createStyledLabel(label, C_TEXT_MUTED, 11, true);
        labelLbl.setMinWidth(100);

        String valueColor = editable ? C_TEXT_MAIN : C_TEXT_MUTED;
        Label valueLbl = createStyledLabel(value, valueColor, 12, false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(labelLbl, spacer, valueLbl);
        return row;
    }

    private Label createStyledLabel(String text, String color, int size, boolean bold) {
        Label label = new Label(text);
        String style = "-fx-text-fill: " + color + "; " +
                "-fx-font-size: " + size + "; ";
        if (bold) {
            style += "-fx-font-weight: bold;";
        }
        label.setStyle(style);
        return label;
    }


    private Button createButton(String text, String textColor, String bgColor) {
        Button button = new Button(text);
        String style = "-fx-padding: 10 16; " +
                "-fx-font-size: 11; " +
                "-fx-text-fill: " + textColor + "; " +
                "-fx-background-color: " + bgColor + "; " +
                "-fx-border-radius: 6; " +
                "-fx-cursor: hand;";
        button.setStyle(style);

        // Hover effect - darker on hover
        button.setOnMouseEntered(e -> {
            if (bgColor.equals(C_PRIMARY)) {
                button.setStyle(style + " -fx-background-color: " + C_PRIMARY_DARK + ";");
            } else if (bgColor.equals(C_GREEN)) {
                button.setStyle(style + " -fx-background-color: #2e7d32;");
            } else {
                button.setStyle(style + " -fx-background-color: " + C_BLUE_BG + ";");
            }
        });

        // Return to normal on exit
        button.setOnMouseExited(e -> {
            button.setStyle(style);
        });

        return button;
    }

    private Label sectionTitle(String text) {
        return createStyledLabel(text, C_TEXT_MAIN, 14, true);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        alert.showAndWait();
    }

    private String cardStyle() {
        return "-fx-background-color: " + C_WHITE + "; " +
                "-fx-border-color: " + C_BORDER + "; " +
                "-fx-border-radius: 8; " +
                "-fx-border-width: 1;";
    }

    private String cardWarningStyle() {
        return "-fx-background-color: " + C_AMBER_BG + "; " +
                "-fx-border-color: " + C_AMBER + "; " +
                "-fx-border-radius: 8; " +
                "-fx-border-width: 1;";
    }


    private String textFieldStyle() {
        return "-fx-font-size: 11; " +
                "-fx-padding: 8 12; " +
                "-fx-text-fill: " + C_TEXT_MAIN + "; " +
                "-fx-prompt-text-fill: " + C_TEXT_MUTED + "; " +
                "-fx-border-color: " + C_BORDER + "; " +
                "-fx-border-radius: 6; " +
                "-fx-border-width: 1; " +
                "-fx-background-color: " + C_WHITE + ";";
    }
}