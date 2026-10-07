package com.authentisign.desktop.ui;

import com.authentisign.desktop.services.identity.UserService;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.animation.PauseTransition;
import javafx.util.*;

public class RegisterView {
    private static final Logger logger =
            LoggerFactory.getLogger(RegisterView.class);

    private TextField fullNameField;
    private TextField emailField;
    private PasswordField passwordField;
    private Label messageLabel;
    private Button registerButton;
    private Hyperlink loginLink;

    private final UserService userService;
    private final Stage primaryStage;
    private final DigSignApp mainApp;

    public RegisterView(Stage primaryStage, UserService userService, DigSignApp mainApp) {
        this.primaryStage = primaryStage;
        this.userService = userService;
        this.mainApp = mainApp;
    }

    // הפונקציה הראשית שמייצרת את ה-Scene ומציגה אותו
    public void show() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setSpacing(25);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: #F4F7F6;");
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        Label headerLabel = new Label("הרשמה למערכת");
        headerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        headerLabel.setTextFill(Color.web("#2C3E50"));

        Label subheaderLabel = new Label("AuthentiSign - פלטפורמת חתימה מאובטחת");
        subheaderLabel.setFont(Font.font("Segoe UI", 14));
        subheaderLabel.setTextFill(Color.web("#7F8C8D"));

        GridPane inputGrid = new GridPane();
        inputGrid.setAlignment(Pos.CENTER);
        inputGrid.setHgap(10);
        inputGrid.setVgap(15);
        inputGrid.setMaxWidth(400);
        // הגדרת סגנון אחיד לשדות קלט
        String inputStyle = "-fx-background-color: white; " +
                "-fx-border-color: #BDC3C7; " +
                "-fx-border-radius: 5px; " +
                "-fx-background-radius: 5px; " +
                "-fx-padding: 10px; " +
                "-fx-font-size: 14px;";

        // שדה שם מלא
        Label fullNameLabel = new Label("שם מלא:");
        fullNameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        fullNameField = new TextField();
        fullNameField.setPromptText("ישראל ישראלי");
        fullNameField.setStyle(inputStyle);
        inputGrid.add(fullNameLabel, 0, 0);
        inputGrid.add(fullNameField, 1, 0);

        // שדה אימייל
        Label emailLabel = new Label("אימייל:");
        emailLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        emailField = new TextField();
        emailField.setPromptText("example@email.com");
        emailField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT); // אימייל תמיד משמאל לימין
        emailField.setStyle(inputStyle);
        inputGrid.add(emailLabel, 0, 1);
        inputGrid.add(emailField, 1, 1);

        // סיסמה - כיווניות משמאל לימין בתוך השדה
        Label passwordLabel = new Label("סיסמה:");
        passwordLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        passwordField = new PasswordField();
        passwordField.setPromptText("******");
        passwordField.setStyle(inputStyle);
        passwordField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        inputGrid.add(passwordLabel, 0, 2);
        inputGrid.add(passwordField, 1, 2);

        // הודעת שגיאה/הצלחה
        messageLabel = new Label("");
        messageLabel.setFont(Font.font("Segoe UI", 13));
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);

        // אזור כפתורים
        VBox buttonContainer = new VBox(15);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setMaxWidth(400);

        registerButton = new Button("צור חשבון");
        registerButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        registerButton.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 12px 0; -fx-background-radius: 25px; -fx-cursor: hand;");
        registerButton.setMinWidth(280);
        registerButton.setOnAction(e -> handleRegister());

        loginLink = new Hyperlink("כבר יש לך חשבון? להתחברות");
        loginLink.setFont(Font.font("Segoe UI", 13));
        loginLink.setTextFill(Color.web("#7F8C8D"));
        loginLink.setUnderline(false);
        loginLink.setOnAction(e -> handleBackToLogin());

        // הוספת הכפתור והקישור
        buttonContainer.getChildren().addAll(registerButton, loginLink);

        root.getChildren().addAll(headerLabel, subheaderLabel, inputGrid, messageLabel, buttonContainer);

        //  יצירת הצגה
        Scene scene = new Scene(root, 600, 550);
        primaryStage.setTitle("AuthentiSign - הרשמה");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    //לוגיקת אבטחה
    private void handleRegister() {
        String fullName = fullNameField.getText();
        String email = emailField.getText().trim().toLowerCase();
        String password = passwordField.getText();

        //בדיקות תקינות
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showMessage("נא למלא את כל השדות", true);
            return;
        }

        // בדיקה בסיסית של פורמט אימייל
        if (!email.contains("@") || !email.contains(".")) {
            showMessage("כתובת אימייל לא תקינה", true);
            return;
        }

        // הגנה מפני אורך סיסמה
        if (password.length() < 8) {
            showMessage("הסיסמה חייבת להכיל לפחות 8 תווים", true);
            return;
        }

        try {
            userService.register(fullName, email, password);

            showMessage("החשבון נוצר בהצלחה! מעביר להתחברות...", false);
            clearFields();
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(event -> handleBackToLogin());
            delay.play();

        } catch (RuntimeException e) {
            if (e.getMessage().equals("Registration failed")) {
                showMessage("שגיאה: אימייל זה כבר רשום במערכת", true);
            } else {
                logger.error("Error during registration", e);
                showMessage("שגיאה בתהליך ההרשמה. נא לנסות שוב.", true);
            }
        } catch (Exception e) {
            try{
                logger.error("Fatal error during registration", e);
                showMessage("אירעה שגיאה לא צפויה במערכת", true);
            } catch(Exception ex){
                logger.error("Fatal error during registration", ex);
                mainApp.showLoginScene();
            }

        }
    }

    private void handleBackToLogin() {
        logger.info("User requested to go back to Login screen.");
        mainApp.showLoginScene();
    }

    // פונקציית עזר להצגת הודעות
    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setTextFill(isError ? Color.RED : Color.GREEN);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    // ניקוי שדות אחרי יצירת חשבוןכדי למנוע "זליגת מידע" מהזיכרון
    private void clearFields() {
        fullNameField.clear();
        emailField.clear();
        passwordField.clear();
    }
}