//
//
//import com.authentisign.desktop.services.signing.ocsp.OcspService;
//import com.authentisign.desktop.services.signing.pdf.PdfSignatureVerifier;
//
//import java.io.File;
//import java.security.Security;
//
//public class PdfVerifierServiceTest {
//    public static void main(String[] args) {
//        try {
//            //טעינת ה-Provider של BC
//            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
//
//            //נתיב לקובץ ה-PDF
//            File fileToVerify = new File("signed_test.pdf");
//
//            //בדיקת האימות
//            OcspService ocsp = new OcspService(null, true);   // fail-open לבדיקה
//            PdfSignatureVerifier verifier = new PdfSignatureVerifier(ocsp);
//
//            System.out.println("--- מתחיל אימות קובץ ---");
//            verifier.verifySignatures(fileToVerify);
//            System.out.println("--- סיום האימות ---");
//
//        } catch (Exception e) {
//            System.err.println("שגיאה קריטית בתהליך האימות: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}