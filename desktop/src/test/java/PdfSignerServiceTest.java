//import com.authentisign.desktop.services.signing.ocsp.OcspService;
//import com.authentisign.desktop.services.signing.pdf.PdfSignatureHandler;
//import com.authentisign.desktop.services.signing.pdf.PdfSignerService;
//import org.bouncycastle.cert.X509v3CertificateBuilder;
//import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
//import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
//import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
//
//import javax.security.auth.x500.X500Principal;
//import java.io.File;
//import java.math.BigInteger;
//import java.security.KeyPair;
//import java.security.Security;
//import java.security.cert.X509Certificate;
//import java.util.Date;
//
//public class PdfSignerServiceTest {
//    public static void main(String[] args) {
//        try {
//            //טעינת ה-Provider
//            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
//
//            //יצירת מפתחות ותעודה כאן בשביל הבדיקה
//            EdDSASigningStrategy strategy = new EdDSASigningStrategy();
//            KeyPair kp = strategy.generateKeys();
//            X509Certificate dummyCert = generateDummyCertificate(kp);
//            java.security.cert.Certificate[] chain = new java.security.cert.Certificate[] { dummyCert };
//
//            //הכנה של קובץ PDF
//            File input = new File("C:\\Java projects\\DigSign\\Desktop\\עדכני\\AuthentiSignDesktop\\efrat.pdf");
//            System.out.println("Does file exist? " + input.exists());
//            File output = new File(input.getName() + "_signed_test.pdf");
//
//            // יצירת האובייקטים
//            OcspService ocsp = new OcspService();
//            PdfSignatureHandler handler = new PdfSignatureHandler(strategy, kp.getPrivate(), chain, ocsp);
//            PdfSignerService service = new PdfSignerService();
//
//            //חתימה על ה-PDF
//            service.signPdf(input, output, handler);
//
//            System.out.println("Success! File saved as: " + output.getAbsolutePath());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static X509Certificate generateDummyCertificate(KeyPair keyPair) throws Exception {
//        long now = System.currentTimeMillis();
//        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(
//                new X500Principal("CN=TestUser"),
//                BigInteger.valueOf(now),
//                new Date(now),
//                new Date(now + 365 * 24 * 60 * 60 * 1000),
//                new X500Principal("CN=TestUser"),
//                keyPair.getPublic());
//
//        return new JcaX509CertificateConverter()
//                .setProvider("BC")
//                .getCertificate(v3CertGen.build(new JcaContentSignerBuilder("Ed25519")
//                        .build(keyPair.getPrivate())));
//    }
//}