package com.authentisign.desktop.ui;

import com.authentisign.desktop.client.AuthClient;
import com.authentisign.desktop.client.TlsTrustConfig;
import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.services.identity.UserService;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.*;

public class DigSignApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(DigSignApp.class);
    private Stage primaryStage;
    private UserService userService;


    @Override
    public void init() throws Exception {
        //  רץ לפני כל קריאת HTTPS
        TlsTrustConfig.trustAllForDev();

        logger.info("Initializing database services...");
        this.userService = new UserService();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        this.primaryStage.setTitle("AuthentiSign - Secure Digital Signature");

        showLoginScene();
        this.primaryStage.show();
    }
    //מסך ההתחברות
  public void showLoginScene() {
        try{
            LoginView loginView = new LoginView(primaryStage, userService, this);
            loginView.show();
            //מעבר למסך הבא
            if (primaryStage.getScene() != null) {
                Parent root = primaryStage.getScene().getRoot();
                applyFadeTransition(root);
            }
            logger.info("Login screen displayed successfully.");

        }catch (Exception e){
            logger.error("Error switching to Login screen", e);
        }
      this.primaryStage.show();
    }
    //מסך הרשמה
    public void showRegisterScene() {
        try {
            RegisterView registerView = new RegisterView(primaryStage, userService, this);
            registerView.show();
            logger.info("Showing Registration screen.");
        } catch (Exception e) {
            logger.error("Error switching to Register screen", e);
        }
    }
    private void applyFadeTransition(Parent root) {
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(600), root);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }
    //לאחר התחברות בהצלחה
    public void showDashboard(User authenticatedUser) {
        try {
            AuthClient.requestToken(authenticatedUser.getEmail());
            Dashboard dashboard = new Dashboard(primaryStage, authenticatedUser, this.userService, this);
            dashboard.show();


            logger.info("User {} logged in. Showing Dashboard.", authenticatedUser.getEmail());
        } catch (Exception e) {
            logger.error("Error switching to Dashboard", e);
        }
    }
    public static void main(String[] args) {
        // הפעלת  JavaFX
        launch(args);
    }
}
