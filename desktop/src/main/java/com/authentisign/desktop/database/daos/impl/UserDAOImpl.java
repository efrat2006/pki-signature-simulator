package com.authentisign.desktop.database.daos.impl;

import com.authentisign.desktop.database.DBConnector;
import com.authentisign.desktop.database.JPAUtil;
import com.authentisign.desktop.database.daos.GenericDAO;
import com.authentisign.desktop.database.daos.UserDAO;
import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.exceptions.DatabaseException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAOImpl extends GenericDAO<User> implements UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAOImpl.class);

    public UserDAOImpl() {
        super(User.class);
    }
    //השימוש ב-PreparedStatement הוא נגד מתקפת SQL Injection
    @Override
    public Optional<User> findByEmail(String email) {
        EntityManager em = getEntityManager();
        try(em) {
            return em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", email)
                    //האימייל מוגדר כמשתנה ולא כקוד SQL, כך שבודק את כל הפרמטר ובמקרה של קוד SQL יחזיר false
                    .getResultList().stream()
                    .findFirst();

        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public void updatePassword(Long userId, String newPasswordHash) {
        EntityManager em = getEntityManager();
        try(em) {
            em.getTransaction().begin();
            User user = em.find(User.class, userId);

            if (user != null) {
                user.setPasswordHash(newPasswordHash);
                em.getTransaction().commit();
                logger.info("Password updated successfully for user ID: {}", userId);
                return;
            }
            em.getTransaction().rollback();
            logger.warn("Update password failed: User with ID {} not found.", userId);
            throw new DatabaseException("A system error occurred. Please try again later.");

        } catch (Exception e){
            if(em.getTransaction().isActive()){
                em.getTransaction().rollback();
            }
            logger.error("Failed to update password for user ID: " + userId, e);
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }
@Override
    public void delete(Long id) {

        EntityManager em = getEntityManager();
        try(em) {
            em.getTransaction().begin();
            User user = em.find(User.class, id);

            if(user != null){
                user.setActive(false);
                em.getTransaction().commit();
                logger.info("User with ID {} has been deactivated successfully.", id);
            }
            else{
                em.getTransaction().rollback();
                logger.warn("Delete failed: User with ID " + id + " not found.");
                throw new DatabaseException("A system error occurred. Please try again later.");
            }

        } catch (Exception e){
            if (em.getTransaction().isActive()){
                //מבטל במקרה של שגיאה
                em.getTransaction().rollback();
            }
            logger.error("Database error during user deletion", e);
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }

    public void changePassword(Long userId, String oldPassword, String newPasswordHash) {
        UserDAO userDao = new UserDAOImpl();
        User user = userDao.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with ID " + userId + " not found."));

        if(!BCrypt.checkpw(oldPassword, user.getPasswordHash())){
            throw new RuntimeException("The password is incorrect.");
        }
    }
}
