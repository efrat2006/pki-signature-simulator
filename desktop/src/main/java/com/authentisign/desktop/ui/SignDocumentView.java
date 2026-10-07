package com.authentisign.desktop.ui;

import com.authentisign.desktop.controller.SignDocumentController;
import com.authentisign.desktop.controller.SignDocumentController.SignResult;
import com.authentisign.desktop.database.entities.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.Consumer;

import static com.authentisign.desktop.ui.Styles.*;


public class SignDocumentView {

    private final Stage stage;
    private final Runnable onIssueCertificateRequested;
    private final SignDocumentController controller;

    public SignDocumentView(Stage stage, User currentUser, Runnable onIssueCertificateRequested) {
        this.stage = stage;
        this.onIssueCertificateRequested = onIssueCertificateRequested;
        this.controller = new SignDocumentController(currentUser);
    }


    public ScrollPane build() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");

        Label title = pageTitle("חתימה על מסמך PDF");

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        Button signBtn = primaryButton("✍️  חתום על המסמך");
        signBtn.setDisable(true);

        Runnable refreshSignEnabled = () -> signBtn.setDisable(!controller.isReadyToSign());

        //אזור בחירת קובץ
        VBox fileBox = buildFilePickerBox(file -> {
            controller.setSelectedFile(file);
            //בודקת אם אפשר להפעיל כבר את כפתור החתימה
            refreshSignEnabled.run();
        });

        //אזור בחירת התעודה
        VBox certCard = buildCertificateSelectionCard(alias -> {
            controller.setSelectedAlias(alias);
            refreshSignEnabled.run();
        });

        //לחיצה על הכפתור חתום
        signBtn.setOnAction(e ->
                handleSignClick(statusLabel, signBtn));

        HBox signBar = new HBox(signBtn);
        signBar.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(
                title,
                fileBox,
                sectionTitle("בחירת תעודה"),
                certCard,
                signBar,
                statusLabel
        );

