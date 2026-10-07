import com.authentisign.desktop.services.signing.ocsp.OcspService;

import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * טסט מקצה-לקצה של הלקוח: מפעיל את OcspService (הלקוח), ששולח בקשת OCSP אמיתית
 * לשרת ומקבל תשובה. דורש שהשרת (ו/או ה-Gateway) ירוצו.
 *
 * לפני הרצה:
 *   1. ודאי שהשרת רץ (ושה-BASE_URL ב-OcspService מצביע לכתובת הנכונה: 8082 ישיר, או 8080 דרך Gateway).
 *   2. שני את SERIAL_NUMBER למספר סידורי של תעודה שקיימת במסד שלך.
 */
public class OcspClientTest {

    // שני למספר סידורי אמיתי מהמסד שלך (של תעודה תקפה / מבוטלת - לפי מה שרוצים לבדוק)
    private static final String SERIAL_NUMBER = "1234567890";

    public static void main(String[] args) {
        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

            System.out.println("--- OCSP Client Test ---");

            // בונים תעודת דמה עם המספר הסידורי שנבדוק
            X509Certificate cert = buildCertificate(new BigInteger(SERIAL_NUMBER));
            // תעודה עצמית: המנפיק = התעודה עצמה
            X509Certificate[] chain = new X509Certificate[]{ cert, cert };

            // הלקוח: fail-closed כדי שבאמת נראה אם משהו נכשל (לא "ממשיך בשקט")
            OcspService client = new OcspService(null, false);

            System.out.println("Sending OCSP request for serial: " + SERIAL_NUMBER);
            client.verifyForSigning(chain);

            // אם הגענו לכאן בלי חריגה - השרת ענה GOOD (או fail-open)
            System.out.println("RESULT: certificate is VALID (GOOD) - signing allowed");

        } catch (RuntimeException e) {
            // התעודה בוטלה / לא ידועה / השרת לא זמין
            System.out.println("RESULT: signing blocked -> " + e.getMessage());
        } catch (Exception e) {
            System.err.println("TEST ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** בונה תעודת X.509 עם מספר סידורי נתון (Ed25519). */
    private static X509Certificate buildCertificate(BigInteger serial) throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        X500Principal name = new X500Principal("CN=OCSP Test");
        long now = System.currentTimeMillis();

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial,
                new Date(now),
                new Date(now + 365L * 24 * 60 * 60 * 1000),
                name, kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("Ed25519")
                .setProvider("BC").build(kp.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));
    }
}