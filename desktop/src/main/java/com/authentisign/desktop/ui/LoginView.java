package com.authentisign.desktop.ui;

import com.authentisign.desktop.services.identity.UserService;
import com.authentisign.desktop.database.entities.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import java.util.Optional;

public class LoginView {

    private final Stage stage;
    private final UserService userService;
    private final DigSignApp mainApp;

    private TextField emailField;
    private PasswordField passwordField;
    private Label messageLabel;
    private Button loginButton;

    public LoginView(Stage stage, UserService userService, DigSignApp mainApp) {
        this.stage = stage;
        this.userService = userService;
        this.mainApp = mainApp;
    }

    public void show() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: #f4f7f6;");
        root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);

        Label logoLabel = new Label("🔐");
        logoLabel.setFont(Font.font(50));

        Label titleLabel = new Label("AuthentiSign");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.web("#2c3e50"));

        Label subtitleLabel = new Label("מערכת חתימה דיגיטלית מאובטחת");
        subtitleLabel.setFont(Font.font("Segoe UI", 14));
        subtitleLabel.setTextFill(Color.web("#7f8c8d"));

        header.getChildren().addAll(logoLabel, titleLabel, subtitleLabel);

        VBox card = new VBox(25);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(400);
        card.setStyle("-fx-background-color: white; -fx-padding: 40; -fx-background-radius: 15; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 20, 0, 0, 0);");

        String fieldStyle = "-fx-padding: 12; -fx-background-radius: 5; -fx-border-color: #dcdde1; -fx-font-size: 14px;";

        emailField = new TextField();
        emailField.setPromptText("כתובת אימייל");
        emailField.setStyle(fieldStyle);

        passwordField = new PasswordField();
        passwordField.setPromptText("סיסמה");
        passwordField.setStyle(fieldStyle);
        passwordField.setNodeOrientation(javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);

        messageLabel = new Label();
        messageLabel.setFont(Font.font("Segoe UI", 13));
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);


        loginButton = new Button("התחברות");
        loginButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        loginButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-min-width: 300; -fx-padding: 15; -fx-background-radius: 8; -fx-cursor: hand;");
        loginButton.setOnAction(e -> handleLogin());

        Hyperlink registerLink = new Hyperlink("עדיין אין לך חשבון? הרשמה");
        registerLink.setOnAction(e -> mainApp.showRegisterScene());

        card.getChildren().addAll(emailField, passwordField, messageLabel, loginButton, registerLink);
        root.getChildren().addAll(header, card);

        stage.setTitle("AuthentiSign - התחברות");
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    private void handleLogin() {
        String email = emailField.getText().toLowerCase().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showMessage("נא למלא אימייל וסיסמה", true);
            return;
        }

        loginButton.setDisable(true);
        showMessage("מתחבר למערכת...", false);

        new Thread(() -> {
            try {
                Optional<User> userOptional = userService.login(email, password);

                Platform.runLater(() -> {
                    if (userOptional.isPresent()) {
                        showMessage("התחברת בהצלחה! מעביר...", false);
                        PauseTransition delay = new PauseTransition(Duration.seconds(1));
                        delay.setOnFinished(e -> mainApp.showDashboard(userOptional.get()));
                        delay.play();
                    } else {
                        showMessage("אימייל או סיסמה שגויים", true);
                        loginButton.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showMessage("שגיאת חיבור לשרת", true);
                    loginButton.setDisable(false);
                });
            }
        }).start();
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setTextFill(isError ? Color.RED : Color.GREEN);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }
}