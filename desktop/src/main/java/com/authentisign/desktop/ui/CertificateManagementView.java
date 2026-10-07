package com.authentisign.desktop.ui;

import com.authentisign.desktop.client.SignerClient;
import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.model.dto.RevocationReason;
import com.authentisign.desktop.security.KeyStore.KeyAndChain;
import com.authentisign.desktop.security.KeyStore.KeyStoreService;
import com.authentisign.desktop.services.identity.SecurityQuestionsService;
import com.authentisign.desktop.services.signing.ocsp.OcspResult;
import com.authentisign.desktop.services.signing.ocsp.OcspService;
import com.authentisign.desktop.services.signing.ocsp.OcspStatus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.authentisign.desktop.ui.Styles.*;


public class CertificateManagementView {

    private final Stage stage;
    private final User currentUser;
    private final KeyStoreService keyStoreService = new KeyStoreService();
    private final SignerClient signerController = new SignerClient();
    private final OcspService ocspService = new OcspService();
    private final SecurityQuestionsService securityQuestionsService = new SecurityQuestionsService();

    private VBox validContainer;
    private VBox expiredContainer;
    private VBox revokedContainer;

    public CertificateManagementView(Stage stage, User currentUser) {
        this.stage = stage;
        this.currentUser = currentUser;
    }

    public ScrollPane build() {
        ensureBcProvider();

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");

        VBox header = new VBox(4);
        Label headerHint = subTitle(
                "לכל תעודה סיסמה נפרדת. הזן/י את הסיסמה כדי לפתוח תעודה ולצפות בפרטיה ובמצב התוקף שלה. " +
                        "כל תעודה תסווג אוטומטית לפי מצבה: תקפה, פגת-תוקף, או בוטלה."
        );
        headerHint.setWrapText(true);
        header.getChildren().addAll(
                pageTitle("התעודות שלי"),
                headerHint
        );

        validContainer = new VBox(14);
        expiredContainer = new VBox(14);
        revokedContainer = new VBox(14);

        content.getChildren().addAll(
                header,
                sectionTitle("תעודות תקפות"),
                validContainer,
                sectionTitle("תעודות שפג תוקפן"),
                expiredContainer,
                sectionTitle("תעודות שבוטלו"),
                revokedContainer
        );

        loadAliasList();
        return pageScroll(content);
    }


    //טעינת התעודות
    private void loadAliasList() {
        validContainer.getChildren().setAll(buildLoadingRow("טוען את רשימת התעודות שלך..."));
        expiredContainer.getChildren().clear();
        revokedContainer.getChildren().clear();

        new Thread(() -> {
            List<String> validAliases = listAliasesFromDisk();
            List<String> expiredAliases = listAliasesInSubfolder("expired");
            List<String> revokedAliases = listAliasesInSubfolder("revoked");

            Platform.runLater(() -> {
                // תעודות תקפות מוצגות כ"נעולות"וכאשר יפתחו עם סיסמה יחשפו הפרטים והתעודות תסווג מחדש
                if (validAliases.isEmpty()) {
                    validContainer.getChildren().setAll(buildEmptyState(emptyMessageFor(validContainer)));
                } else {
                    validContainer.getChildren().clear();
                    for (String alias : validAliases) {
                        validContainer.getChildren().add(buildLockedCard(alias));
                    }
                }

                // פג תוקף
                if (expiredAliases.isEmpty()) {
                    expiredContainer.getChildren().setAll(buildEmptyState(emptyMessageFor(expiredContainer)));
                } else {
                    expiredContainer.getChildren().clear();
                    for (String alias : expiredAliases) {
                        expiredContainer.getChildren().add(buildStatusCard(alias, "פג תוקף", C_TEXT_LIGHT, C_BORDER));
                    }
                }

                // בוטלו
                if (revokedAliases.isEmpty()) {
                    revokedContainer.getChildren().setAll(buildEmptyState(emptyMessageFor(revokedContainer)));
                } else {
                    revokedContainer.getChildren().clear();
                    for (String alias : revokedAliases) {
                        revokedContainer.getChildren().add(buildStatusCard(alias, "⛔ בוטלה", C_RED, C_RED_BG));
                    }
                }
            });
        }, "list-cert-aliases").start();
    }

