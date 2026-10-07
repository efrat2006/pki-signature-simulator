package com.authentisign.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.authentisign.desktop.ui.Styles.*;


public class SecurityQuestionsView {

    public enum Mode {
        SELECT,
        ANSWER
    }

    private static final int DEFAULT_REQUIRED = 3;

    private final Mode mode;
    private final List<String> questions;
    private final Consumer<Map<String, String>> onSubmit;

    private int requiredCount = DEFAULT_REQUIRED;
    private String title;
    private String subtitle;
    private String submitText;

    private final List<Row> rows = new ArrayList<>();
    private Label counter;
    private javafx.scene.control.Button submitBtn;

    public SecurityQuestionsView(Mode mode, List<String> questions,
                                 Consumer<Map<String, String>> onSubmit) {
        this.mode = mode;
        this.questions = questions == null ? List.of() : questions;
        this.onSubmit = onSubmit;
    }


    //צריך לבחור 3 שאלות
    public SecurityQuestionsView withRequiredCount(int count) {
        this.requiredCount = Math.max(1, count);
        return this;
    }


    public SecurityQuestionsView withHeader(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        return this;
    }

    //כפתור שליחה
    public SecurityQuestionsView withSubmitText(String text) {
        this.submitText = text;
        return this;
    }


    public Region build() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + C_BG + ";");
        content.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        content.getChildren().addAll(buildHeader(), buildQuestions(), buildFooter());

        updateState();
        return content;
    }

    //בניית כותרות
    private Node buildHeader() {
        String t = title != null ? title
                : (mode == Mode.SELECT ? "הגדרת שאלות אבטחה" : "אימות שאלות אבטחה");
        String s = subtitle != null ? subtitle
                : (mode == Mode.SELECT
                ? "בחר " + requiredCount + " שאלות והגדר להן תשובות. עליהן תידרש לענות בעת פעולות רגישות."
                : "השב על שאלות האבטחה שהגדרת כדי להמשיך.");

        VBox box = new VBox(6, pageTitle(t), subTitle(s));

        if (mode == Mode.SELECT) {
            Label ok = pill("✓  הזהות אומתה בהצלחה", C_GREEN_BG, C_GREEN);
            VBox withBadge = new VBox(12, ok, box);
            withBadge.setAlignment(Pos.TOP_RIGHT);
            return withBadge;
        }
        return box;
    }

    //בניית שאלה
    private Node buildQuestions() {
        VBox list = new VBox(10);
        for (String q : questions) {
            Row row = new Row(q);
            rows.add(row);
            list.getChildren().add(row.container);
        }

        ScrollPane scroll = pageScroll(list);
        scroll.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        scroll.setPrefViewportHeight(mode == Mode.SELECT ? 380 : 260);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private HBox buildFooter() {
        counter = new Label();
        counter.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        String btnText = submitText != null ? submitText
                : (mode == Mode.SELECT ? "שמירה" : "אישור");
        submitBtn = primaryButton(btnText);
        submitBtn.setOnAction(e -> handleSubmit());

        HBox footer = new HBox(12, counter, spacer(), submitBtn);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 0, 0, 0));
        return footer;
    }


    private void updateState() {
        if (mode == Mode.SELECT) {
            int selected = 0, answered = 0;
            for (Row r : rows) {
                if (r.selected) {
                    selected++;
                    if (!r.field.getText().isEmpty()) answered++;
                }
            }
            boolean full = selected >= requiredCount;
            // נעילת שאלות שלא נבחרו אחרי שבחת 3 שאלות
            for (Row r : rows) r.setLocked(full && !r.selected);

            boolean ready = selected == requiredCount && answered == selected;
            submitBtn.setDisable(!ready);

            if (selected < requiredCount) {
                setCounter("נבחרו " + selected + " מתוך " + requiredCount, C_TEXT_MUTED);
            } else if (answered < selected) {
                setCounter("יש למלא תשובה לכל שאלה שנבחרה", C_AMBER);
            } else {
                setCounter("מוכן — נבחרו " + selected + " שאלות ✓", C_GREEN);
            }
        } else {
            int total = rows.size(), answered = 0;
            for (Row r : rows) if (!r.field.getText().isEmpty()) answered++;

            submitBtn.setDisable(answered != total || total == 0);

            if (answered < total) {
                setCounter("נותרו " + (total - answered) + " ללא מענה", C_TEXT_MUTED);
            } else {
                setCounter("כל השאלות נענו ✓", C_GREEN);
            }
        }
    }

    private void setCounter(String text, String color) {
        counter.setText(text);
        counter.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    private void handleSubmit() {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Row r : rows) {
            boolean include = (mode == Mode.ANSWER) || r.selected;
            if (include && !r.field.getText().isEmpty()) {
                answers.put(r.question, r.field.getText());
            }
        }
        if (onSubmit != null) onSubmit.accept(answers);
    }


    private final class Row {
        final String question;
        final VBox container;
        final SecretField field;
        final Label indicator;
        boolean selected;
        boolean locked;

        Row(String question) {
            this.question = question;
            this.field = new SecretField("התשובה שלך");
            this.field.textProperty().addListener((o, a, b) -> updateState());

            Label qLabel = new Label(question);
            qLabel.setWrapText(true);
            qLabel.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            qLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + C_TEXT_MAIN + ";"
                    + (mode == Mode.ANSWER ? " -fx-font-weight: bold;" : ""));
            HBox.setHgrow(qLabel, Priority.ALWAYS);


            if (mode == Mode.SELECT) {
                indicator = new Label();
                indicator.setMinSize(20, 20);
                indicator.setPrefSize(20, 20);
                indicator.setAlignment(Pos.CENTER);

                HBox top = new HBox(10, indicator, qLabel);
                top.setAlignment(Pos.CENTER_RIGHT);

                container = new VBox(10, top, field);
                container.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                container.setPadding(new Insets(14, 16, 14, 16));
                container.setOnMouseClicked(e -> {
                    if (!locked) toggle();
                });
                field.setVisible(false);
                field.setManaged(false);
            } else {
                indicator = null;
                selected = true; // במצב ANSWER כל השאלות פעילות
                container = new VBox(10, qLabel, field);
                container.setPadding(new Insets(14, 16, 14, 16));
            }
            paint();
        }

        void toggle() {
            selected = !selected;
            field.setVisible(selected);
            field.setManaged(selected);
            if (!selected) field.clear();
            paint();
            updateState();
        }

        void setLocked(boolean locked) {
            this.locked = locked;
            paint();
        }

        private void paint() {
            String bg = selected ? C_BLUE_BG : C_WHITE;
            String border = selected ? C_PRIMARY : C_BORDER;
            container.setStyle(
                    "-fx-background-color: " + bg + "; -fx-background-radius: 10;"
                            + "-fx-border-color: " + border + "; -fx-border-radius: 10; -fx-border-width: 1;"
                            + "-fx-cursor: " + (mode == Mode.SELECT && !locked ? "hand" : "default") + ";"
                            + (locked ? "-fx-opacity: 0.45;" : "")
            );
            if (indicator != null) {
                indicator.setText(selected ? "✓" : "");
                indicator.setStyle(
                        "-fx-background-radius: 6; -fx-font-size: 12; -fx-font-weight: bold;"
                                + "-fx-text-fill: white;"
                                + "-fx-background-color: " + (selected ? C_PRIMARY : "transparent") + ";"
                                + "-fx-border-color: " + (selected ? C_PRIMARY : C_BORDER) + ";"
                                + "-fx-border-radius: 6; -fx-border-width: 1;"
                );
            }
        }
    }


    //הסתרת התשובה
    private static final class SecretField extends HBox {
        private final PasswordField pf = new PasswordField();
        private final TextField tf = new TextField();

        SecretField(String prompt) {
            super(8);
            setAlignment(Pos.CENTER_RIGHT);

            pf.setPromptText(prompt);
            tf.setPromptText(prompt);
            tf.textProperty().bindBidirectional(pf.textProperty());
            tf.setVisible(false);
            tf.setManaged(false);

            String fieldStyle = "-fx-padding: 9 12; -fx-font-size: 13; -fx-background-radius: 7;"
                    + "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 7; -fx-border-width: 1;"
                    + "-fx-background-color: " + C_WHITE + ";";
            pf.setStyle(fieldStyle);
            tf.setStyle(fieldStyle);

            StackPane stack = new StackPane(pf, tf);
            HBox.setHgrow(stack, Priority.ALWAYS);

            ToggleButton eye = new ToggleButton("הצג");
            String eyeBase = "-fx-background-color: transparent; -fx-text-fill: " + C_TEXT_MUTED + ";"
                    + "-fx-font-size: 12; -fx-background-radius: 6; -fx-padding: 6 12; -fx-cursor: hand;"
                    + "-fx-border-color: " + C_BORDER + "; -fx-border-radius: 6; -fx-border-width: 1;";
            eye.setStyle(eyeBase);
            eye.selectedProperty().addListener((o, was, on) -> {
                tf.setVisible(on);
                tf.setManaged(on);
                pf.setVisible(!on);
                pf.setManaged(!on);
                eye.setText(on ? "הסתר" : "הצג");
            });

            getChildren().addAll(stack, eye);
        }

        javafx.beans.property.StringProperty textProperty() {
            return pf.textProperty();
        }

        String getText() {
            return pf.getText() == null ? "" : pf.getText().trim();
        }

        void clear() {
            pf.clear();
        }
    }
}