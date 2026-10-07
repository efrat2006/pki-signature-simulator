package com.authentisign.desktop.ui;

import com.authentisign.desktop.client.AuthClient;
import com.authentisign.desktop.client.SignerClient;
import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.services.identity.SecurityQuestionsService;
import com.authentisign.desktop.services.identity.UserService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;


public class Dashboard {

    private final Stage stage;
    private final User currentUser;
    private final UserService userService;
    private final DigSignApp mainApp;
    private final SignerClient signerController;

    static final String C_PRIMARY        = "#1e3a5f";
    static final String C_PRIMARY_DARK   = "#162d4a";
    static final String C_BG             = "#f5f6fa";
    static final String C_WHITE          = "#ffffff";
    static final String C_BORDER         = "#e2e6ed";
    static final String C_TEXT_MAIN      = "#1e3a5f";
    static final String C_TEXT_MUTED     = "#718096";
    static final String C_TEXT_LIGHT     = "#a0aec0";
    static final String C_GREEN          = "#38a169";
    static final String C_GREEN_BG       = "#f0faf4";
    static final String C_GREEN_BORDER   = "#c6f6d5";
    static final String C_BLUE_BG        = "#ebf0f8";
    static final String C_BLUE           = "#3182ce";
    static final String C_SIDEBAR_HOVER  = "#f0f4f8";
    static final String C_SIDEBAR_ACTIVE = "#ebf0f8";
    static final String C_AMBER_BG       = "#fffbeb";
    static final String C_AMBER          = "#d97706";

    private HBox activeSidebarItem = null;
    private BorderPane root;

    private final SecurityQuestionsService securityQuestionsService = new SecurityQuestionsService();
    private FaceRecognitionView faceRecognitionView;

    public Dashboard(Stage stage, User user, UserService userService, DigSignApp mainApp) {
        this.stage            = stage;
        this.currentUser      = user;
        this.userService      = userService;
        this.mainApp          = mainApp;
        this.signerController = new SignerClient();
    }

