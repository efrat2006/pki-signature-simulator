package com.authentisign.desktop.security.KeyStore;

import com.authentisign.desktop.security.Utils.FilePaths;
import com.authentisign.desktop.security.strategies.SigningStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;


public class KeyStoreService {


    //מטפלת בחיבור לקובץ וטעינתו לזיכרון
    private KeyStore loadKeyStore(String filePath, char[] password) throws Exception {

        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("File not found: " + filePath);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            //כדי שידע איך לפרש את הנתונים שהועברו
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(fis, password);
            return ks;
        }
    }

    //מחיקת סיסמה - למניעת צילום זיכרון - מתקפת חילוץ זיכרון
    //תוקף עם הרשאות גבוהות יכול לסרוק את זיכרון ה-RAM ולחפש סודות גלויים
    public void wipePassword(char[] password) throws Exception {
        Arrays.fill(password, '\u0000');
    }

    //פונקצית עזר לניהול תיקיות למשתמשים
    public String getUserDirectory(String userEmail) {
        String basePath = FilePaths.getKeysDirectory();
        String userDirPath = basePath + File.separator + userEmail;

        File userDir = new File(userDirPath);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }

        return userDirPath;
    }

    // מחזירה נתיב מדויק ל-alias של משתמש מסוים
    public String getPathForAlias(String email, String alias) {
        String safeAlias = sanitizeAlias(alias);
        //שרשרור נתיבים
        return getUserDirectory(email) + File.separator + alias + ".p12";
    }

    //פונקציית ניקוי תווים אסורים
    private String sanitizeAlias(String alias) {
        return alias.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    //יצירת מפתחות ושמירה בכספת + תעודה זמנית
    // name = המייל מזהה את התיקייה, commonName = שם המשתמש לתצוגה בתעודה הזמנית
    public KeyStore generateAndSaveKeyPair(SigningStrategy strategy, char[] passwordArray, String alias,
                                           String email, String commonName) throws Exception {
        String filePath = getPathForAlias(email, alias);
        File file = new File(filePath);

        if (file.exists() && file.length() > 0) {
            throw new IllegalArgumentException(
                    "כבר קיימת תעודה בשם \"" + alias + "\". בחר/י שם אחר לתעודה."
            );
        }

        file.getParentFile().mkdirs();

        KeyPair pair = strategy.generateKeys();
        X509Certificate cert = CertificateUtils.generateSelfSignedCertificate(pair, strategy, commonName, email);

        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, passwordArray);

        //שמירת המפתח הפרטי ביחד עם התעודות בKS
        keyStore.setKeyEntry(alias, pair.getPrivate(), passwordArray, new Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            keyStore.store(fos, passwordArray);
            fos.flush();
        }
        return keyStore;
    }

    //החלפת התעודה הזמנית בתעודה מה-CA
    public void installIssuedCertificate(String name, String alias, char[] password,
                                         X509Certificate issuedCert, X509Certificate caCert) throws Exception {
        if (issuedCert == null) {
            throw new IllegalArgumentException("issuedCert must not be null");
        }

        String filePath = getPathForAlias(name, alias);
        KeyStore ks = loadKeyStore(filePath, password);


        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
        if (privateKey == null) {
            throw new Exception("No private key found for alias '" + alias + "' — cannot install certificate.");
        }


        Certificate existing = ks.getCertificate(alias);
        if (existing != null &&
                !Arrays.equals(existing.getPublicKey().getEncoded(), issuedCert.getPublicKey().getEncoded())) {
            throw new Exception("Issued certificate public key does not match the key already in the keystore.");
        }

        Certificate[] chain = (caCert != null)
                ? new Certificate[]{issuedCert, caCert}
                : new Certificate[]{issuedCert};


        //החלפה
        if (ks.containsAlias(alias)) {
            ks.deleteEntry(alias);
        }
        ks.setKeyEntry(alias, privateKey, password, chain);


        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            ks.store(fos, password);
            fos.flush();
        }
    }

    //שליפת המפתח הפרטי ושרשרת התעודות ביחד
    public KeyAndChain getKeyAndChain(String filePath, char[] password, String alias) throws Exception {
        try {
            KeyStore ks = loadKeyStore(filePath, password);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
            Certificate[] chain = ks.getCertificateChain(alias);

            if (privateKey == null || chain == null) {
                throw new Exception("Key or certificate  not found");
            }
            return new KeyAndChain(privateKey, chain);
        } finally {
            wipePassword(password);
        }
    }


    //רשימת כל התעודות של משתמש
    public Map<String, X509Certificate> getAllUserCertificates(String userEmail, char[] password) throws Exception {
        String directoryPath = getUserDirectory(userEmail);
        File folder = new File(directoryPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".p12"));

        if (files == null)
            return new HashMap<>();

        Map<String, X509Certificate> userCerts = new HashMap<>();

        try {
            for (File file : files) {
                try {
                    KeyStore ks = loadKeyStore(file.getAbsolutePath(), password);

                        String alias = ks.aliases().nextElement();
                        if (ks.isKeyEntry(alias)) {
                            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
                            userCerts.put(alias, cert);
                        }
                } catch (Exception e) {
                    System.out.println("Could not load keystore file: " + file.getName());
                }
            }
            return userCerts;
        } finally {
            wipePassword(password);
        }
    }

    //שליפת כל התעודות התקפות של משתמש מסוים
    public Map<String, X509Certificate> getValidUserAliases(String email, char[] password) throws Exception {
        Map<String, X509Certificate> allCerts = getAllUserCertificates(email, password);
        Map<String, X509Certificate> validCerts = new HashMap<>();
        Date now = new Date();

        for (Map.Entry<String, X509Certificate> entry : allCerts.entrySet()) {
            try {
                entry.getValue().checkValidity(now);
                validCerts.put(entry.getKey(),  entry.getValue());
            } catch (Exception e) {
                System.out.println("Certificate has expired");
            }
        }
        return validCerts;
    }


    //שליפת התעודה
    public X509Certificate getCertificateDetails(String filePath, char[] password, String alias) throws Exception {
        try {
            KeyStore ks = loadKeyStore(filePath, password);
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            if (cert == null) {
                throw new Exception("Certificate not found");
            }
            return cert;
        } finally {
            wipePassword(password);
        }
    }

    //שליפת המפתח הציבורי
    public PublicKey getPublicKey(String filePath, char[] password, String alias) throws Exception {
        try {
            Certificate cert = getCertificateDetails(filePath, password, alias);
            return cert.getPublicKey();
        } finally {
            wipePassword(password);
        }
    }

    //העברת הALIAS לתיקיית ביטול
    public boolean revokeLocalCertificate(String userName, String alias) {
        return moveAliasToSubfolder(userName, alias, "revoked");
    }

    //העברת התעודה לתיקיית פג תוקף
    public boolean moveToExpiredLocally(String userName, String alias) {
        return moveAliasToSubfolder(userName, alias, "expired");
    }

    //מעבירה את התעודה לתיקייה פנימית
    private boolean moveAliasToSubfolder(String userName, String alias, String subfolder) {
        try {
            Path source = Paths.get(getPathForAlias(userName, alias));
            if (!Files.exists(source)) {
                return true;
            }

            Path targetDir = Paths.get(getUserDirectory(userName), subfolder);
            Files.createDirectories(targetDir);
            String targetName = alias + "." + System.currentTimeMillis() + ".p12";
            Path target = targetDir.resolve(targetName);   //שם ייחודי עם חותמת זמן

            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.out.println("Moving alias '" + alias + "' to '" + subfolder + "' failed: " + e.getMessage());
            return false;
        }
    }

}

