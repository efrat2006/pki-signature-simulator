package com.authentisign.desktop.database;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JPAUtil {
    private static final Logger logger = LoggerFactory.getLogger(JPAUtil.class);
    private static final String PERSISTENCE_UNIT_NAME = "SigningAppDB";
    private static EntityManagerFactory factory;

    static {
        try {
            // הדפסה ראשונית לוודא שה-Thread הגיע לכאן
            System.out.println("Initialization started");
            logger.info("Starting EntityManagerFactory creation for unit: {}", PERSISTENCE_UNIT_NAME);
            Thread.currentThread().setContextClassLoader(JPAUtil.class.getClassLoader());
            factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);

            System.out.println("SUCCESS! Factory created");
            logger.info("ntityManagerFactory created successfully!");

        } catch (Throwable ex) {
            System.err.println("ERROR DURING INITIALIZATION");
            logger.error("EntityManagerFactory creation failed!", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        return factory;
    }

    public static void shutdown() {
        if (factory != null && factory.isOpen()) {
            logger.info("Shutting down EntityManagerFactory");
            factory.close();
        }
    }
}