    public void show() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");
        root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        root.setTop(buildTitleBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildHomeContent());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1000, 680);
        stage.setTitle("DigiSign");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    private VBox buildTitleBar() {
        VBox wrapper = new VBox();

        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: " + C_PRIMARY + ";");
        titleBar.setPadding(new Insets(0, 16, 0, 16));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPrefHeight(44);

        HBox logoArea = new HBox(10);
        logoArea.setAlignment(Pos.CENTER_LEFT);

        Label logoIcon = new Label("🔐");
        logoIcon.setStyle(
                "-fx-background-color: rgba(255,255,255,0.15);" +
                        "-fx-background-radius: 6; -fx-padding: 4 6; -fx-font-size: 14;"
        );

        VBox titleTexts = new VBox(1);
        Label appName = new Label("DigiSign");
        appName.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
        Label appSub  = new Label("סימולטור חתימה דיגיטלית");
        appSub.setStyle("-fx-text-fill: rgba(255,255,255,0.45); -fx-font-size: 10;");
        titleTexts.getChildren().addAll(appName, appSub);
        logoArea.getChildren().addAll(logoIcon, titleTexts);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(logoArea, spacer);

        wrapper.getChildren().addAll(titleBar, buildToolbar());
        return wrapper;
    }

    private Circle makeWindowDot(String color) {
        Circle c = new Circle(6);
        c.setFill(Color.web(color));
        c.setStyle("-fx-cursor: hand;");
        return c;
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(6);
        toolbar.setStyle("-fx-background-color: " + C_WHITE + "; -fx-border-width: 0 0 1 0; -fx-border-color: " + C_BORDER + ";");
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button btnIssueCert = makePrimaryButton("🎫  הנפק תעודה");
        btnIssueCert.setOnAction(e -> handleIssueCertificate());

        Button btnOpenPdf  = makeSecondaryButton("📄  פתח PDF");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(btnIssueCert, btnOpenPdf, spacer);
        return toolbar;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.setStyle(
                "-fx-background-color: " + C_WHITE + ";" +
                        "-fx-border-color: transparent " + C_BORDER + " transparent transparent;" +
                        "-fx-border-width: 0 1 0 0; -fx-pref-width: 210;"
        );
        sidebar.setPadding(new Insets(16, 10, 16, 10));

        HBox userChip = new HBox(10);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.setPadding(new Insets(8, 10, 14, 10));

        Label avatar = new Label(initials(currentUser.getName()));
        avatar.setStyle(
                "-fx-background-color: " + C_BLUE_BG + "; -fx-text-fill: " + C_PRIMARY + ";" +
                        "-fx-font-size: 12; -fx-font-weight: bold; -fx-background-radius: 50;" +
                        "-fx-min-width: 32; -fx-min-height: 32; -fx-alignment: center;"
        );
        VBox userInfo = new VBox(1);
        Label nameLabel = new Label(currentUser.getName());
        nameLabel.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 12; -fx-font-weight: bold;");
        Label roleLabel = new Label("משתמש רשום");
        roleLabel.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 10;");
        userInfo.getChildren().addAll(nameLabel, roleLabel);
        userChip.getChildren().addAll(avatar, userInfo);

        Separator chipSep = new Separator();
        chipSep.setPadding(new Insets(0, 0, 6, 0));
        sidebar.getChildren().addAll(userChip, chipSep);

        sidebar.getChildren().add(makeSidebarSection("ראשי"));

        HBox homeItem = makeSidebarItem("🏠", "לוח בקרה");
        homeItem.setOnMouseClicked(e -> {
            setActive(homeItem);
            root.setCenter(buildHomeContent());
        });
        setActive(homeItem);
        sidebar.getChildren().add(homeItem);

        HBox signItem = makeSidebarItem("✍️", "חתימה על מסמך");
        signItem.setOnMouseClicked(e -> {
            setActive(signItem);
            root.setCenter(new SignDocumentView(stage, currentUser, this::handleIssueCertificate).build());
        });
        sidebar.getChildren().add(signItem);

        HBox verifyItem = makeSidebarItem("🛡️", "אימות מסמך");
        verifyItem.setOnMouseClicked(e -> {
            setActive(verifyItem);
            root.setCenter(new VerifyDocumentView(stage).build());
        });
        sidebar.getChildren().add(verifyItem);

        sidebar.getChildren().add(makeSidebarSection("תעודות"));

        HBox issueCertItem = makeSidebarItem("🎫", "הנפקת תעודה");
        issueCertItem.setOnMouseClicked(e -> {
            setActive(issueCertItem);
            handleIssueCertificate();
        });
        sidebar.getChildren().add(issueCertItem);

        HBox myCertsItem = makeSidebarItem("📋", "התעודות שלי");
        myCertsItem.setOnMouseClicked(e -> {
            setActive(myCertsItem);
            root.setCenter(new CertificateManagementView(stage, currentUser).build());
        });
        sidebar.getChildren().add(myCertsItem);

        sidebar.getChildren().add(makeSidebarSection("חשבון"));

        HBox profileItem = makeSidebarItem("👤", "פרטים אישיים");
        profileItem.setOnMouseClicked(e -> {
            setActive(profileItem);
            PersonalDetailsView personalDetailsView = new PersonalDetailsView(currentUser);
            root.setCenter(personalDetailsView.build());
        });
        sidebar.getChildren().add(profileItem);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        HBox logoutItem = makeSidebarItem("🚪", "התנתק");
        logoutItem.setOnMouseClicked(e -> handleLogout());
        sidebar.getChildren().add(logoutItem);

        return sidebar;
    }

    private ScrollPane buildHomeContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");

        VBox greeting = new VBox(3);
        Label hello = new Label("שלום, " + currentUser.getName());
        hello.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 20; -fx-font-weight: bold;");
        Label sub = new Label("ברוך הבא למערכת DigiSign");
        sub.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        greeting.getChildren().addAll(hello, sub);
        content.getChildren().add(greeting);


        content.getChildren().add(makeSectionTitle("פעולות מהירות"));
        HBox cards = new HBox(14);

        VBox certsCard = makeActionCard("📋", "התעודות שלי",
                "צפה בכל התעודות הדיגיטליות הפעילות שלך", C_GREEN_BG, C_GREEN);
        certsCard.setOnMouseClicked(e -> root.setCenter(new CertificateManagementView(stage, currentUser).build()));

        VBox verifyCard = makeActionCard("🛡️", "אימות חתימה",
                "בדוק את תוקף החתימה הדיגיטלית על מסמך", C_BLUE_BG, C_BLUE);
        verifyCard.setOnMouseClicked(e -> root.setCenter(new VerifyDocumentView(stage).build()));

        VBox signCard = makeActionCard("✍️", "חתימה על מסמך",
                "טען קובץ PDF וחתום עליו דיגיטלית", C_AMBER_BG, C_AMBER);
        signCard.setOnMouseClicked(e -> root.setCenter(new SignDocumentView(stage, currentUser, this::handleIssueCertificate).build()));

        cards.getChildren().addAll(certsCard, verifyCard, signCard);
        content.getChildren().add(cards);

        content.getChildren().add(buildRevocationNotice());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + ";");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }


    private HBox buildRevocationNotice() {
        HBox notice = new HBox(10);
        notice.setAlignment(Pos.TOP_RIGHT);
        notice.setPadding(new Insets(14, 16, 14, 16));
        notice.setStyle(
                "-fx-background-color: " + C_AMBER_BG + "; -fx-background-radius: 10;"
        );

        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 14;");

        Label text = new Label(
                "שימו לב: על פי תקנות חתימה אלקטרונית, המחזיק בתעודה אחראי לדווח על אובדן " +
                        "או חשיפה של אמצעי החתימה ולבקש את ביטולה במיידי."
        );
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: " + C_AMBER + "; -fx-font-size: 12.5;");
        HBox.setHgrow(text, Priority.ALWAYS);

        notice.getChildren().addAll(icon, text);
        return notice;
    }


    private void handleIssueCertificate() {
        //אם הטוקן פג
        if (!AuthClient.isTokenValid() && !promptReloginAndRefreshToken()) {
            return;
        }

        String email = currentUser.getEmail();

        VBox loadingView = new VBox(12);
        loadingView.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(40, 40);
        Label loadingLabel = new Label("בודק חשבון CA...");
        loadingLabel.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        loadingView.getChildren().addAll(pi, loadingLabel);
        root.setCenter(loadingView);

        new Thread(() -> {
            boolean hasAccount = signerController.hasAccount(email);

            Platform.runLater(() -> {
                if (hasAccount) {
                    //שאלות אבטחה למשתמש שיש לו חשבון
                    verifyQuestionsThenEnroll();
                } else {
                    faceRecognitionView = new FaceRecognitionView(
                            stage,
                            () -> {
                                showSecurityQuestionsSetup(
                                        faceRecognitionView.getFirstName(),
                                        faceRecognitionView.getLastName(),
                                        faceRecognitionView.getBirthDate(),
                                        faceRecognitionView.getIdFrontBytes(),
                                        faceRecognitionView.getIdBackBytes()
                                );
                            }
                    );
                    root.setCenter(faceRecognitionView.build());
                }
            });
        }).start();
    }

    //טעינת שאלות אבטחה שהמשתמש בחר
    private void verifyQuestionsThenEnroll() {
        String email = currentUser.getEmail();

        VBox loadingView = new VBox(12);
        loadingView.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(40, 40);
        Label loadingLabel = new Label("טוען שאלות אבטחה...");
        loadingLabel.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        loadingView.getChildren().addAll(pi, loadingLabel);
        root.setCenter(loadingView);

        new Thread(() -> {
            List<String> questions = securityQuestionsService.fetchUserQuestions(email);
            Platform.runLater(() -> {
                if (questions == null || questions.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("לא נמצאו שאלות אבטחה עבור החשבון. לא ניתן להנפיק תעודה כעת.");
                    alert.showAndWait();
                    root.setCenter(buildHomeContent());
                    return;
                }

                Region view = new SecurityQuestionsView(
                        SecurityQuestionsView.Mode.ANSWER,
                        questions,
                        this::verifyAnswersThenShowWizard
                )
                        .withHeader("אימות שאלות אבטחה",
                                "לפני הנפקת תעודה נוספת, ענה על שאלות האבטחה שהגדרת.")
                        .withSubmitText("אמת והמשך להנפקה")
                        .build();
                root.setCenter(view);
            });
        }).start();
    }

    //אימות תשובות האבטחה והעברה להנפקת תעודה
    private void verifyAnswersThenShowWizard(Map<String, String> answers) {
        String email = currentUser.getEmail();

        VBox loadingView = new VBox(12);
        loadingView.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(40, 40);
        Label loadingLabel = new Label("מאמת שאלות אבטחה...");
        loadingLabel.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        loadingView.getChildren().addAll(pi, loadingLabel);
        root.setCenter(loadingView);

        new Thread(() -> {
            boolean valid = securityQuestionsService.verifyAnswers(email, answers);
            Platform.runLater(() -> {
                root.setCenter(buildHomeContent());
                if (valid) {
                    CertificateEnrollmentWizard wizard =
                            new CertificateEnrollmentWizard(stage, currentUser.getName(), currentUser.getEmail());
                    wizard.show();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("תשובות האבטחה שגויות. לא ניתן להנפיק תעודה.");
                    alert.showAndWait();
                }
            });
        }).start();
    }

    private void showSecurityQuestionsSetup(String firstName, String lastName, String birthDate,
                                            byte[] idFront, byte[] idBack) {
        String email = currentUser.getEmail();

        VBox loadingView = new VBox(12);
        loadingView.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(40, 40);
        Label loadingLabel = new Label("טוען שאלות אבטחה...");
        loadingLabel.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        loadingView.getChildren().addAll(pi, loadingLabel);
        root.setCenter(loadingView);

        new Thread(() -> {
            List<String> allQuestions = securityQuestionsService.getAllQuestions();
            Platform.runLater(() -> {
                if(allQuestions == null || allQuestions.isEmpty()){
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("לא ניתן לטעון שאלות אבטחה מהשרת");
                    alert.showAndWait();
                    root.setCenter(buildHomeContent());
                    return;
                }

                Region view = new SecurityQuestionsView(
                        SecurityQuestionsView.Mode.SELECT,
                        allQuestions,
                        answers -> registerSignerWithQuestions(firstName, lastName, email, birthDate, idFront, idBack, answers)
                ).build();
                root.setCenter(view);
            });
        }).start();
    }

    private void registerSignerWithQuestions(String firstName, String lastName, String email,
                                             String birthDate, byte[] idFront, byte[] idBack,
                                             Map<String, String> answers) {
        VBox loadingView = new VBox(12);
        loadingView.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(40, 40);
        Label loadingLabel = new Label("יוצר חשבון וחותם...");
        loadingLabel.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        loadingView.getChildren().addAll(pi, loadingLabel);
        root.setCenter(loadingView);

        new Thread(() -> {
            boolean success = securityQuestionsService.registerWithQuestions(
                    firstName, lastName, email, birthDate, idFront, idBack, answers
            );
            Platform.runLater(() -> {
                if(success) {
                    handleIssueCertificate();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("שגיאה בהרשמה. נסה שוב.");
                    alert.showAndWait();
                    root.setCenter(buildHomeContent());
                }
            });
        }).start();
    }


    HBox makeMetricCard(String label, String value, String valueColor, String bg) {
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

    private VBox makeActionCard(String icon, String title, String desc, String iconBg, String accentColor) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setPrefWidth(240);
        String base = "-fx-background-color: " + C_WHITE + "; -fx-background-radius: 10;" +
                "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 10; -fx-border-width: 1; -fx-cursor: hand;";
        card.setStyle(base);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 8; -fx-padding: 8 10; -fx-font-size: 18;");
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");
        titleLbl.setWrapText(true);
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 11.5;");
        descLbl.setWrapText(true);
        card.getChildren().addAll(iconLbl, titleLbl, descLbl);

        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: " + C_WHITE + "; -fx-background-radius: 10;" +
                        "-fx-border-color: " + accentColor + "; -fx-border-radius: 10; -fx-border-width: 1.5; -fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(30,58,95,0.1), 12, 0, 0, 4);"
        ));
        card.setOnMouseExited(e -> card.setStyle(base));
        return card;
    }

    Label makeSectionTitle(String text) {
        Label lbl = new Label(text.toUpperCase());
        lbl.setStyle("-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 11; -fx-font-weight: bold;");
        return lbl;
    }

    Button makePrimaryButton(String text) {
        Button btn = new Button(text);
        String base  = "-fx-background-color: " + C_PRIMARY + "; -fx-text-fill: white;" +
                "-fx-font-size: 12; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;";
        String hover = "-fx-background-color: " + C_PRIMARY_DARK + "; -fx-text-fill: white;" +
                "-fx-font-size: 12; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    Button makeSecondaryButton(String text) {
        Button btn = new Button(text);
        String base  = "-fx-background-color: transparent; -fx-text-fill: " + C_TEXT_MUTED + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand; -fx-border-width: 0;";
        String hover = "-fx-background-color: " + C_SIDEBAR_HOVER + "; -fx-text-fill: " + C_TEXT_MAIN + ";" +
                "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand; -fx-border-width: 0;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    private Label makeSidebarSection(String text) {
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-text-fill: " + C_TEXT_LIGHT + "; -fx-font-size: 10;" +
                        "-fx-font-weight: bold; -fx-padding: 14 8 4 8;"
        );
        return lbl;
    }

    private HBox makeSidebarItem(String icon, String text) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(8, 10, 8, 10));
        item.setPrefWidth(190);
        item.setStyle("-fx-background-color: transparent; -fx-background-radius: 7; -fx-cursor: hand;");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 14; -fx-min-width: 20; -fx-alignment: center;");
        Label textLbl = new Label(text);
        textLbl.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        item.getChildren().addAll(iconLbl, textLbl);

        item.setOnMouseEntered(e -> {
            if (item != activeSidebarItem)
                item.setStyle("-fx-background-color: " + C_SIDEBAR_HOVER + "; -fx-background-radius: 7; -fx-cursor: hand;");
        });
        item.setOnMouseExited(e -> {
            if (item != activeSidebarItem)
                item.setStyle("-fx-background-color: transparent; -fx-background-radius: 7; -fx-cursor: hand;");
        });
        return item;
    }

    void setActive(HBox item) {
        if (activeSidebarItem != null) {
            activeSidebarItem.setStyle("-fx-background-color: transparent; -fx-background-radius: 7; -fx-cursor: hand;");
            ((Label) activeSidebarItem.getChildren().get(1))
                    .setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 13;");
        }
        activeSidebarItem = item;
        item.setStyle("-fx-background-color: " + C_SIDEBAR_ACTIVE + "; -fx-background-radius: 7; -fx-cursor: hand;");
        ((Label) item.getChildren().get(1))
                .setStyle("-fx-text-fill: " + C_TEXT_MAIN + "; -fx-font-size: 13; -fx-font-weight: bold;");
    }


    private HBox buildStatusBar() {
        HBox bar = new HBox(8);
        bar.setNodeOrientation(javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
        bar.setStyle("-fx-background-color: " + C_PRIMARY + ";");
        bar.setPadding(new Insets(6, 20, 6, 20));
        bar.setAlignment(Pos.CENTER_RIGHT);

        Label tagline = new Label("לאשר בביטחון");
        tagline.setStyle("-fx-text-fill: " + C_GREEN + "; -fx-font-size: 11; -fx-font-weight: bold;");

        Label dash = new Label("–");
        dash.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 11;");

        Label brand = new Label("DigiSign");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");

        Circle statusDot = new Circle(3);
        statusDot.setFill(Color.web(C_GREEN));

        bar.getChildren().addAll(tagline, dash, brand, statusDot);
        return bar;
    }

    private Label makeStatusItem(String text, String color) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11;");
        return lbl;
    }

    private Label makeStatusSep() {
        Label sep = new Label("|");
        sep.setStyle("-fx-text-fill: rgba(255,255,255,0.2); -fx-font-size: 11;");
        return sep;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length()));
        return "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0);
    }


    private boolean promptReloginAndRefreshToken() {
        final boolean[] success = {false};
        String email = currentUser.getEmail();

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.setTitle("התחברות מחדש");
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(22));
        root.setPrefWidth(360);
        root.setStyle("-fx-background-color: " + C_WHITE + ";");
        root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        Label title = new Label("התחברות מחדש");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: " + C_TEXT_MAIN + ";");

        Label sub = new Label("תוקף החיבור פג. הזן/י שוב את סיסמת החשבון (" + email + ") כדי להמשיך.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: " + C_TEXT_MUTED + "; -fx-font-size: 12.5;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("סיסמת החשבון");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11.5;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        Button cancelBtn = new Button("ביטול");
        Button confirmBtn = new Button("התחבר");
        confirmBtn.setStyle("-fx-background-color: " + C_PRIMARY + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");

        cancelBtn.setOnAction(e -> dialog.close());

        Runnable attempt = () -> {
            String pwd = passField.getText();
            if (pwd == null || pwd.isEmpty()) {
                errorLbl.setText("יש להזין סיסמה");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);

            new Thread(() -> {
                // אימות מול חשבון התוכנה ואז הנפקת טוקן חדש מהשרת
                boolean ok = userService.login(email, pwd).isPresent()
                        && AuthClient.requestToken(email);
                Platform.runLater(() -> {
                    if (ok) {
                        success[0] = true;
                        dialog.close();
                    } else {
                        errorLbl.setText("סיסמה שגויה או שגיאת חיבור לשרת");
                        errorLbl.setVisible(true);
                        errorLbl.setManaged(true);
                        confirmBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                    }
                });
            }, "relogin").start();
        };

        confirmBtn.setOnAction(e -> attempt.run());
        passField.setOnAction(e -> attempt.run());   // Enter מאשר

        HBox buttons = new HBox(10, confirmBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, sub, passField, errorLbl, buttons);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();

        return success[0];
    }

    //התנקתות
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("התנתקות");
        alert.setHeaderText(null);
        alert.setContentText("האם את בטוחה שברצונך להתנתק מהמערכת?");
        alert.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        if (alert.showAndWait().get() == ButtonType.OK) {
            AuthClient.clear();   // ניקוי הטוקן מהזיכרון בהתנתקות
            new LoginView(stage, userService, mainApp).show();
        }
    }

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