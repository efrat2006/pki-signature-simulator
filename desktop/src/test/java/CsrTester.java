
import com.authentisign.desktop.security.CsrGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.StringReader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;

public class CsrTester {

    public static void main(String[] args) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            CsrGenerator csrGenerator = new CsrGenerator();

            //יצירת מפתחות לבדיקה
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            //יצירת ה-CSR
            System.out.println("--- מתחיל יצירת CSR ---");
            String csrPem = csrGenerator.generateCSRPem(kp.getPublic(), kp.getPrivate(), "TestUser123", "SHA256withRSA");
            System.out.println("CSR נוצר בהצלחה!");
            System.out.println(csrPem);

            //אימות ה-CSR שנוצר
            System.out.println("--- מתחיל אימות CSR ---");
            try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
                Object obj = parser.readObject();
                if (obj instanceof PKCS10CertificationRequest) {
                    PKCS10CertificationRequest csr = (PKCS10CertificationRequest) obj;
                    System.out.println(" הפורמט תקין");
                    System.out.println("Subject: " + csr.getSubject().toString());
                } else {
                    System.out.println(" הפורמט שגוי!");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}