        return pageScroll(content);
    }

    //בניית רשימת התעודות של המשתמש
    private VBox buildCertificateSelectionCard(Consumer<String> onAliasSelected) {
        VBox card = whiteCard(10);
        card.setMaxWidth(560);

        List<String> aliases = controller.listAvailableAliases();

        if (aliases.isEmpty()) {
            card.getChildren().add(buildNoCertificatesNotice(
                    "לא נמצאו תעודות בכספת שלך",
                    "עדיין לא הנפקת תעודה דיגיטלית. יש להנפיק תעודה חדשה כדי להמשיך."
            ));
            return card;
        }

        Label hint = subTitle("לכל תעודה יש סיסמה נפרדת משלה — הזן/י ליד התעודה הרצויה את הסיסמה שלה ולחצי/י \"פתח\".");
        hint.setWrapText(true);
        card.getChildren().add(hint);

        //יצירת קבוצה משותפת לכל הכפתורים של התעודות כדי שרק תעודה אחת תבחר בו בכל זמן
        ToggleGroup group = new ToggleGroup();
        for (String alias : aliases) {
            card.getChildren().add(buildCertificateRow(alias, group, onAliasSelected));
        }

        return card;
    }

  //יצירת שורה לכל תעודה
    private VBox buildCertificateRow(String alias, ToggleGroup group, Consumer<String> onAliasSelected) {
        VBox rowContainer = new VBox(6);
        rowContainer.setPadding(new Insets(8, 10, 8, 10));
        rowContainer.setStyle("-fx-background-color: " + C_BG + "; -fx-background-radius: 8;");

        HBox lockedRow = new HBox(8);
        lockedRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(alias);
        nameLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        PasswordField pwField = new PasswordField();
        pwField.setPromptText("סיסמת התעודה");
        pwField.setPrefWidth(140);

        Button openBtn = secondaryButton("פתח");

        lockedRow.getChildren().addAll(nameLbl, spacer, pwField, openBtn);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: " + C_RED + "; -fx-font-size: 11;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        rowContainer.getChildren().addAll(lockedRow, errorLbl);

        Runnable attemptUnlock = () -> {
            String pwText = pwField.getText();
            openBtn.setDisable(true);
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);

            //בדיקה אם התעודה תקפה
            controller.unlockCertificate(alias, pwText, result -> {
                openBtn.setDisable(false);

                if (!result.success()) {
                    errorLbl.setText(result.errorMessage());
                    errorLbl.setVisible(true);
                    errorLbl.setManaged(true);
                    return;
                }

                pwField.clear();
                renderUnlockedRow(rowContainer, alias, result.certificate(), group, onAliasSelected);
            });
        };

        openBtn.setOnAction(e -> attemptUnlock.run());
        pwField.setOnAction(e -> attemptUnlock.run());

        return rowContainer;
    }

    //בונה תווית להצגה במקרה שהתעודה נפתחת
    private void renderUnlockedRow(VBox rowContainer, String alias, X509Certificate cert,
                                   ToggleGroup group, Consumer<String> onAliasSelected) {
        RadioButton rb = new RadioButton();
        rb.setToggleGroup(group);

        VBox labelBox = new VBox(2);
        Label unlockedName = new Label(alias);
        unlockedName.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Label expiryLbl = new Label("בתוקף עד " + fmt.format(cert.getNotAfter()));
        expiryLbl.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 11;");
        labelBox.getChildren().addAll(unlockedName, expiryLbl);

        HBox unlockedRow = new HBox(10, rb, labelBox);
        unlockedRow.setAlignment(Pos.CENTER_LEFT);
        unlockedRow.setOnMouseClicked(ev -> rb.setSelected(true));

        rb.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) onAliasSelected.accept(alias);
        });

        rowContainer.getChildren().setAll(unlockedRow);
        rowContainer.setStyle("-fx-background-color: " + C_GREEN_BG + "; -fx-background-radius: 8;");

        if (group.getSelectedToggle() == null) {
            rb.setSelected(true);
        }
    }

    //הודעת אין תעודות
    private VBox buildNoCertificatesNotice(String title, String subtitle) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18));
        box.setStyle(
                "-fx-background-color: " + C_AMBER_BG + "; -fx-background-radius: 8;" +
                        "-fx-border-color: " + C_AMBER + "; -fx-border-radius: 8; -fx-border-width: 1;"
        );

        Label msg = new Label(title);
        msg.setStyle("-fx-text-fill: " + C_AMBER + "; -fx-font-size: 13; -fx-font-weight: bold;");

        Label sub = new Label(subtitle);
        sub.setWrapText(true);
        sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        sub.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 11.5;");

        Button issueBtn = primaryButton("🎫  הנפק תעודה חדשה");
        issueBtn.setOnAction(e -> {
            if (onIssueCertificateRequested != null) onIssueCertificateRequested.run();
        });

        box.getChildren().addAll(msg, sub, issueBtn);
        return box;
    }

    //ביצוע פעולת החתימה
    private void handleSignClick(Label statusLabel, Button signBtn) {
        File inputFile = controller.getSelectedFile();

        //פתיחת חלון בחירת קבצים
        FileChooser fc = new FileChooser();
        String baseName = inputFile.getName().replaceAll("(?i)\\.pdf$", "");
        fc.setInitialFileName(baseName + "_signed.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File outputFile = fc.showSaveDialog(stage);
        if (outputFile == null)
            return;

        //לא מאפשר לחיצה כפולה בזמן שהחתימה מתבצעת
        signBtn.setDisable(true);
        showStatus(statusLabel, "חותם על המסמך...", true);

        controller.sign(outputFile, (SignResult result) -> {
            signBtn.setDisable(false);
            if (result.success()) {
                showStatus(statusLabel, "✅ המסמך נחתם בהצלחה ונשמר בקובץ: " + result.outputFile().getName(), true);
            } else if (result.errorMessage() != null) {
                showStatus(statusLabel, "❌ שגיאה בחתימה על המסמך: " + result.errorMessage(), false);
            }
        });
    }

    //הצגת סטטוס החתימה
    private void showStatus(Label label, String text, boolean ok) {
        label.setText(text);
        label.setStyle("-fx-text-fill: " + (ok ? C_GREEN : C_RED) + "; -fx-font-size: 12.5; -fx-font-weight: bold;");
        label.setVisible(true);
        label.setManaged(true);
    }

    //אזור בחירת קובץ
    private VBox buildFilePickerBox(Consumer<File> onFileSelected) {
        VBox zone = new VBox(12);
        zone.setAlignment(Pos.CENTER);
        zone.setPadding(new Insets(36));
        zone.setMinWidth(520); zone.setMaxWidth(520);
        zone.setMinHeight(340); zone.setMaxHeight(340);
        String baseStyle =
                "-fx-background-color: " + C_WHITE + "; -fx-background-radius: 10;" +
                        "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 10;" +
                        "-fx-border-width: 2; -fx-border-style: dashed; -fx-cursor: hand;";
        zone.setStyle(baseStyle);

        Label pdfIcon = new Label("📄");
        pdfIcon.setStyle("-fx-font-size: 40;");
        Label title = new Label("בחר קובץ PDF");
        title.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 14; -fx-font-weight: bold;");
        Label sub = new Label("לחץ על הכפתור לבחירת קובץ מהמחשב");
        sub.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 12;");

        Button chooseBtn = primaryButton("בחר קובץ PDF");
        chooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showOpenDialog(stage);
            if (file != null) {
                try { displayPdfAsImage(zone, file); } catch (Exception ex) { ex.printStackTrace(); }
                if (onFileSelected != null) onFileSelected.accept(file);
            }
        });
        zone.getChildren().addAll(pdfIcon, title, sub, chooseBtn);
        return zone;
    }

    //הופך את PDF לתמונות ומציג אותן
    private void displayPdfAsImage(VBox zone, File file) {
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(50, 50);
        Label loading = new Label("טוען את דפי המסמך...");
        loading.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 14;");
        VBox loadingView = new VBox(15, pi, loading);
        loadingView.setAlignment(Pos.CENTER);
        zone.getChildren().setAll(loadingView);

        Thread t = new Thread(() -> {
            PDDocument doc = null;
            try {
                doc = Loader.loadPDF(file);
                PDFRenderer renderer = new PDFRenderer(doc);
                int pages = doc.getNumberOfPages();
                VBox pagesBox = new VBox(15);
                pagesBox.setAlignment(Pos.TOP_CENTER);
                pagesBox.setStyle("-fx-background-color: #f0f2f5; -fx-padding: 15;");

                for (int i = 0; i < pages; i++) {
                    BufferedImage bim = renderer.renderImageWithDPI(i, 150);
                    javafx.scene.image.Image img = javafx.embed.swing.SwingFXUtils.toFXImage(bim, null);
                    javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
                    view.setPreserveRatio(true);
                    view.setFitWidth(zone.getMaxWidth() - 50);
                    Platform.runLater(() -> pagesBox.getChildren().add(view));
                }
                doc.close();

                Platform.runLater(() -> {
                    ScrollPane sp = new ScrollPane(pagesBox);
                    sp.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
                    sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    sp.setFitToWidth(true);
                    sp.setPannable(true);
                    sp.setPrefViewportHeight(zone.getMaxHeight() - 10);
                    sp.setStyle("-fx-background: #f0f2f5; -fx-background-color: transparent; -fx-border-color: transparent;");
                    zone.getChildren().setAll(sp);
                    zone.setStyle(
                            "-fx-background-color: white; -fx-border-color: " + C_BORDER +
                                    "; -fx-border-radius: 10; -fx-border-width: 1; -fx-border-style: solid;"
                    );
                });
            } catch (Exception ex) {
                if (doc != null) { try { doc.close(); } catch (Exception ignored) {} }
                Platform.runLater(() ->
                        zone.getChildren().setAll(new Label("שגיאה בטעינת הקובץ")));
            }
        });
        t.setDaemon(true);
        t.start();
    }
}