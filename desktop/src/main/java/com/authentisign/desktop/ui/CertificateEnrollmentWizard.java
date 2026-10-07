package com.authentisign.desktop.ui;

import com.authentisign.desktop.client.AuthClient;
import com.authentisign.desktop.client.CaCertificateDownloader;
import com.authentisign.desktop.client.CertificateIssuanceClient;
import com.authentisign.desktop.security.KeyStore.KeyStoreService;
import com.authentisign.desktop.services.enrollment.CsrService;
import com.authentisign.desktop.security.strategies.*;
import com.authentisign.desktop.security.strategies.Ed25519;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;

public class CertificateEnrollmentWizard {

    private final Stage dialog;
    private final VBox mainLayout;
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("ממתין להגדרות...");
    private final Label stepDetail = new Label("אנא בחר שם לתעודה וסיסמה");

    private final KeyStoreService ksService = new KeyStoreService();

    private final String userName;
    private final String email;

    public CertificateEnrollmentWizard(Stage owner, String userName, String email) {
        this.userName = userName;
        this.email = email;
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());

        this.dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("הנפקת זהות דיגיטלית - " + userName);

        mainLayout = new VBox(20);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setStyle("-fx-background-color: white; -fx-padding: 40; -fx-background-radius: 10;");

        buildSelectionUI();
        dialog.setScene(new Scene(mainLayout, 500, 450));
    }

    private void buildSelectionUI() {
        Label title = new Label("הגדרות הנפקת תעודה");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e3a5f;");

        SigningStrategy strategy = new Ed25519();

        TextField aliasField = new TextField();
        aliasField.setPromptText("תן שם לתעודה");
        aliasField.setPrefWidth(300);

        PasswordField passField = new PasswordField();
        passField.setPromptText("הזן סיסמה להגנת הכספת");
        passField.setPrefWidth(300);

        Button startBtn = new Button("התחל תהליך הנפקה");
        startBtn.setStyle("-fx-background-color: #1e3a5f; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 25; -fx-cursor: hand;");

        //פעולת כפתור הנפק תעודה
        startBtn.setOnAction(e -> {
            if (passField.getText().isEmpty() || aliasField.getText().isEmpty()) {
                stepDetail.setText("שגיאה: חובה למלא את כל השדות!");
                return;
            }
            switchToProgressUI(strategy, passField.getText().toCharArray(), aliasField.getText());
        });

        mainLayout.getChildren().addAll(title, new Label("הזהות תונפק עבור: " + userName + " (" + email + ")"),
                aliasField, passField, startBtn, stepDetail);
    }

    private void switchToProgressUI(SigningStrategy strategy, char[] password, String alias) {
        mainLayout.getChildren().clear();
        Label title = new Label("מנפיק תעודה דיגיטלית...");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        progressBar.setPrefWidth(350);
        mainLayout.getChildren().addAll(title, progressBar, statusLabel, stepDetail);
        runEnrollmentLogic(strategy, password, alias);
    }


    //הנפקת תעודה
    private void runEnrollmentLogic(SigningStrategy strategy, char[] password, String alias) {
        System.out.println("Enrollment for userName='" + userName + "', email='" + email + "'");

        Thread worker = new Thread(() -> {
            CsrService csrService = new CsrService();
            CertificateIssuanceClient issuanceClient = new CertificateIssuanceClient();
            CaCertificateDownloader caCertClient = new CaCertificateDownloader();

            try {
                uiUpdate(0.15, "מייצר מפתחות...", "שומר זוג מפתחות בכספת המקומית...");
                KeyStore ks = ksService.generateAndSaveKeyPair(strategy, password, alias, email, userName);
                System.out.println("המפתחות נשמרו בהצלחה בכספת: " + ksService.getPathForAlias(email, alias));

                //מייצר csr
                uiUpdate(0.40, "מייצר בקשת חתימה...", "מכין בקשת חתימה...");
                String csrPem = csrService.generateCsr(ks, password, alias, userName, strategy);
                System.out.println("CSR נוצר בהצלחה");

                //מוודא שקיים טוקן
                if (AuthClient.getToken() == null || AuthClient.getToken().isBlank()) {
                    uiUpdate(0.50, "מתחבר לשרת...", "מבקש טוקן הזדהות...");
                    if (!AuthClient.requestToken(email)) {
                        throw new IllegalStateException("לא התקבל טוקן הזדהות מהשרת עבור " + email);
                    }
                }

                //בקשת תעודה חתומה מהשרת
                uiUpdate(0.70, "מול ה-CA...", "שולח ומבקש תעודה חתומה...");
                X509Certificate issuedCert = issuanceClient.requestCertificate(email, csrPem);
                System.out.println("התקבלה תעודה מה-CA. Subject: " + issuedCert.getSubjectX500Principal());

                //הורדת התעודה החתומה לKS
                uiUpdate(0.85, "בונה שרשרת...", "מוריד את תעודת ה-CA...");
                X509Certificate caCert = caCertClient.fetchCaCertificate();
                if (caCert == null) {
                    System.out.println("אזהרה: לא הצלחנו להוריד את תעודת ה-CA — מותקנת רק תעודת המשתמש");
                }

                uiUpdate(0.95, "מתקין תעודה...", "מחליף את התעודה הזמנית בתעודה מה-CA...");
                ksService.installIssuedCertificate(email, alias, password, issuedCert, caCert);
                System.out.println("התעודה הותקנה בכספת בהצלחה");

                uiUpdate(1.0, "הושלם!", "התעודה הונפקה והותקנה בכספת בהצלחה ✔");
                finish(password, true);

            } catch (Exception ex) {
                System.out.println("error: " + ex.getMessage());
                ex.printStackTrace();
                uiUpdate(0.0, "שגיאה!", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                finish(password, false);
            }
        }, "cert-enrollment");

        worker.setDaemon(true);
        worker.start();
    }

    private void uiUpdate(double progress, String status, String detail) {
        Platform.runLater(() -> updateStep(progress, status, detail));
    }

    private void updateStep(double progress, String status, String detail) {
        progressBar.setProgress(progress);
        statusLabel.setText(status);
        stepDetail.setText(detail);
    }


    //מנקה את הסיסמה מהזיכרון ומשנה את מצב החלון בהתאם
    private void finish(char[] password, boolean success) {
        try {
            ksService.wipePassword(password);
            System.out.println("הסיסמה נמחקה מהזיכרון");
        } catch (Exception ignored) {}

        if (success) {
            Platform.runLater(() -> {
                PauseTransition pause = new PauseTransition(Duration.seconds(1.8));
                pause.setOnFinished(e -> dialog.close());
                pause.play();
            });
        }
    }

    public void show() {
        dialog.show();
    }
}