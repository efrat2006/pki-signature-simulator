//package com.pki.ca;
//
//import com.pki.ca.certs.issuer.CertificateIssuer;
//import com.pki.ca.crypto.SigningStrategy;
//import org.bouncycastle.asn1.x500.X500Name;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import javax.security.auth.x500.X500Principal;
//import java.security.KeyPair;
//import java.security.KeyPairGenerator;
//import java.security.Security;
//import java.security.cert.X509Certificate;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.when;
//
//@SpringBootTest
//class CertificateIssuerTest {
//
//    private KeyPair caKeyPair;
//    private KeyPair userKeyPair;
//    private X500Name caName;
//    private X500Name userName;
//
//    @BeforeEach
//    void setUp() throws Exception {
//        // רישום ה-Provider של Bouncy Castle לסביבת הטסט
//        if (Security.getProvider("BC") == null) {
//            Security.addProvider(new BouncyCastleProvider());
//        }
//
//        // יצירת מפתחות לבדיקה (RSA 2048)
//        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
//        keyGen.initialize(2048);
//        caKeyPair = keyGen.generateKeyPair();
//        userKeyPair = keyGen.generateKeyPair();
//
//        // הגדרת שמות ה-Subject וה-Issuer
//        caName = new X500Name("CN=AuthentiSign Root CA, O=AuthentiSign, C=IL");
//        userName = new X500Name("CN=Test User, OU=Development, C=IL");
//    }
//
//    @Test
//    @DisplayName("בדיקת הנפקת תעודה תקינה ואימות חתימת CA")
//    void shouldIssueValidCertificateSignedByCA() {
//        try {
//            // 1. הגדרת Mock לאסטרטגיית החתימה
//            SigningStrategy mockStrategy = Mockito.mock(SigningStrategy.class);
//            when(mockStrategy.getAlgorithmName()).thenReturn("SHA256withRSA");
//
//            // 2. הפעלת הלוגיקה של ה-CertificateIssuer
//            X509Certificate issuedCert = CertificateIssuer.issueCertificate(
//                    userKeyPair.getPublic(),
//                    caKeyPair.getPrivate(),
//                    caName,
//                    userName,
//                    mockStrategy
//            );
//
//            // --- מערך הבדיקות (Assertions) ---
//
//            // א. וודוא שהאובייקט נוצר
//            assertNotNull(issuedCert, "התעודה לא הונפקה (null)");
//
//            // ב. אימות חתימה קריפטוגרפי - מוודא שה-CA באמת חתם על התעודה
//            assertDoesNotThrow(() -> issuedCert.verify(caKeyPair.getPublic()),
//                    "אימות החתימה נכשל! התעודה לא נחתמה כראוי על ידי מפתח ה-CA");
//
//            // ג. בדיקת Basic Constraints - מוודא שזו תעודת קצה (End-Entity) ולא CA
//            assertEquals(-1, issuedCert.getBasicConstraints(),
//                    "התעודה הונפקה כ-CA, אך היא חייבת להיות End-Entity");
//
//            // ד. בדיקת תוקף זמן (Not Before / Not After)
//            assertDoesNotThrow(() -> issuedCert.checkValidity(),
//                    "התעודה הונפקה מחוץ לטווח התוקף הנוכחי");
//
//            // ה. בדיקת זהות ה-Subject (השוואה בינארית של ה-Distinguished Name)
//            // אנחנו משווים את ה-Encoded Bytes כדי לעקוף בעיות של סדר שדות במחרוזת
//            byte[] expectedSubjectEncoded = new X500Principal(userName.toString()).getEncoded();
//            byte[] actualSubjectEncoded = issuedCert.getSubjectX500Principal().getEncoded();
//
//            assertArrayEquals(expectedSubjectEncoded, actualSubjectEncoded,
//                    "ה-Subject בתעודה אינו תואם לזהות המשתמש המצופית");
//
//            // הדפסת הצלחה לסיכום
//            System.out.println("✅ הטסט עבר בהצלחה!");
//            System.out.println("Subject: " + issuedCert.getSubjectX500Principal());
//            System.out.println("Serial Number: " + issuedCert.getSerialNumber());
//
//        } catch (Exception e) {
//            fail("הטסט נכשל עקב שגיאה בלתי צפויה: " + e.getMessage());
//        }
//    }
//}