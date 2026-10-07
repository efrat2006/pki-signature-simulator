import com.authentisign.desktop.model.dto.CertificationRequestDTO;
import com.authentisign.desktop.security.CsrGenerator;
import com.authentisign.desktop.security.KeyStore.KeyStoreService;
import com.authentisign.desktop.security.strategies.Ed25519;
import com.authentisign.desktop.security.strategies.SigningStrategy;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.File;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;

public class CertificateIssuanceTest {

    // חשבון המייל שעבורו מנפיקים (משמש גם כ-CN בתעודה וגם כמזהה התיקייה)
    private static final String EMAIL = "efrat.mani123@gmail.com";

    private static final String ALIAS      = "my test";
    private static final char[] PASSWORD   = "12345678".toCharArray();

    private static int failures = 0;

    private static void check(String name, boolean condition) {
        System.out.printf("%-42s: %s%n", name, condition);
        if (!condition) failures++;
    }

    public static void main(String[] args) {
        try {
            Security.addProvider(new BouncyCastleProvider());

            final SigningStrategy algorithm = new Ed25519();
            final String algoName  = algorithm.getAlgorithmName();  // "Ed25519"
            final String commonName = EMAIL;                        // ה-CN בתעודה = המייל

            System.out.println("=== הנפקת תעודה עבור " + EMAIL + " ===\n");

            KeyStoreService keyStoreService = new KeyStoreService();
            CsrGenerator csrGenerator = new CsrGenerator();

            // ---------- שלב 1: יצירת זוג מפתחות + כספת תחת תיקיית המייל ----------
            // המייל משמש כמזהה החשבון/התיקייה ישירות
            KeyStore ks = keyStoreService.generateAndSaveKeyPair(
                    algorithm, PASSWORD.clone(), ALIAS, EMAIL, commonName);
            check("keystore created", ks != null);

            // ---------- שלב 2: שליפת חומרי המפתח ----------
            PrivateKey privateKey = (PrivateKey) ks.getKey(ALIAS, PASSWORD);
            Certificate cert      = ks.getCertificate(ALIAS);
            PublicKey publicKey   = (cert != null) ? cert.getPublicKey() : null;

            check("private key present", privateKey != null);
            check("certificate present", cert != null);
            check("public key present", publicKey != null);

            // ---------- שלב 3: יצירת CSR ----------
            String csrPem = csrGenerator.generateCSRPem(
                    publicKey, privateKey, commonName, algoName);

            check("CSR is not null/empty", csrPem != null && !csrPem.isBlank());
            check("CSR has PEM header", csrPem != null && csrPem.contains("BEGIN CERTIFICATE REQUEST"));

            // ---------- שלב 4: אימות ה-CSR ----------
            PKCS10CertificationRequest csr = parseCsr(csrPem);
            check("CSR parses as PKCS#10", csr != null);

            if (csr != null) {
                // 4a. חתימת ה-CSR (self-signature) תקפה מול המפתח הציבורי שבבקשה
                ContentVerifierProvider verifierProvider =
                        new JcaContentVerifierProviderBuilder().setProvider("BC").build(publicKey);
                boolean signatureValid =
                        new JcaPKCS10CertificationRequest(csr).isSignatureValid(verifierProvider);
                check("CSR signature valid", signatureValid);

                // 4b. ה-CN בבקשה שווה למייל המשתמש
                String cn = extractCommonName(csr.getSubject());
                check("CSR CN == email", EMAIL.equals(cn));
                System.out.println("   Subject : " + csr.getSubject());
            }

            // ---------- שלב 5: בניית ה-DTO לשרת ה-CA ----------
            // verified=true הוא placeholder עד לחיבור שרת אימות הזהות.
            CertificationRequestDTO dto = new CertificationRequestDTO(
                    csrPem, true, algoName, commonName);

            check("DTO.csrPem matches", csrPem.equals(dto.getCsrPem()));
            check("DTO.verified == true", dto.isVerified());
            check("DTO.algorithm correct", algoName.equals(dto.getAlgorithm()));
            check("DTO.commonName == email", EMAIL.equals(dto.getCommonName()));

            // ---------- שלב 6: הכספת נכתבה לדיסק תחת תיקיית המייל ----------
            String expectedPath = keyStoreService.getPathForAlias(EMAIL, ALIAS);
            File p12 = new File(expectedPath);
            check("keystore file written under email folder", p12.exists() && p12.length() > 0);
            System.out.println("   File    : " + expectedPath);

            // ---------- סיכום ----------
            System.out.println();
            if (failures == 0) {
                System.out.println("ALL CHECKS PASSED - certificate issued for " + EMAIL);
            } else {
                System.out.println(failures + " CHECK(S) FAILED");
                System.exit(1);
            }

        } catch (Exception e) {
            System.out.println("TEST CRASHED:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** ניתוח מחרוזת PEM לאובייקט PKCS#10. */
    private static PKCS10CertificationRequest parseCsr(String csrPem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            Object obj = parser.readObject();
            return (obj instanceof PKCS10CertificationRequest) ? (PKCS10CertificationRequest) obj : null;
        }
    }

    /** חילוץ ערך ה-CN מתוך שם ה-Subject. */
    private static String extractCommonName(X500Name subject) {
        RDN[] rdns = subject.getRDNs(BCStyle.CN);
        if (rdns.length == 0) return null;
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }
}