    //הודעה כשאין תעודות בסטטוס מסוים
    private String emptyMessageFor(VBox container) {
        if (container == expiredContainer) return "אין תעודות שפג תוקפן.";
        if (container == revokedContainer) return "אין תעודות שבוטלו.";
        return "אין תעודות תקפות.";
    }

    //רשימת התעודות לפי מה שיש בתיקייה
    private List<String> listAliasesFromDisk() {
        List<String> aliases = new ArrayList<>();
        String dirPath = keyStoreService.getUserDirectory(currentUser.getEmail());
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".p12"));
        if (files != null) {
            for (File f : files) {
                aliases.add(f.getName().replaceFirst("(?i)\\.p12$", ""));
            }
        }
        return aliases;
    }

    //שמות התעודות לפי התיקייה
    private List<String> listAliasesInSubfolder(String subfolder) {
        List<String> aliases = new ArrayList<>();
        String dirPath = keyStoreService.getUserDirectory(currentUser.getEmail()) + File.separator + subfolder;
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".p12"));
        if (files != null) {
            for (File f : files) {
                aliases.add(displayAliasFromFileName(f.getName()));
            }
        }
        return aliases;
    }

    //מחזיר את שם התעודה מתוך הALIAS
    private String displayAliasFromFileName(String fileName) {
        String base = fileName.replaceFirst("(?i)\\.p12$", "");
        return base.replaceFirst("\\.\\d{10,}$", "");
    }

    private VBox buildEmptyState(String text) {
        VBox box = whiteCard(10);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        box.getChildren().add(lbl);
        return box;
    }

    private HBox buildLoadingRow(String text) {
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(20, 20);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 12.5;");
        HBox row = new HBox(10, pi, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }


    private VBox buildLockedCard(String alias) {
        VBox card = new VBox(6);
        card.setId("cert-card-" + alias);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setStyle("-fx-background-color: " + C_BG + "; -fx-background-radius: 8;");

        HBox lockedRow = new HBox(8);
        lockedRow.setAlignment(Pos.CENTER_LEFT);

        Label aliasLbl = new Label("🔒  " + alias);
        aliasLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("סיסמת התעודה");
        passField.setPrefWidth(150);

        Button unlockBtn = secondaryButton("פתח");

        lockedRow.getChildren().addAll(aliasLbl, spacer(), passField, unlockBtn);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: " + C_RED + "; -fx-font-size: 11;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        Runnable attemptUnlock = () -> {
            String pwd = passField.getText();
            if (pwd == null || pwd.isEmpty()) {
                showError(errorLbl, "יש להזין סיסמה");
                return;
            }
            hideError(errorLbl);
            unlockCertificate(alias, pwd.toCharArray(), card, unlockBtn, errorLbl);
        };
        unlockBtn.setOnAction(e -> attemptUnlock.run());
        passField.setOnAction(e -> attemptUnlock.run()); // Enter בשדה הסיסמה פותח את התעודה

        card.getChildren().setAll(lockedRow, errorLbl);
        return card;
    }

    //הצגת תעודות עם שמם וסיווג
    private VBox buildStatusCard(String alias, String statusText, String statusColor, String statusBg) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(
                "-fx-background-color: " + C_WHITE + "; -fx-background-radius: 10;" +
                        "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 10; -fx-border-width: 1;"
        );

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label aliasLbl = new Label(alias);
        aliasLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");

        Label badge = pill(statusText, statusBg, statusColor);
        row.getChildren().addAll(aliasLbl, spacer(), badge);

        card.getChildren().add(row);
        return card;
    }

    private void unlockCertificate(String alias, char[] password, VBox card, Button unlockBtn, Label errorLbl) {
        unlockBtn.setDisable(true);

        new Thread(() -> {
            try {
                //וידוא לפני שימוש בgetInstanxe
                ensureBcProvider();

                String path = keyStoreService.getPathForAlias(currentUser.getEmail(), alias);
                //משתמשים בפונקציה זו כדי לקבל את תעודת המנפיק
                KeyAndChain keyAndChain = keyStoreService.getKeyAndChain(path, password, alias);
                X509Certificate cert = (X509Certificate) keyAndChain.chain()[0];
                X509Certificate issuer = keyAndChain.chain().length > 1
                        ? (X509Certificate) keyAndChain.chain()[1]
                        : cert;

                //בדיקת OCSP
                OcspResult ocspResult = ocspService.check(cert, issuer);

                Platform.runLater(() -> renderUnlockedCard(card, alias, cert, ocspResult));
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    unlockBtn.setDisable(false);
                    showError(errorLbl, "סיסמה שגויה עבור תעודה זו, או שגיאה בפתיחתה");
                });
            }
        }, "unlock-cert-" + alias).start();
    }


    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private void renderUnlockedCard(VBox card, String alias, X509Certificate cert, OcspResult ocspResult) {
        boolean expired = isExpired(cert);

        String statusText;
        String statusColor;
        String statusBg;
        String statusTooltip;
        boolean invalid;
        boolean canRevoke;

        if (ocspResult.isRevoked()) {
            String when = ocspResult.revocationTime() != null ? " (" + DATE_TIME_FORMAT.format(ocspResult.revocationTime()) + ")" : "";
            statusText = "⛔ בוטלה ע\"י ה-CA" + when;
            statusColor = C_RED;
            statusBg = C_RED_BG;
            statusTooltip = "התעודה בוטלה על ידי רשות האישורים (CA) ואינה תקפה עוד. לא ניתן להשתמש בה לחתימה.";
            invalid = true;
            canRevoke = false;
        } else if (expired) {
            statusText = "פג תוקף";
            statusColor = C_TEXT_LIGHT;
            statusBg = C_BORDER;
            statusTooltip = "תוקף התעודה פג. לא ניתן להשתמש בה לחתימה - יש להנפיק תעודה חדשה.";
            invalid = true;
            canRevoke = false;
        } else if (ocspResult.status() == OcspStatus.UNKNOWN) {
            statusText = "❓ לא מוכרת ל-CA";
            statusColor = C_AMBER;
            statusBg = C_AMBER_BG;
            statusTooltip = "רשות האישורים אינה מזהה את התעודה הזו - ייתכן שלא הונפקה על ידה או שהמידע אינו זמין. " +
                    "מומלץ לא להסתמך עליה לחתימה.";
            invalid = true;
            canRevoke = false;
        } else if (ocspResult.status() == OcspStatus.RESPONDER_UNAVAILABLE) {
            statusText = "⚠️ פעילה (לא ניתן לאמת מול ה-CA כרגע)";
            statusColor = C_AMBER;
            statusBg = C_AMBER_BG;
            statusTooltip = "לא הצלחנו ליצור קשר עם רשות האישורים (CA) כדי לוודא שהתעודה לא בוטלה. " +
                    "התעודה תקינה מבחינת התוקף המקומי, אך ייתכן שהמידע על ביטול אינו מעודכן. נסה שוב מאוחר יותר.";
            invalid = false;
            canRevoke = true;
        } else {
            statusText = "✔ פעילה";
            statusColor = C_GREEN;
            statusBg = C_GREEN_BG;
            statusTooltip = "התעודה תקפה ואומתה מול רשות האישורים (CA). ניתן להשתמש בה לחתימה.";
            invalid = false;
            canRevoke = true;
        }

        //הרחבת השורה של התעודה לפרטים וכפתור ביטול
        card.setSpacing(12);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle(
                "-fx-background-color: " + C_WHITE + "; -fx-background-radius: 10;" +
                        "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 10; -fx-border-width: 1;"
        );

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label aliasLbl = new Label("🔓  " + alias);
        aliasLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 14; -fx-font-weight: bold;");
        Label subjectLbl = new Label(extractCommonName(cert));
        subjectLbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 12;");
        titleBox.getChildren().addAll(aliasLbl, subjectLbl);

        Label statusBadge = pill(statusText, statusBg, statusColor);
        Tooltip statusTip = new Tooltip(statusTooltip);
        statusTip.setWrapText(true);
        statusTip.setMaxWidth(280);
        Tooltip.install(statusBadge, statusTip);
        topRow.getChildren().addAll(titleBox, spacer(), statusBadge);

        Label serialLbl = new Label("מספר סידורי: " + cert.getSerialNumber().toString());
        serialLbl.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 11;");

        Label validityLbl = new Label(
                "תוקף: " + DATE_FORMAT.format(cert.getNotBefore()) + " – " + DATE_FORMAT.format(cert.getNotAfter())
        );
        validityLbl.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 11;");

        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button revokeBtn = dangerButton("🚫  בטל תעודה");
        revokeBtn.setDisable(!canRevoke);
        revokeBtn.setOnAction(e -> openRevokeDialog(alias, cert, card, revokeBtn));
        actionsRow.getChildren().add(revokeBtn);

        card.getChildren().setAll(topRow, serialLbl, validityLbl, actionsRow);
        //העברת התעודה למקום סיווג הנכון
        if (ocspResult.isRevoked()) {
            moveCardToSection(card, revokedContainer);
            persistLocalMove(alias, "revoked");
        } else if (expired) {
            moveCardToSection(card, expiredContainer);
            persistLocalMove(alias, "expired");
        }
    }

    //מעביר את התעודה בין התיקיות המסווגות
    private void persistLocalMove(String alias, String subfolder) {
        new Thread(() -> {
            if ("revoked".equals(subfolder)) {
                keyStoreService.revokeLocalCertificate(currentUser.getEmail(), alias);
            } else {
                keyStoreService.moveToExpiredLocally(currentUser.getEmail(), alias);
            }
        }, "persist-move-" + alias).start();
    }

    private boolean isExpired(X509Certificate cert) {
        try {
            cert.checkValidity();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private String extractCommonName(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        Matcher m = CN_PATTERN.matcher(dn);
        return m.find() ? m.group(1).trim() : dn;
    }


    //מעביר את הכרטיס בין סיווגים שונים
    private void moveCardToSection(VBox card, VBox targetContainer) {
        if (card.getParent() == targetContainer) return;

        if (card.getParent() instanceof VBox parent) {
            parent.getChildren().remove(card);
            if (parent.getChildren().isEmpty()) {
                parent.getChildren().add(buildEmptyState(emptyMessageFor(parent)));
            }
        }

        clearEmptyPlaceholderCards(targetContainer);
        targetContainer.getChildren().add(card);
    }

    private void clearEmptyPlaceholderCards(VBox container) {
        container.getChildren().removeIf(node ->
                node instanceof VBox vb && vb.getChildren().size() == 1
                        && vb.getChildren().get(0) instanceof Label lbl
                        && (lbl.getText().startsWith("אין ") || lbl.getText().startsWith("לא נמצאו"))
        );
    }


    //פתיחת דיאלוג ביטול
    private void openRevokeDialog(String alias, X509Certificate cert, VBox card, Button revokeBtn) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("ביטול תעודה");
        dialog.setResizable(false);

        StackPane contentHolder = new StackPane();
        contentHolder.setPadding(new Insets(24));
        contentHolder.setPrefWidth(440);
        contentHolder.setStyle("-fx-background-color: " + C_WHITE + ";");
        contentHolder.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        contentHolder.getChildren().setAll(
                buildRevokeDetailsStep(alias, cert, card, revokeBtn, dialog, contentHolder));

        dialog.setScene(new Scene(contentHolder));
        dialog.showAndWait();
    }

    //פירוט סיבת הביטול
    private Node buildRevokeDetailsStep(String alias, X509Certificate cert, VBox card, Button revokeBtn,
                                        Stage dialog, StackPane holder) {
        VBox root = new VBox(16);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        Label title = pageTitle("ביטול תעודה");
        Label subtitle = subTitle(alias + "  ·  מס' סידורי " + cert.getSerialNumber());

        Label warning = new Label(
                "⚠️ פעולה זו בלתי הפיכה. לאחר הביטול לא ניתן יהיה להשתמש בתעודה זו לחתימה, " +
                        "וכל שימוש עתידי בה ייחשב לא תקין."
        );
        warning.setWrapText(true);
        warning.setStyle(
                "-fx-text-fill: " + C_RED + "; -fx-font-size: 12; -fx-background-color: " + C_RED_BG + ";" +
                        "-fx-background-radius: 8; -fx-padding: 10;"
        );

        Label reasonLbl = sectionTitle("סיבת הביטול");
        ComboBox<RevocationReason> reasonBox = new ComboBox<>();
        reasonBox.getItems().addAll(RevocationReason.values());
        reasonBox.setValue(RevocationReason.UNSPECIFIED);
        reasonBox.setMaxWidth(Double.MAX_VALUE);

        TextArea detailsArea = new TextArea();
        detailsArea.setPromptText("פרטים נוספים (לא חובה)");
        detailsArea.setPrefRowCount(3);
        detailsArea.setWrapText(true);

        Label passLbl = sectionTitle("אימות זהות");
        PasswordField passField = new PasswordField();
        passField.setPromptText("הזן שוב את סיסמת התעודה \"" + alias + "\" לאישור");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: " + C_RED + "; -fx-font-size: 11.5;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        Button cancelBtn = secondaryButton("ביטול");
        Button confirmBtn = dangerButton("המשך לאימות אבטחה");

        cancelBtn.setOnAction(e -> dialog.close());

        confirmBtn.setOnAction(e -> {
            String pwd = passField.getText();
            if (pwd == null || pwd.isEmpty()) {
                showError(errorLbl, "יש להזין את הסיסמה כדי להמשיך");
                return;
            }
            hideError(errorLbl);
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);

            char[] password = pwd.toCharArray();
            new Thread(() -> {
                ensureBcProvider();
                boolean passwordValid;
                try {
                    //בדיקה שהסיסמה שהוקשה שייכת לתעודה הזאת
                    String path = keyStoreService.getPathForAlias(currentUser.getEmail(), alias);
                    keyStoreService.getKeyAndChain(path, password, alias);
                    passwordValid = true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    passwordValid = false;
                }

                boolean valid = passwordValid;
                Platform.runLater(() -> {
                    if (!valid) {
                        showError(errorLbl, "סיסמה שגויה - נסה שוב");
                        confirmBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                        return;
                    }
                    //אם הסיסמה נכונה עוברים לאימות שאלות האבטחה לפני הביטול בפועל
                    holder.getChildren().setAll(buildRevokeQuestionsStep(
                            alias, cert, card, revokeBtn, dialog, holder,
                            reasonBox.getValue(), detailsArea.getText()));
                });
            }, "revoke-verify-pass").start();
        });

        HBox buttonsRow = new HBox(10, spacer(), confirmBtn, cancelBtn);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                title, subtitle, warning,
                reasonLbl, reasonBox, detailsArea,
                passLbl, passField, errorLbl,
                buttonsRow
        );
        return root;
    }

    //הצגת שאלות האבטחה שמהשתמש בחר לפני הביטול
    private Node buildRevokeQuestionsStep(String alias, X509Certificate cert, VBox card, Button revokeBtn,
                                          Stage dialog, StackPane holder,
                                          RevocationReason reason, String details) {
        VBox root = new VBox(16);
        root.setPrefWidth(440);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        Label loading = subTitle("טוען שאלות אבטחה...");
        root.getChildren().add(loading);

        String email = currentUser.getEmail();
        new Thread(() -> {
            List<String> questions = securityQuestionsService.fetchUserQuestions(email);
            Platform.runLater(() -> {
                if (questions == null || questions.isEmpty()) {
                    loading.setText("לא נמצאו שאלות אבטחה עבור החשבון. לא ניתן להשלים את הביטול כעת.");
                    Button close = secondaryButton("סגור");
                    close.setOnAction(e -> dialog.close());
                    HBox row = new HBox(close);
                    row.setAlignment(Pos.CENTER_LEFT);
                    root.getChildren().setAll(loading, row);
                    return;
                }

                Label errorLbl = new Label();
                errorLbl.setStyle("-fx-text-fill: " + C_RED + "; -fx-font-size: 11.5;");
                errorLbl.setVisible(false);
                errorLbl.setManaged(false);

                Region questionsView = new SecurityQuestionsView(
                        SecurityQuestionsView.Mode.ANSWER, questions,
                        answers -> verifyQuestionsThenRevoke(answers, alias, cert, card, revokeBtn,
                                dialog, holder, reason, details, errorLbl))
                        .withHeader("אימות שאלות אבטחה",
                                "לפני ביטול התעודה, ענה על שאלות האבטחה שהגדרת.")
                        .withSubmitText("אמת ובטל את התעודה")
                        .build();

                root.getChildren().setAll(questionsView, errorLbl);
            });
        }, "revoke-load-questions").start();

        return root;
    }

    //אימות תשובות האבטחה
    private void verifyQuestionsThenRevoke(Map<String, String> answers, String alias, X509Certificate cert,
                                           VBox card, Button revokeBtn, Stage dialog, StackPane holder,
                                           RevocationReason reason, String details, Label errorLbl) {
        hideError(errorLbl);
        String email = currentUser.getEmail();
        new Thread(() -> {
            boolean answersValid = securityQuestionsService.verifyAnswers(email, answers);
            if (!answersValid) {
                Platform.runLater(() ->
                        showError(errorLbl, "תשובות האבטחה שגויות - הביטול לא בוצע"));
                return;
            }

            boolean revoked = signerController.revokeCertificate(
                    email, cert.getSerialNumber().toString(), reason.name(), details);

            //הביטול המקומיי מתבצע רק לאחר שהשרת אישר את הביטול
            boolean locallyRevoked = revoked && keyStoreService.revokeLocalCertificate(email, alias);

            Platform.runLater(() -> {
                if (revoked) {
                    revokeBtn.setDisable(true);
                    revokeBtn.setText("✅ בוטלה");
                    //מעביר את התעודה לתעודות שבוטלו
                    moveCardToSection(card, revokedContainer);
                    holder.getChildren().setAll(buildRevokeSuccessStep(dialog, locallyRevoked));
                } else {
                    showError(errorLbl, "אירעה שגיאה בתקשורת מול שרת ה-CA. נסה שוב מאוחר יותר.");
                }
            });
        }, "revoke-verify-answers").start();
    }

    //ביטטול התעודה
    private Node buildRevokeSuccessStep(Stage dialog, boolean locallyRevoked) {
        VBox root = new VBox(16);
        root.setPrefWidth(440);
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        Label icon = new Label("✅");
        icon.setStyle("-fx-font-size: 34;");

        Label title = pageTitle("התעודה בוטלה");

        String msg = locallyRevoked
                ? "התעודה בוטלה בהצלחה. לא ניתן יהיה להשתמש בה עוד לחתימה."
                : "התעודה בוטלה בשרת ה-CA, אך לא הצלחנו להסיר את העותק המקומי. " +
                "מומלץ להסירו ידנית כדי למנוע שימוש בו.";
        Label body = new Label(msg);
        body.setWrapText(true);
        body.setMaxWidth(392);
        body.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13;");

        Button closeBtn = primaryButton("סגור");
        closeBtn.setOnAction(e -> dialog.close());
        HBox row = new HBox(closeBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(icon, title, body, row);
        return root;
    }

    private void showError(Label errorLbl, String text) {
        errorLbl.setText(text);
        errorLbl.setVisible(true);
        errorLbl.setManaged(true);
    }

    private void hideError(Label errorLbl) {
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);
    }

    private void ensureBcProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}