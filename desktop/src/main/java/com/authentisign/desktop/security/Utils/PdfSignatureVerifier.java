//package com.authentisign.desktop.security.Utils;
//
//import com.authentisign.desktop.security.KeyStore.KeyStoreService;
//import org.apache.pdfbox.Loader;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
//import org.bouncycastle.cert.X509CertificateHolder;
//import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
//import org.bouncycastle.cms.CMSProcessableByteArray;
//import org.bouncycastle.cms.CMSSignedData;
//import org.bouncycastle.cms.SignerInformation;
//import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
//import org.bouncycastle.util.Store;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.security.cert.X509Certificate;
//import java.util.Collection;
//import java.util.Date;
//import java.util.List;
//
//public class PdfSignatureVerifier {
//
//    private final KeyStoreService ksService = new KeyStoreService();
//    private final HttpClient httpClient = HttpClient.newHttpClient();
//    private static final String CA_BASE_URL = "http://localhost:8080"; // כתובת שרת ה-CA
//
//    public enum RevocationStatus { GOOD, REVOKED, UNKNOWN }
//
//    public void verifySignature(File signedPdf) throws Exception {
//        try (PDDocument doc = Loader.loadPDF(signedPdf)) {
//            List<PDSignature> signatures = doc.getSignatureDictionaries();
//
//            for (PDSignature signature : signatures) {
//                // קבלת התוכן החתום מה-PDF
//                byte[] sigContent = signature.getContents(new FileInputStream(signedPdf));
//                byte[] signedData = signature.getSignedContent(new FileInputStream(signedPdf));
//                CMSSignedData cms = new CMSSignedData(
//                        new CMSProcessableByteArray(signedData), sigContent);
//
//                verifySingleSignature(cms);
//                System.out.println("Signature verified successfully");
//            }
//        } catch (Exception e) {
//            System.out.println("Failed to verified a signature: " + e.getMessage());
//        }
//    }
//
//    private void verifySingleSignature(CMSSignedData cms) throws Exception {
//        var signers = cms.getSignerInfos().getSigners();
//        if (signers.isEmpty()) {
//            throw new Exception("Signer not found");
//        }
//
//        SignerInformation signer = signers.iterator().next();
//        X509Certificate cert = extractCertificate(cms, signer);
//        boolean isValid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
//                .setProvider("BC")
//                .build(cert));
//        if (!isValid) {
//            throw new Exception("החתימה אינה תקפה - ייתכן שהמסמך שונה");
//        }
//
//        Date signingTime = getSigningTime(cert.getSerialNumber().toString());
//        cert.checkValidity(signingTime);
//        RevocationStatus status = checkRevocationStatus(cert);
//        if (status == RevocationStatus.REVOKED) {
//            throw new Exception("התעודה בוטלה - החתימה אינה תקפה");
//        }
//        if (status == RevocationStatus.UNKNOWN) {
//            throw new Exception("לא ניתן לאמת את מצב התעודה מול ה-CA");
//        }
//
//        verifyCertificateChain(cert);
//
//        System.out.println("Signature is valid");
//    }
//
//    // חילוץ התעודה
//    private X509Certificate extractCertificate(CMSSignedData cms, SignerInformation signer) throws Exception {
//        Store<X509CertificateHolder> certs = cms.getCertificates();
//        Collection<X509CertificateHolder> matches = certs.getMatches(signer.getSID());
//
//        if (matches.isEmpty()) {
//            throw new Exception("Certificate not found");
//        }
//        X509CertificateHolder certHolder = matches.iterator().next();
//
//        return new JcaX509CertificateConverter()
//                .setProvider("BC")
//                .getCertificate(certHolder);
//    }
//
//    /** שליפת חותם הזמן של החתימה מהמסד (דרך שרת ה-CA) */
//    private Date getSigningTime(String serial) throws Exception {
//        HttpResponse<String> resp = httpClient.send(
//                HttpRequest.newBuilder()
//                        .uri(URI.create(CA_BASE_URL + "/signatures/" + serial + "/time"))
//                        .GET().build(),
//                HttpResponse.BodyHandlers.ofString());
//
//        if (resp.statusCode() != 200) {
//            throw new Exception("לא נמצא חותם זמן לחתימה במסד הנתונים");
//        }
//        long epochMillis = Long.parseLong(resp.body().trim()); // השרת מחזיר זמן ב-epoch millis
//        return new Date(epochMillis);
//    }
//
//    /** בדיקת מצב ביטול התעודה מול שרת ה-CA (OCSP) */
//    private RevocationStatus checkRevocationStatus(X509Certificate cert) {
//        try {
//            String serial = cert.getSerialNumber().toString();
//            HttpResponse<String> resp = httpClient.send(
//                    HttpRequest.newBuilder()
//                            .uri(URI.create(CA_BASE_URL + "/ocsp/" + serial))
//                            .GET().build(),
//                    HttpResponse.BodyHandlers.ofString());
//
//            if (resp.statusCode() != 200) {
//                return RevocationStatus.UNKNOWN;
//            }
//            return RevocationStatus.valueOf(resp.body().trim().toUpperCase());
//        } catch (Exception e) {
//            return RevocationStatus.UNKNOWN;
//        }
//    }
//
//    /** אימות שרשרת האמון: שהתעודה נחתמה ע"י ה-CA המהימן */
//    private void verifyCertificateChain(X509Certificate signerCert) throws Exception {
//        X509Certificate caCert = ksService.getCaCertificate(); // תעודת ה-CA - עוגן האמון
//        signerCert.verify(caCert.getPublicKey());              // נחתמה ע"י ה-CA?
//        caCert.verify(caCert.getPublicKey());                  // ה-CA חתום על עצמו (Root)?
//    }
//}
