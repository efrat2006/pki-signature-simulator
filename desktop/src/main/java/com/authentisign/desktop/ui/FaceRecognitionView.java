//package com.authentisign.desktop.ui;
//
//import com.authentisign.desktop.camera.CameraCapture;
//import com.authentisign.desktop.camera.FaceWebSocketClient;
//import javafx.application.Platform;
//import javafx.geometry.*;
//import javafx.scene.Scene;
//import javafx.scene.control.*;
//import javafx.scene.image.*;
//import javafx.scene.layout.*;
//import javafx.stage.*;
//import org.opencv.core.Mat;
//
//import java.io.File;
//import java.nio.file.Files;
//import java.util.Map;
//
//public class FaceRecognitionView {
//    private static final String BG_WHITE = "#FFFFFF";
//    private static final String BG_LIGHT = "#F4F6FB";
//    private static final String BG_CARD = "#FFFFFF";
//    private static final String ACCENT = "#2563EB";
//    private static final String ACCENT_LIGHT = "#EFF4FF";
//    private static final String TEXT_PRIMARY = "#111827";
//    private static final String TEXT_MUTED = "#6B7280";
//    private static final String BORDER_COLOR = "#E5E7EB";
//    private static final String SUCCESS = "#16A34A";
//
//    private final Stage stage;
//    private final CameraCapture cameraCapture;
//    private ImageView cameraView;
//    private Button recognizeBtn;
//    private Region statusDot;
//    private Label statusLabel;
//    private boolean cameraRunning = false;
//    private volatile Mat lastFrame;
//
//    // ID card bytes captured from the "browse" buttons, sent to the server before streaming starts
//    private volatile byte[] frontImageBytes;
//    private volatile byte[] backImageBytes;
//
//    private volatile boolean readyToStream = false;
//    private com.authentisign.desktop.camera.FaceWebSocketClient wsClient;
//    private Label verificationStatusLabel;
//
//    // נקרא פעם אחת כאשר אימות הזהות עבר בהצלחה (על ה-FX thread)
//    private final Runnable onVerified;
//
//    public FaceRecognitionView(Stage stage) {
//        this(stage, null);
//    }
//
//    public FaceRecognitionView(Stage stage, Runnable onVerified) {
//        this.stage = stage;
//        this.cameraCapture = new CameraCapture();
//        this.onVerified = onVerified;
//    }
//
//    /**
//     * בונה את תוכן מסך אימות הפנים כרכיב שניתן לשבץ בתוך הדשבורד
//     * (root.setCenter(...)) - בלי להחליף את כל ה-Scene, כך שהסייד-בר,
//     * סרגל הכותרת ושורת הסטטוס נשארים גלויים.
//     */
//    public javafx.scene.Parent build() {
//        HBox titleBar = buildTitleBar();
//        VBox cameraPanel = buildCameraPanel();
//        VBox imagesPanel = buildImagesPanel();
//
//        // הדשבורד מוגדר RTL, לכן הרכיב הראשון מוצג בצד ימין:
//        // תמונות תעודת הזהות בימין, הווידאו בשמאל
//        HBox content = new HBox(20, imagesPanel, cameraPanel);
//        content.setPadding(new Insets(20));
//        HBox.setHgrow(cameraPanel, Priority.ALWAYS);
//        HBox.setHgrow(imagesPanel, Priority.ALWAYS);
//
//        VBox root = new VBox(0, titleBar, content);
//        root.setStyle("-fx-background-color:" + BG_LIGHT + ";");
//        VBox.setVgrow(content, Priority.ALWAYS);
//
//        // כשהמשתמש עובר למסך אחר בדשבורד (הרכיב מוסר מה-Scene) - עוצרים את המצלמה
//        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
//            if (newScene == null) {
//                stopCameraAndCleanup();
//            }
//        });
//
//        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
//        scroll.setFitToWidth(true);
//        scroll.setFitToHeight(true);
//        scroll.setStyle("-fx-background-color:" + BG_LIGHT + "; -fx-background:" + BG_LIGHT + ";");
//        return scroll;
//    }
//
//    /**
//     * מצב חלון עצמאי (נשמר לתאימות לאחור) - מחליף את כל ה-Scene.
//     */
//    public void show() {
//        stage.setOnCloseRequest(e -> {
//            stopCameraAndCleanup();
//            Platform.exit();
//        });
//
//        Scene scene = new Scene(build(), 1100, 720);
//        stage.setTitle("AuthentiSign – זיהוי פנים");
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    /** עצירה מסודרת של המצלמה ושחרור משאבים. */
//    public void stopCameraAndCleanup() {
//        cameraRunning = false;
//        readyToStream = false;
//        cameraCapture.stopCamera();
//        lastFrame = null;
//        if (wsClient != null) {
//            wsClient.disconnect();
//            wsClient = null;
//        }
//    }
//
//    private HBox buildTitleBar() {
//        statusDot = new Region();
//        statusDot.setMinSize(8, 8);
//        statusDot.setStyle("-fx-background-color:#D1D5DB;-fx-background-radius:4;");
//
//        statusLabel = styledLabel("לא פעיל", TEXT_MUTED, 11, true);
//        HBox status = new HBox(6, statusDot, statusLabel);
//        status.setAlignment(Pos.CENTER_LEFT);
//
//        Region spacer = new Region();
//        HBox.setHgrow(spacer, Priority.ALWAYS);
//
//        // בפריסת RTL הרכיב הראשון מוצג בצד ימין - לכן הסטטוס ראשון
//        HBox bar = new HBox(4, status, spacer);
//        bar.setPadding(new Insets(14, 24, 14, 24));
//        bar.setStyle("-fx-background-color:" + BG_WHITE + ";-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:0 0 1 0;");
//        return bar;
//    }
//
//    private VBox buildCameraPanel() {
//        Label title = sectionTitle("📷  מצלמה חיה");
//        cameraView = new ImageView();
//        cameraView.setFitWidth(460);
//        cameraView.setFitHeight(340);
//        cameraView.setPreserveRatio(true);
//        cameraView.setScaleX(-1);
//
//        StackPane videoFrame = new StackPane(cameraView);
//        videoFrame.setMinHeight(340);
//        videoFrame.setStyle(videoFrameStyle(false));
//
//        Button startBtn = btn("▶  הפעל מצלמה", ACCENT, "#FFFFFF");
//        Button stopBtn = btn("⏹  עצור", BG_LIGHT, TEXT_MUTED);
//        stopBtn.setDisable(true);
//
//        // כפתור אימות פנים - זמין רק אחרי שהמצלמה פעילה. בלחיצה: קודם נשלחות תמונות
//        // תעודת הזהות, ורק אחרי שהשרת מאשר "ready" מתחילה הזרמת הווידאו החי.
//        recognizeBtn = btn("🔍  אימות פנים", ACCENT_LIGHT, ACCENT);
//        recognizeBtn.setMaxWidth(Double.MAX_VALUE);
//        recognizeBtn.setDisable(true);
//
//        verificationStatusLabel = styledLabel("", TEXT_MUTED, 12, false);
//        verificationStatusLabel.setWrapText(true);
//
//        // כפתור הפעלה - thread יחיד שקורא מהמצלמה ושומר ב-lastFrame
//        startBtn.setOnAction(e -> {
//            cameraRunning = true;
//            if (!cameraCapture.startCamera(640, 480)) {
//                System.err.println("❌ Could not start camera");
//                cameraRunning = false;
//                verificationStatusLabel.setStyle("-fx-text-fill:#DC2626;");
//                verificationStatusLabel.setText("❌ לא ניתן להפעיל את המצלמה");
//                return;
//            }
//
//            new Thread(() -> {
//                while (cameraRunning) {
//                    Mat frame = cameraCapture.grabFrame();
//                    if (frame != null && !frame.empty()) {
//                        lastFrame = frame.clone();
//                        Image img = CameraCapture.matToFxImage(frame);
//                        Platform.runLater(() -> cameraView.setImage(img));
//                    }
//                    try { Thread.sleep(33); } catch (InterruptedException ex) { break; }
//                }
//            }).start();
//
//            statusDot.setStyle("-fx-background-color:" + SUCCESS + ";-fx-background-radius:4;");
//            statusLabel.setText("פעיל");
//            startBtn.setDisable(true);
//            stopBtn.setDisable(false);
//            recognizeBtn.setDisable(false);
//            videoFrame.setStyle(videoFrameStyle(true));
//        });
//
//        stopBtn.setOnAction(e -> {
//            readyToStream = false;
//            cameraRunning = false;
//            cameraCapture.stopCamera();
//            lastFrame = null;
//            if (wsClient != null) {
//                wsClient.disconnect();
//                wsClient = null;
//            }
//            verificationStatusLabel.setText("");
//            resetToIdle(startBtn, recognizeBtn, stopBtn, statusDot, statusLabel, videoFrame);
//        });
//
//        // כפתור אימות - שולח קודם את תמונות תעודת הזהות, ורק לאחר מכן מזרים את הווידאו החי
//        recognizeBtn.setOnAction(e -> {
//            if (frontImageBytes == null) {
//                verificationStatusLabel.setStyle("-fx-text-fill:#DC2626;");
//                verificationStatusLabel.setText("יש לבחור קודם תמונת צד קדמי של תעודת הזהות");
//                return;
//            }
//
//            // סגירת ריצה קודמת אם נשארה פתוחה, כדי שלא יישלחו פריימים יתומים ל-session ישן
//            readyToStream = false;
//            if (wsClient != null) {
//                wsClient.disconnect();
//                wsClient = null;
//            }
//
//            recognizeBtn.setDisable(true);
//            recognizeBtn.setText("מאמת נתונים מול השרת...");
//            verificationStatusLabel.setStyle("-fx-text-fill:" + TEXT_MUTED + ";");
//            verificationStatusLabel.setText("שולח תמונות תעודת זהות לשרת...");
//
//            wsClient = new FaceWebSocketClient();
//            wsClient.setListener(new FaceWebSocketClient.Listener() {
//                @Override
//                public void onReady() {
//                    readyToStream = true;
//                    Platform.runLater(() -> verificationStatusLabel.setText("מזהה זהות בזמן אמת..."));
//                }
//
//                @Override
//                public void onProgress(Map<String, String> fields) {
//                    Platform.runLater(() -> verificationStatusLabel.setText(
//                            "פריים " + fields.getOrDefault("frame", "?")
//                                    + " | פנים זוהו: " + fields.getOrDefault("face_detected", "?")
//                                    + " | התאמה ממוצעת: " + fields.getOrDefault("avg_match_score", "?")));
//                }
//
//                @Override
//                public void onResult(Map<String, String> fields) {
//                    // עוצרים מיד את הזרמת הפריימים כדי שלא יישלח פריים יתום אחרי הסיום
//                    readyToStream = false;
//                    Platform.runLater(() -> {
//                        String liveness = fields.getOrDefault("liveness", "unknown");
//                        String match = fields.getOrDefault("avg_match_score", "0");
//                        String reason = fields.getOrDefault("reason", "");
//
//                        double matchVal = 0.0;
//                        try { matchVal = Double.parseDouble(match); } catch (Exception ignore) {}
//
//                        // הצלחה רק כאשר גם חיוּת וגם התאמה עוברים.
//                        // מעדיפים את שדה reason מהשרת אם קיים; אחרת נופלים לבדיקה מקומית.
//                        boolean ok;
//                        String msg;
//                        if (!reason.isEmpty()) {
//                            ok = "verified".equals(reason);
//                            switch (reason) {
//                                case "verified":
//                                    msg = "✅ אימות הושלם בהצלחה (התאמה " + match + ")";
//                                    break;
//                                case "face_mismatch":
//                                    msg = "❌ האימות נכשל — הפנים אינן תואמות את תעודת הזהות (התאמה " + match + ")";
//                                    break;
//                                case "spoof":
//                                    msg = "❌ האימות נכשל — לא זוהה אדם חי מול המצלמה";
//                                    break;
//                                default:
//                                    msg = "❌ האימות נכשל — לא ניתן לאמת בוודאות (התאמה " + match + ")";
//                            }
//                        } else {
//                            // תאימות לאחור: שרת שעדיין לא שולח reason
//                            ok = "alive".equals(liveness) && matchVal >= 0.65;
//                            if (ok) {
//                                msg = "✅ אימות הושלם בהצלחה (התאמה " + match + ")";
//                            } else if (!"alive".equals(liveness)) {
//                                msg = "❌ האימות נכשל — לא זוהה אדם חי מול המצלמה";
//                            } else {
//                                msg = "❌ האימות נכשל — הפנים אינן תואמות את תעודת הזהות (התאמה " + match + ")";
//                            }
//                        }
//
//                        verificationStatusLabel.setStyle("-fx-text-fill:" + (ok ? SUCCESS : "#DC2626") + ";");
//                        verificationStatusLabel.setText(msg);
//                        recognizeBtn.setDisable(false);
//                        recognizeBtn.setText("🔍  אימות פנים");
//
//                        // אימות עבר בהצלחה -> ממשיכים למסך שאלות האבטחה
//                        System.out.println("VERIFY ok=" + ok + "  onVerified=" + (onVerified != null));
//                        if (ok && onVerified != null) {
//                            onVerified.run();
//                        }
//                    });
//                    if (wsClient != null) wsClient.disconnect();
//                }
//
//                @Override
//                public void onError(String message) {
//                    readyToStream = false;
//                    Platform.runLater(() -> {
//                        verificationStatusLabel.setStyle("-fx-text-fill:#DC2626;");
//                        verificationStatusLabel.setText("❌ שגיאה: " + message);
//                        recognizeBtn.setDisable(false);
//                        recognizeBtn.setText("🔍  אימות פנים");
//                    });
//                }
//            });
//
//            new Thread(() -> {
//                try {
//                    // שלב א': חיבור + שליחת תמונות תעודת הזהות בלבד
//                    wsClient.connect();
//                    wsClient.sendIdFront(frontImageBytes);
//                    if (backImageBytes != null) {
//                        wsClient.sendIdBack(backImageBytes);
//                    }
//                    wsClient.sendStartVerification();
//
//                    // ממתינים לאישור "ready" מהשרת (רק אחרי שה-session נוצר מול שרת האימות)
//                    int waited = 0;
//                    while (!readyToStream && cameraRunning && waited < 20_000) {
//                        Thread.sleep(200);
//                        waited += 200;
//                    }
//
//                    // שלב ב': רק עכשיו, אחרי שהתמונות התקבלו ואושרו - מתחילה הזרמת הווידאו החי.
//                    // בודקים readyToStream שוב ממש לפני כל שליחה כדי שלא יישלח פריים יתום
//                    // אחרי שהשרת סיים (concluded) וקבע readyToStream=false.
//                    while (cameraRunning && readyToStream) {
//                        Mat frameToSend = lastFrame;
//                        if (frameToSend != null && !frameToSend.empty() && readyToStream) {
//                            String base64 = CameraCapture.encodeMatToBase64(frameToSend);
//                            if (readyToStream) {   // בדיקה אחרונה צמודה לשליחה
//                                wsClient.sendFrame(base64);
//                            }
//                        }
//                        Thread.sleep(500);
//                    }
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                    Platform.runLater(() -> {
//                        recognizeBtn.setDisable(false);
//                        recognizeBtn.setText("🔍  אימות פנים");
//                    });
//                }
//            }).start();
//        });
//
//        HBox btnRow = new HBox(10, startBtn, stopBtn);
//        VBox panel = new VBox(14, title, videoFrame, btnRow, recognizeBtn, verificationStatusLabel);
//        panel.setPadding(new Insets(20));
//        panel.setStyle(panelStyle());
//        return panel;
//    }
//
//    private void resetToIdle(Button startBtn, Button recognizeBtn, Button stopBtn, Region statusDot, Label statusLabel, StackPane videoFrame) {
//        startBtn.setDisable(false);
//        stopBtn.setDisable(true);
//        recognizeBtn.setDisable(true);
//        recognizeBtn.setText("🔍  אימות פנים");
//        statusDot.setStyle("-fx-background-color:#D1D5DB;-fx-background-radius:4;");
//        statusLabel.setText("לא פעיל");
//        videoFrame.setStyle(videoFrameStyle(false));
//        cameraView.setImage(null);
//    }
//
//    private VBox buildImagesPanel() {
//        Label title = sectionTitle("🪪  תמונות מסמך זהות");
//        VBox panel = new VBox(14, title,
//                buildImageCard("צד קדמי", bytes -> frontImageBytes = bytes),
//                buildImageCard("צד אחורי", bytes -> backImageBytes = bytes));
//        panel.setPadding(new Insets(20));
//        panel.setStyle(panelStyle());
//        return panel;
//    }
//
//    private VBox buildImageCard(String cardTitle, java.util.function.Consumer<byte[]> onImageLoaded) {
//        Label lbl = styledLabel(cardTitle, TEXT_MUTED, 12, true);
//        ImageView imageView = new ImageView();
//        imageView.setFitWidth(150);
//        imageView.setFitHeight(100);
//        imageView.setPreserveRatio(true);
//        imageView.setStyle("-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1;");
//
//        Button browseBtn = btn("📁  בחר קובץ", BG_LIGHT, TEXT_PRIMARY);
//        browseBtn.setOnAction(e -> {
//            File selectedFile = new FileChooser().showOpenDialog(stage);
//            if (selectedFile != null) {
//                imageView.setImage(new Image(selectedFile.toURI().toString()));
//                browseBtn.setText("נבחר: " + selectedFile.getName());
//                try {
//                    onImageLoaded.accept(Files.readAllBytes(selectedFile.toPath()));
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                    browseBtn.setText("❌ שגיאה בקריאת הקובץ");
//                }
//            }
//        });
//
//        browseBtn.setMaxWidth(Double.MAX_VALUE);
//        VBox card = new VBox(8, lbl, imageView, browseBtn);
//        card.setAlignment(Pos.CENTER);
//        card.setPadding(new Insets(14));
//        card.setStyle("-fx-background-color:" + BG_CARD + ";-fx-background-radius:10;-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:1;");
//        return card;
//    }
//
//    private Label styledLabel(String text, String color, int size, boolean bold) {
//        Label l = new Label(text);
//        l.setStyle("-fx-text-fill:" + color + ";-fx-font-size:" + size + "px;-fx-font-family:'Segoe UI';" + (bold ? "-fx-font-weight:bold;" : ""));
//        return l;
//    }
//
//    private Button btn(String text, String bg, String fg) {
//        Button b = new Button(text);
//        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-padding:9 16;-fx-background-radius:8;");
//        return b;
//    }
//
//    private String videoFrameStyle(boolean active) {
//        return "-fx-background-color:" + (active ? "#F0FDF4" : BG_LIGHT) + ";-fx-border-color:" + (active ? SUCCESS : BORDER_COLOR) + ";-fx-border-width:2;-fx-border-radius:10;";
//    }
//
//    private String panelStyle() {
//        return "-fx-background-color:" + BG_WHITE + ";-fx-background-radius:12;-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:1;";
//    }
//
//    private Label sectionTitle(String text) { return styledLabel(text, TEXT_PRIMARY, 14, true); }
//}


















//
//
//package com.authentisign.desktop.ui;
//
//import com.authentisign.desktop.camera.CameraCapture;
//import com.authentisign.desktop.camera.FaceWebSocketClient;
//import javafx.application.Platform;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.*;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.layout.*;
//import javafx.stage.Stage;
//import org.opencv.core.Mat;
//
//import java.util.Map;
//
///**
// * FaceRecognitionView - מלא עם form, camera, WebSocket
// * בלי תמונות זהות - רק firstName, lastName, birthDate
// */
//public class FaceRecognitionView {
//
//    private static final String BG_WHITE = "#FFFFFF";
//    private static final String BG_LIGHT = "#F4F6FB";
//    private static final String ACCENT = "#2563EB";
//    private static final String ACCENT_LIGHT = "#EFF4FF";
//    private static final String TEXT_PRIMARY = "#111827";
//    private static final String TEXT_MUTED = "#6B7280";
//    private static final String BORDER_COLOR = "#E5E7EB";
//    private static final String SUCCESS = "#16A34A";
//
//    private final Stage stage;
//    private final CameraCapture cameraCapture;
//    private ImageView cameraView;
//    private Button recognizeBtn;
//    private Region statusDot;
//    private Label statusLabel;
//    private boolean cameraRunning = false;
//    private volatile Mat lastFrame;
//
//    private volatile boolean readyToStream = false;
//    private FaceWebSocketClient wsClient;
//    private Label verificationStatusLabel;
//
//    // ✅ Form fields
//    private TextField firstNameField;
//    private TextField lastNameField;
//    private TextField birthDateField;
//
//    private final Runnable onVerified;
//
//    public FaceRecognitionView(Stage stage, Runnable onVerified) {
//        this.stage = stage;
//        this.cameraCapture = new CameraCapture();
//        this.onVerified = onVerified;
//    }
//
//    public FaceRecognitionView(Stage stage) {
//        this(stage, null);
//    }
//
//    // ============================================================
//    // Getters - מה שהמשתמש הזין
//    // ============================================================
//
//    public String getFirstName() {
//        return firstNameField != null ? firstNameField.getText().trim() : "";
//    }
//
//    public String getLastName() {
//        return lastNameField != null ? lastNameField.getText().trim() : "";
//    }
//
//    public String getBirthDate() {
//        return birthDateField != null ? birthDateField.getText().trim() : "";
//    }
//
//    // ============================================================
//    // Main Build
//    // ============================================================
//
//    public javafx.scene.Parent build() {
//        HBox titleBar = buildTitleBar();
//        VBox cameraPanel = buildCameraPanel();
//        VBox detailsPanel = buildDetailsPanel();
//
//        HBox content = new HBox(20, detailsPanel, cameraPanel);
//        content.setPadding(new Insets(20));
//        HBox.setHgrow(cameraPanel, Priority.ALWAYS);
//        HBox.setHgrow(detailsPanel, Priority.ALWAYS);
//
//        VBox root = new VBox(0, titleBar, content);
//        root.setStyle("-fx-background-color:" + BG_LIGHT + ";");
//        VBox.setVgrow(content, Priority.ALWAYS);
//
//        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
//            if (newScene == null) {
//                stopCameraAndCleanup();
//            }
//        });
//
//        ScrollPane scroll = new ScrollPane(root);
//        scroll.setFitToWidth(true);
//        scroll.setFitToHeight(true);
//        scroll.setStyle("-fx-background-color:" + BG_LIGHT + ";");
//        return scroll;
//    }
//
//    public void show() {
//        stage.setOnCloseRequest(e -> {
//            stopCameraAndCleanup();
//            Platform.exit();
//        });
//
//        Scene scene = new Scene(build(), 1100, 720);
//        stage.setTitle("AuthentiSign – זיהוי פנים");
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    public void stopCameraAndCleanup() {
//        cameraRunning = false;
//        readyToStream = false;
//        cameraCapture.stopCamera();
//        lastFrame = null;
//        if (wsClient != null) {
//            wsClient.disconnect();
//            wsClient = null;
//        }
//    }
//
//    // ============================================================
//    // Build Panels
//    // ============================================================
//
//    private VBox buildDetailsPanel() {
//        Label title = label("📋  פרטי בעל החשבון", TEXT_PRIMARY, 14, true);
//
//        Label fnLbl = label("שם פרטי:", TEXT_MUTED, 11, true);
//        firstNameField = textField("לדוגמה: יוני");
//
//        Label lnLbl = label("שם משפחה:", TEXT_MUTED, 11, true);
//        lastNameField = textField("לדוגמה: כהן");
//
//        Label bdLbl = label("תאריך לידה (yyyy-MM-dd):", TEXT_MUTED, 11, true);
//        birthDateField = textField("לדוגמה: 1990-05-15");
//
//        VBox panel = new VBox(12, title, fnLbl, firstNameField, lnLbl, lastNameField, bdLbl, birthDateField);
//        panel.setPadding(new Insets(20));
//        panel.setStyle(panelStyle());
//        return panel;
//    }
//
//    private VBox buildCameraPanel() {
//        Label title = label("📷  מצלמה חיה", TEXT_PRIMARY, 14, true);
//        cameraView = new ImageView();
//        cameraView.setFitWidth(460);
//        cameraView.setFitHeight(340);
//        cameraView.setPreserveRatio(true);
//        cameraView.setScaleX(-1);
//
//        StackPane videoFrame = new StackPane(cameraView);
//        videoFrame.setMinHeight(340);
//        videoFrame.setStyle(videoFrameStyle(false));
//
//        Button startBtn = btn("▶  הפעל מצלמה", ACCENT, "#FFFFFF");
//        Button stopBtn = btn("⏹  עצור", BG_LIGHT, TEXT_MUTED);
//        stopBtn.setDisable(true);
//
//        recognizeBtn = btn("🔍  אימות פנים", ACCENT_LIGHT, ACCENT);
//        recognizeBtn.setMaxWidth(Double.MAX_VALUE);
//        recognizeBtn.setDisable(true);
//
//        verificationStatusLabel = label("", TEXT_MUTED, 12, false);
//        verificationStatusLabel.setWrapText(true);
//
//        startBtn.setOnAction(e -> {
//            cameraRunning = true;
//            if (!cameraCapture.startCamera(640, 480)) {
//                setMessage("❌ לא ניתן להפעיל את המצלמה", "#DC2626");
//                return;
//            }
//
//            new Thread(() -> {
//                while (cameraRunning) {
//                    Mat frame = cameraCapture.grabFrame();
//                    if (frame != null && !frame.empty()) {
//                        lastFrame = frame.clone();
//                        Image img = CameraCapture.matToFxImage(frame);
//                        Platform.runLater(() -> cameraView.setImage(img));
//                    }
//                    try { Thread.sleep(33); } catch (InterruptedException ex) { break; }
//                }
//            }).start();
//
//            statusDot.setStyle("-fx-background-color:" + SUCCESS + ";");
//            statusLabel.setText("פעיל");
//            startBtn.setDisable(true);
//            stopBtn.setDisable(false);
//            recognizeBtn.setDisable(false);
//            videoFrame.setStyle(videoFrameStyle(true));
//        });
//
//        stopBtn.setOnAction(e -> {
//            readyToStream = false;
//            cameraRunning = false;
//            cameraCapture.stopCamera();
//            lastFrame = null;
//            if (wsClient != null) wsClient.disconnect();
//            verificationStatusLabel.setText("");
//            resetToIdle(startBtn, recognizeBtn, stopBtn, statusDot, statusLabel, videoFrame);
//        });
//
//        recognizeBtn.setOnAction(e -> {
//            // בדיקות
//            if (firstNameField.getText().trim().isEmpty()) {
//                setMessage("❌ יש להזין שם פרטי", "#DC2626");
//                return;
//            }
//            if (lastNameField.getText().trim().isEmpty()) {
//                setMessage("❌ יש להזין שם משפחה", "#DC2626");
//                return;
//            }
//            if (birthDateField.getText().trim().isEmpty()) {
//                setMessage("❌ יש להזין תאריך לידה", "#DC2626");
//                return;
//            }
//
//            readyToStream = false;
//            if (wsClient != null) wsClient.disconnect();
//
//            recognizeBtn.setDisable(true);
//            recognizeBtn.setText("מאמת...");
//            setMessage("מתחיל אימות...", TEXT_MUTED);
//
//            wsClient = new FaceWebSocketClient();
//            wsClient.setListener(new FaceWebSocketClient.Listener() {
//                @Override
//                public void onReady() {
//                    readyToStream = true;
//                    Platform.runLater(() -> setMessage("מזהה זהות בזמן אמת...", TEXT_MUTED));
//                }
//
//                @Override
//                public void onProgress(Map<String, String> fields) {
//                    Platform.runLater(() -> {
//                        String frame = fields.getOrDefault("frame", "?");
//                        String detected = fields.getOrDefault("face_detected", "?");
//                        String score = fields.getOrDefault("avg_match_score", "?");
//                        setMessage("Frame " + frame + " | פנים: " + detected + " | התאמה: " + score, TEXT_MUTED);
//                    });
//                }
//
//                @Override
//                public void onResult(Map<String, String> fields) {
//                    readyToStream = false;
//                    Platform.runLater(() -> {
//                        String reason = fields.getOrDefault("reason", "");
//                        boolean ok = "verified".equals(reason);
//
//                        if (ok) {
//                            setMessage("✅ אימות בהצלחה!", SUCCESS);
//                        } else {
//                            setMessage("❌ אימות נכשל", "#DC2626");
//                        }
//
//                        recognizeBtn.setDisable(false);
//                        recognizeBtn.setText("🔍  אימות פנים");
//
//                        if (ok && onVerified != null) {
//                            onVerified.run();
//                        }
//                    });
//                    if (wsClient != null) wsClient.disconnect();
//                }
//
//                @Override
//                public void onError(String message) {
//                    readyToStream = false;
//                    Platform.runLater(() -> {
//                        setMessage("❌ שגיאה: " + message, "#DC2626");
//                        recognizeBtn.setDisable(false);
//                        recognizeBtn.setText("🔍  אימות פנים");
//                    });
//                }
//            });
//
//            new Thread(() -> {
//                try {
//                    wsClient.connect();
//
//                    // ✅ שלח את הפרטים בלבד (לא תמונות!)
//                    wsClient.sendFirstName(getFirstName());
//                    wsClient.sendLastName(getLastName());
//                    wsClient.sendBirthDate(getBirthDate());
//                    wsClient.sendStartVerification();
//
//                    // המתן עד ש-ready
//                    int waited = 0;
//                    while (!readyToStream && cameraRunning && waited < 20_000) {
//                        Thread.sleep(200);
//                        waited += 200;
//                    }
//
//                    // שלח frames
//                    while (cameraRunning && readyToStream) {
//                        Mat frameToSend = lastFrame;
//                        if (frameToSend != null && !frameToSend.empty() && readyToStream) {
//                            String base64 = CameraCapture.encodeMatToBase64(frameToSend);
//                            if (readyToStream) {
//                                wsClient.sendFrame(base64);
//                            }
//                        }
//                        Thread.sleep(500);
//                    }
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                }
//            }).start();
//        });
//
//        HBox btnRow = new HBox(10, startBtn, stopBtn);
//        VBox panel = new VBox(14, title, videoFrame, btnRow, recognizeBtn, verificationStatusLabel);
//        panel.setPadding(new Insets(20));
//        panel.setStyle(panelStyle());
//        return panel;
//    }
//
//    private HBox buildTitleBar() {
//        statusDot = new Region();
//        statusDot.setMinSize(8, 8);
//        statusDot.setStyle("-fx-background-color:#D1D5DB;");
//
//        statusLabel = label("לא פעיל", TEXT_MUTED, 11, true);
//        HBox status = new HBox(6, statusDot, statusLabel);
//        status.setAlignment(Pos.CENTER_LEFT);
//
//        Region spacer = new Region();
//        HBox.setHgrow(spacer, Priority.ALWAYS);
//
//        HBox bar = new HBox(4, status, spacer);
//        bar.setPadding(new Insets(14, 24, 14, 24));
//        bar.setStyle("-fx-background-color:" + BG_WHITE + ";-fx-border-width:0 0 1 0;-fx-border-color:" + BORDER_COLOR + ";");
//        return bar;
//    }
//
//    // ============================================================
//    // Helpers
//    // ============================================================
//
//    private void resetToIdle(Button startBtn, Button recognizeBtn, Button stopBtn,
//                             Region statusDot, Label statusLabel, StackPane videoFrame) {
//        startBtn.setDisable(false);
//        stopBtn.setDisable(true);
//        recognizeBtn.setDisable(true);
//        recognizeBtn.setText("🔍  אימות פנים");
//        statusDot.setStyle("-fx-background-color:#D1D5DB;");
//        statusLabel.setText("לא פעיל");
//        videoFrame.setStyle(videoFrameStyle(false));
//        cameraView.setImage(null);
//    }
//
//    private void setMessage(String text, String color) {
//        verificationStatusLabel.setStyle("-fx-text-fill:" + color + ";");
//        verificationStatusLabel.setText(text);
//    }
//
//    private Label label(String text, String color, int size, boolean bold) {
//        Label l = new Label(text);
//        l.setStyle("-fx-text-fill:" + color + ";-fx-font-size:" + size + "px;" + (bold ? "-fx-font-weight:bold;" : ""));
//        return l;
//    }
//
//    private TextField textField(String prompt) {
//        TextField tf = new TextField();
//        tf.setPromptText(prompt);
//        tf.setStyle("-fx-font-size:12px;-fx-padding:8 10;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:6;");
//        return tf;
//    }
//
//    private Button btn(String text, String bg, String fg) {
//        Button b = new Button(text);
//        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-padding:9 16;-fx-background-radius:8;");
//        return b;
//    }
//
//    private String videoFrameStyle(boolean active) {
//        return "-fx-background-color:" + (active ? "#F0FDF4" : BG_LIGHT) + ";-fx-border-color:" + (active ? SUCCESS : BORDER_COLOR) + ";-fx-border-width:2;-fx-border-radius:10;";
//    }
//
//    private String panelStyle() {
//        return "-fx-background-color:" + BG_WHITE + ";-fx-background-radius:12;-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:1;";
//    }
//}























package com.authentisign.desktop.ui;

import com.authentisign.desktop.camera.CameraCapture;
import com.authentisign.desktop.camera.FaceWebSocketClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.opencv.core.Mat;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;


public class FaceRecognitionView {

    private static final String BG_WHITE = "#FFFFFF";
    private static final String BG_LIGHT = "#F4F6FB";
    private static final String ACCENT = "#2563EB";
    private static final String ACCENT_LIGHT = "#EFF4FF";
    private static final String TEXT_PRIMARY = "#111827";
    private static final String TEXT_MUTED = "#6B7280";
    private static final String BORDER_COLOR = "#E5E7EB";
    private static final String SUCCESS = "#16A34A";

    private final Stage stage;
    private final CameraCapture cameraCapture;
    private ImageView cameraView;
    private ImageView idFrontView;
    private ImageView idBackView;
    private Button recognizeBtn;
    private Region statusDot;
    private Label statusLabel;
    private boolean cameraRunning = false;
    private volatile Mat lastFrame;

    private volatile boolean readyToStream = false;
    private FaceWebSocketClient wsClient;
    private Label verificationStatusLabel;

    private byte[] idFrontBytes = null;
    private byte[] idBackBytes = null;

    private Map<String, String> extractedData = null;

    private final Runnable onVerified;

    public FaceRecognitionView(Stage stage, Runnable onVerified) {
        this.stage = stage;
        this.cameraCapture = new CameraCapture();
        this.onVerified = onVerified;
    }

    public FaceRecognitionView(Stage stage) {
        this(stage, null);
    }


    public String getFirstName() {
        return extractedData != null ? extractedData.getOrDefault("firstName", "") : "";
    }

    public String getLastName() {
        return extractedData != null ? extractedData.getOrDefault("lastName", "") : "";
    }

    public String getBirthDate() {
        return extractedData != null ? extractedData.getOrDefault("birthDate", "16-05-2006") : "";
    }

    public byte[] getIdFrontBytes() {
        return idFrontBytes;
    }

    public byte[] getIdBackBytes() {
        return idBackBytes;
    }

    public Map<String, String> getExtractedData() {
        return extractedData;
    }

    public javafx.scene.Parent build() {
        HBox titleBar = buildTitleBar();
        VBox cameraPanel = buildCameraPanel();
        VBox idUploadPanel = buildIdUploadPanel();

        HBox content = new HBox(20, idUploadPanel, cameraPanel);
        content.setPadding(new Insets(20));
        HBox.setHgrow(cameraPanel, Priority.ALWAYS);
        HBox.setHgrow(idUploadPanel, Priority.ALWAYS);

        VBox root = new VBox(0, titleBar, content);
        root.setStyle("-fx-background-color:" + BG_LIGHT + ";");
        VBox.setVgrow(content, Priority.ALWAYS);

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopCameraAndCleanup();
            }
        });

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color:" + BG_LIGHT + ";");
        return scroll;
    }

    public void show() {
        stage.setOnCloseRequest(e -> {
            stopCameraAndCleanup();
            Platform.exit();
        });

        Scene scene = new Scene(build(), 1100, 720);
        stage.setTitle("AuthentiSign – זיהוי פנים");
        stage.setScene(scene);
        stage.show();
    }

    public void stopCameraAndCleanup() {
        cameraRunning = false;
        readyToStream = false;
        cameraCapture.stopCamera();
        lastFrame = null;
        if (wsClient != null) {
            wsClient.disconnect();
            wsClient = null;
        }
    }


    private VBox buildIdUploadPanel() {
        Label title = label("📋  תעודת זהות", TEXT_PRIMARY, 14, true);

        // Front
        Label frontLbl = label("עמוד קדמי:", TEXT_MUTED, 11, true);
        idFrontView = new ImageView();
        idFrontView.setFitWidth(200);
        idFrontView.setFitHeight(120);
        idFrontView.setStyle("-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:6;");
        Button uploadFrontBtn = btn("🖼️  בחר תמונה", ACCENT, "#FFFFFF");
        uploadFrontBtn.setMaxWidth(Double.MAX_VALUE);
        uploadFrontBtn.setOnAction(e -> {
            File file = chooseImageFile();
            if (file != null) {
                try {
                    idFrontBytes = Files.readAllBytes(file.toPath());
                    idFrontView.setImage(new Image(file.toURI().toString()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Back
        Label backLbl = label("עמוד אחורי:", TEXT_MUTED, 11, true);
        idBackView = new ImageView();
        idBackView.setFitWidth(200);
        idBackView.setFitHeight(120);
        idBackView.setStyle("-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:6;");
        Button uploadBackBtn = btn("🖼️  בחר תמונה", ACCENT, "#FFFFFF");
        uploadBackBtn.setMaxWidth(Double.MAX_VALUE);
        uploadBackBtn.setOnAction(e -> {
            File file = chooseImageFile();
            if (file != null) {
                try {
                    idBackBytes = Files.readAllBytes(file.toPath());
                    idBackView.setImage(new Image(file.toURI().toString()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        VBox panel = new VBox(12,
                title,
                frontLbl, idFrontView, uploadFrontBtn,
                backLbl, idBackView, uploadBackBtn
        );
        panel.setPadding(new Insets(20));
        panel.setStyle(panelStyle());
        return panel;
    }

    private VBox buildCameraPanel() {
        Label title = label("📷  מצלמה חיה", TEXT_PRIMARY, 14, true);
        cameraView = new ImageView();
        cameraView.setFitWidth(460);
        cameraView.setFitHeight(340);
        cameraView.setPreserveRatio(true);
        cameraView.setScaleX(-1);

        StackPane videoFrame = new StackPane(cameraView);
        videoFrame.setMinHeight(340);
        videoFrame.setStyle(videoFrameStyle(false));

        Button startBtn = btn("▶  הפעל מצלמה", ACCENT, "#FFFFFF");
        Button stopBtn = btn("⏹  עצור", BG_LIGHT, TEXT_MUTED);
        stopBtn.setDisable(true);

        recognizeBtn = btn("🔍  אימות פנים", ACCENT_LIGHT, ACCENT);
        recognizeBtn.setMaxWidth(Double.MAX_VALUE);
        recognizeBtn.setDisable(true);

        verificationStatusLabel = label("", TEXT_MUTED, 12, false);
        verificationStatusLabel.setWrapText(true);

        startBtn.setOnAction(e -> {
            cameraRunning = true;
            if (!cameraCapture.startCamera(640, 480)) {
                setMessage("❌ לא ניתן להפעיל את המצלמה", "#DC2626");
                return;
            }

            new Thread(() -> {
                while (cameraRunning) {
                    Mat frame = cameraCapture.grabFrame();
                    if (frame != null && !frame.empty()) {
                        lastFrame = frame.clone();
                        Image img = CameraCapture.matToFxImage(frame);
                        Platform.runLater(() -> cameraView.setImage(img));
                    }
                    try { Thread.sleep(33); } catch (InterruptedException ex) { break; }
                }
            }).start();

            statusDot.setStyle("-fx-background-color:" + SUCCESS + ";");
            statusLabel.setText("פעיל");
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            recognizeBtn.setDisable(false);
            videoFrame.setStyle(videoFrameStyle(true));
        });

        stopBtn.setOnAction(e -> {
            readyToStream = false;
            cameraRunning = false;
            cameraCapture.stopCamera();
            lastFrame = null;
            if (wsClient != null) wsClient.disconnect();
            verificationStatusLabel.setText("");
            resetToIdle(startBtn, recognizeBtn, stopBtn, statusDot, statusLabel, videoFrame);
        });

        recognizeBtn.setOnAction(e -> {
            // בדיקות
            if (idFrontBytes == null) {
                setMessage("❌ יש להעלות תמונת עמוד קדמי", "#DC2626");
                return;
            }

            readyToStream = false;
            if (wsClient != null) wsClient.disconnect();

            recognizeBtn.setDisable(true);
            recognizeBtn.setText("מאמת...");
            setMessage("מתחיל אימות...", TEXT_MUTED);

            wsClient = new FaceWebSocketClient();
            wsClient.setListener(new FaceWebSocketClient.Listener() {
                @Override
                public void onReady() {
                    readyToStream = true;
                    Platform.runLater(() -> setMessage("מזהה זהות בזמן אמת...", TEXT_MUTED));
                }

                @Override
                public void onProgress(Map<String, String> fields) {
                    Platform.runLater(() -> {
                        String frame = fields.getOrDefault("frame", "?");
                        String detected = fields.getOrDefault("face_detected", "?");
                        String score = fields.getOrDefault("avg_match_score", "?");
                        setMessage("Frame " + frame + " | פנים: " + detected + " | התאמה: " + score, TEXT_MUTED);
                    });
                }

                @Override
                public void onResult(Map<String, String> fields) {
                    readyToStream = false;
                    Platform.runLater(() -> {
                        String reason = fields.getOrDefault("reason", "");
                        boolean ok = "verified".equals(reason);

                        if (ok) {
                            // ✅ שמור את הפרטים המחולצים מהשרת
                            extractedData = fields;
                            setMessage("✅ אימות בהצלחה! חולצו הפרטים מהתעודה.", SUCCESS);
                        } else {
                            setMessage("❌ אימות נכשל", "#DC2626");
                        }

                        recognizeBtn.setDisable(false);
                        recognizeBtn.setText("🔍  אימות פנים");

                        if (ok && onVerified != null) {
                            onVerified.run();
                        }
                    });
                    if (wsClient != null) wsClient.disconnect();
                }

                @Override
                public void onError(String message) {
                    readyToStream = false;
                    Platform.runLater(() -> {
                        setMessage("❌ שגיאה: " + message, "#DC2626");
                        recognizeBtn.setDisable(false);
                        recognizeBtn.setText("🔍  אימות פנים");
                    });
                }
            });

            new Thread(() -> {
                try {
                    wsClient.connect();

                    // ✅ שלח תמונות זהות
                    wsClient.sendIdFront(idFrontBytes);
                    if (idBackBytes != null) {
                        wsClient.sendIdBack(idBackBytes);
                    }
                    wsClient.sendStartVerification();

                    // המתן עד ש-ready
                    int waited = 0;
                    while (!readyToStream && cameraRunning && waited < 20_000) {
                        Thread.sleep(200);
                        waited += 200;
                    }

                    // שלח frames
                    while (cameraRunning && readyToStream) {
                        Mat frameToSend = lastFrame;
                        if (frameToSend != null && !frameToSend.empty() && readyToStream) {
                            String base64 = CameraCapture.encodeMatToBase64(frameToSend);
                            if (readyToStream) {
                                wsClient.sendFrame(base64);
                            }
                        }
                        Thread.sleep(500);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        HBox btnRow = new HBox(10, startBtn, stopBtn);
        VBox panel = new VBox(14, title, videoFrame, btnRow, recognizeBtn, verificationStatusLabel);
        panel.setPadding(new Insets(20));
        panel.setStyle(panelStyle());
        return panel;
    }

    private HBox buildTitleBar() {
        statusDot = new Region();
        statusDot.setMinSize(8, 8);
        statusDot.setStyle("-fx-background-color:#D1D5DB;");

        statusLabel = label("לא פעיל", TEXT_MUTED, 11, true);
        HBox status = new HBox(6, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(4, status, spacer);
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.setStyle("-fx-background-color:" + BG_WHITE + ";-fx-border-width:0 0 1 0;-fx-border-color:" + BORDER_COLOR + ";");
        return bar;
    }

    private File chooseImageFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("בחר תמונה");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );
        return chooser.showOpenDialog(stage);
    }

    private void resetToIdle(Button startBtn, Button recognizeBtn, Button stopBtn,
                             Region statusDot, Label statusLabel, StackPane videoFrame) {
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        recognizeBtn.setDisable(true);
        recognizeBtn.setText("🔍  אימות פנים");
        statusDot.setStyle("-fx-background-color:#D1D5DB;");
        statusLabel.setText("לא פעיל");
        videoFrame.setStyle(videoFrameStyle(false));
        cameraView.setImage(null);
    }

    private void setMessage(String text, String color) {
        verificationStatusLabel.setStyle("-fx-text-fill:" + color + ";");
        verificationStatusLabel.setText(text);
    }

    private Label label(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + ";-fx-font-size:" + size + "px;" + (bold ? "-fx-font-weight:bold;" : ""));
        return l;
    }

    private Button btn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-padding:9 16;-fx-background-radius:8;");
        return b;
    }

    private String videoFrameStyle(boolean active) {
        return "-fx-background-color:" + (active ? "#F0FDF4" : BG_LIGHT) + ";-fx-border-color:" + (active ? SUCCESS : BORDER_COLOR) + ";-fx-border-width:2;-fx-border-radius:10;";
    }

    private String panelStyle() {
        return "-fx-background-color:" + BG_WHITE + ";-fx-background-radius:12;-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:1;";
    }
}