package com.pki.ca;
import com.pki.ca.certs.validation.CsrValidator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;

public class CsrFullTest {

    public static void main(String[] args) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            CsrValidator validator = new CsrValidator();

            //יצירת CSR תקין
            System.out.println("---  יצירת CSR תקין ---");
            String validCsrPem = createStandaloneCsr();
            System.out.println("CSR נוצר בהצלחה.");

            //אימות ה-CSR התקין
            System.out.println("---  אימות ה-CSR התקין ---");
            validator.validate(validator.parseCsr(validCsrPem));
            System.out.println("✅ האימות עבר בהצלחה!");

            // שיבוש נתונים
            System.out.println("\n---  בדיקת שיבוש (Tampering) ---");
            String tamperedCsr = validCsrPem.replace("A", "B"); // שינוי תו
            try {
                validator.validate(validator.parseCsr(tamperedCsr));
                System.out.println("❌ תקלה! האימות עבר למרות שהקובץ שובש.");
            } catch (Exception e) {
                System.out.println("✅ האימות נכשל כצפוי! סיבת הכשל: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String createStandaloneCsr() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        KeyPair kp = kpg.generateKeyPair();

        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                new X500Principal("CN=TestUser"), kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(kp.getPrivate());

        PKCS10CertificationRequest csr = p10Builder.build(signer);

        StringWriter sw = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(sw)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE REQUEST", csr.getEncoded()));
        }
        return sw.toString();
    }
}