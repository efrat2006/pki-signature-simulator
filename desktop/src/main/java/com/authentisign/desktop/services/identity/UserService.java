package com.authentisign.desktop.services.identity;

import com.authentisign.desktop.client.SignerClient;
import com.authentisign.desktop.database.daos.UserDAO;
import com.authentisign.desktop.database.daos.impl.UserDAOImpl;
import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.exceptions.DatabaseException;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.*;

import java.time.LocalDateTime;
import java.util.Optional;

public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserDAO userDAO = new UserDAOImpl();
    //hash דמה קבוע לשימוש במקרה של אימייל לא קיים
    private static final String DUMMY_HASH = "$2a$12$Lcy9WzB396.oQ9Zqj9Zqj9eR8z6D3BqLqLqLqLqLqLqLqLqLqLqLq";

    public UserService() {}
    //הרשמה
    public void register(String name,  String email, String password) {
            UserDAO userDao = new UserDAOImpl();
            try{
                //האם הפורמט תקין
                if(!isValidEmail(email)) {
                    logger.warn("Invalid email address: {}", email);
                    throw new RuntimeException("Registration failed");
                }
                //בדיקה האם קיים משתמש בעל אימייל כזה
                if(userDao.existsByEmail(email)) {
                    logger.info("User with email already exists");
                    throw new RuntimeException("Registration failed");
                }
                //האם הסיסמה תקינה
                if(password == null || password.length() < 8) {
                    logger.warn("Password too short");
                    throw new RuntimeException("Registration failed");

                }

                String hashPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
                User user = new User(name, email, hashPassword, true, LocalDateTime.now());
                userDao.save(user);
                logger.info("User registered successfully: {}", email);
             } catch (Exception e){
                logger.error("Error registering user", e);
                throw new DatabaseException("A system error occurred. Please try again later.");
             }
    }

    //התחברות
    public Optional<User> login(String email, String password) {
            UserDAO userDao = new UserDAOImpl();

            try {
                Optional<User> user = userDao.findByEmail(email.trim().toLowerCase());
                //למניעת מתקפת Timing Attack - בה התוקף יכול לדעת את השגיאה ע"פ זמן התגובה של המערכת
                String hashToCompare = user.isPresent()
                        ?user.get().getPasswordHash()
                        :DUMMY_HASH;

                boolean isPasswordMatch = BCrypt.checkpw(password, hashToCompare);

                if(user.isPresent() && isPasswordMatch) {
                    logger.info("Successful login for: {}", email);
                    return user;
                }
                logger.warn("Failed login attempt for email: {}", email);
                return Optional.empty();
            }

            catch (Exception e) {
               logger.error("Login process failed due to technical error", e);
               return Optional.empty();
            }
    }

    //בדיקת תקינות האימייל
    private boolean isValidEmail(String email) {
        if(email == null || email.isBlank()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    public boolean hasAccount(String email) {
        SignerClient signerController = new SignerClient();
        return signerController.hasAccount(email);
    }

}
