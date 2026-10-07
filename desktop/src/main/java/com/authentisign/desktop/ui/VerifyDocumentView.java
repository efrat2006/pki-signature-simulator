package com.authentisign.desktop.ui;

import com.authentisign.desktop.controller.VerifyDocumentController;
import com.authentisign.desktop.services.signing.pdf.PdfSignatureVerifier.SignerCheck;
import com.authentisign.desktop.services.signing.pdf.PdfSignatureVerifier.VerificationOutcome;
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
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import static com.authentisign.desktop.ui.Styles.*;


public class VerifyDocumentView {

    private final Stage stage;
    private final VerifyDocumentController controller;

    public VerifyDocumentView(Stage stage) {
        this.stage = stage;
        this.controller = new VerifyDocumentController();
    }

    public ScrollPane build() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");

        Label title = pageTitle("אימות חתימה על מסמך PDF");

        File[] selectedFile = new File[1];
        VBox resultBox = new VBox();

        Button verifyBtn = primaryButton("🔍  אמת חתימה");
        verifyBtn.setDisable(true);

        //אזור בחירת קובץ
        VBox fileBox = buildFilePickerBox(file -> {
            selectedFile[0] = file;
            verifyBtn.setDisable(false);
            resultBox.getChildren().clear();
        });

        //כפתור אימות
        verifyBtn.setOnAction(e -> {
            if (selectedFile[0] == null) return;
            handleVerifyClick(selectedFile[0], resultBox);
        });

        HBox verifyBar = new HBox(verifyBtn);
        verifyBar.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(title, fileBox, verifyBar, resultBox);

        return pageScroll(content);
    }

    //פעולת כפתור אימות
    private void handleVerifyClick(File file, VBox resultBox) {
        resultBox.getChildren().setAll(buildLoadingRow("מאמת את החתימה על המסמך..."));
        controller.verify(file, outcome ->
                resultBox.getChildren().setAll(buildVerificationResultPanel(outcome)));
    }

    //הצגת תוצאת האימות
    private VBox buildVerificationResultPanel(VerificationOutcome result) {
        VBox box = whiteCard(10);
        box.setMaxWidth(600);

        String badgeText;
        String badgeColor;
        String badgeBg;
        switch (result.status()) {
            case VALID -> { badgeText = "✅  החתימה תקינה"; badgeColor = C_GREEN; badgeBg = C_GREEN_BG; }
            case INVALID -> { badgeText = "❌  החתימה אינה תקינה"; badgeColor = C_RED; badgeBg = C_RED_BG; }
            case NO_SIGNATURE -> { badgeText = "⚠️  לא נמצאה חתימה במסמך"; badgeColor = C_AMBER; badgeBg = C_AMBER_BG; }
            default -> { badgeText = "⚠️  שגיאה באימות המסמך"; badgeColor = C_RED; badgeBg = C_RED_BG; }
        }

        Label badge = new Label(badgeText);
        badge.setStyle(
                "-fx-text-fill: " + badgeColor + "; -fx-font-size: 16; -fx-font-weight: bold;" +
                        "-fx-background-color: " + badgeBg + "; -fx-background-radius: 8; -fx-padding: 10 14;"
        );
        box.getChildren().add(badge);

        if (result.generalMessage() != null) {
            Label general = new Label(result.generalMessage());
            general.setWrapText(true);
            general.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 12.5;");
            box.getChildren().add(general);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (SignerCheck check : result.signerChecks()) {
            VBox row = new VBox(4);
            row.setPadding(new Insets(10));
            row.setStyle(
                    "-fx-background-color: " + (check.fullyValid() ? C_GREEN_BG : C_RED_BG) + "; -fx-background-radius: 8;"
            );

            Label signerLbl = new Label("חותם: " + check.signerName());
            signerLbl.setWrapText(true);
            signerLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 12.5; -fx-font-weight: bold;");
            row.getChildren().add(signerLbl);

            if (check.signDate() != null) {
                Label dateLbl = new Label("נחתם בתאריך: " + check.signDate().format(dtf));
                dateLbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 11;");
                row.getChildren().add(dateLbl);
            }

            if (check.fullyValid()) {
                Label ok = new Label("החתימה תקינה - תוכן המסמך לא שונה מאז החתימה, והתעודה של החותם תקפה.");
                ok.setWrapText(true);
                ok.setStyle("-fx-text-fill: " + C_GREEN + "; -fx-font-size: 11.5;");
                row.getChildren().add(ok);
            } else {
                Label why = new Label("הסיבה: " + check.reason());
                why.setWrapText(true);
                why.setStyle("-fx-text-fill: " + C_RED + "; -fx-font-size: 11.5;");
                row.getChildren().add(why);
            }

            box.getChildren().add(row);
        }

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

    //טעינת המסמך
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

    //הופך את PDF לתמונות ומציגה אותן שיהיה אפשר לראות על המסך
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