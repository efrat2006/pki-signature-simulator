package com.authentisign.desktop.services.signing.pdf;

import com.authentisign.desktop.security.strategies.Ed25519;
import com.authentisign.desktop.services.signing.ocsp.OcspService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import java.io.File;
import java.io.FileInputStream;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfSignatureVerifier {

    private final OcspService ocspService;

    public PdfSignatureVerifier(OcspService ocspService) {
        this.ocspService = ocspService;
        ensureBcProvider();
    }

    //מאמת את כל החתימות הדיגטליות שנמצאות בקובץ + פירוט
    public VerificationOutcome verify(File signedPdf) {
        List<SignerCheck> checks = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(signedPdf)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();

            if (signatures.isEmpty()) {
                return new VerificationOutcome(Status.NO_SIGNATURE,
                        "לא נמצאה חתימה דיגיטלית במסמך זה.", checks);
            }

            for (PDSignature signature : signatures) {
                checks.add(verifySingleSignature(signedPdf, signature));
            }

            boolean allValid = checks.stream().allMatch(SignerCheck::fullyValid);
            Status status = allValid ? Status.VALID : Status.INVALID;
            String general = allValid
                    ? "כל החתימות במסמך תקינות."
                    : "אחת או יותר מהחתימות במסמך אינן תקינות.";
            return new VerificationOutcome(status, general, checks);

        } catch (Exception e) {
            return new VerificationOutcome(Status.ERROR,
                    "שגיאה בעת אימות המסמך: " + e.getMessage(), checks);
        }
    }


    //אימות חתימה בודדת
    private SignerCheck verifySingleSignature(File signedPdf, PDSignature signature) {
        try {
            byte[] sigContent = readContents(signedPdf, signature);
            byte[] signedData = readSignedContent(signedPdf, signature);
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedData), sigContent);

            Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                return SignerCheck.invalid("לא ידוע", null, "לא נמצא חותם בתוך החתימה הדיגיטלית.");
            }
            SignerInformation signer = signers.iterator().next();

            X509Certificate cert = extractCertificate(cms, signer);
            String signerName = extractCommonName(cert);

            Calendar signDateCal = signature.getSignDate();
            LocalDateTime signDate = signDateCal != null
                    ? LocalDateTime.ofInstant(signDateCal.toInstant(), signDateCal.getTimeZone().toZoneId())
                    : null;

            boolean cryptoValid = verifyEd25519Signature(signer, cert, signedData);
            if (!cryptoValid) {
                return SignerCheck.invalid(signerName, signDate,
                        "האימות המתמטי נכשל - ייתכן שתוכן המסמך שונה לאחר החתימה.");
            }

            Date checkDate = (signDateCal != null) ? signDateCal.getTime() : new Date();
            try {
                cert.checkValidity(checkDate);
            } catch (Exception certEx) {
                return SignerCheck.invalid(signerName, signDate,
                        "תעודת החותם אינה תקפה (פגה תוקף / טרם נכנסה לתוקף במועד החתימה).");
            }

            return SignerCheck.valid(signerName, signDate);

        } catch (Exception e) {
            return SignerCheck.invalid("לא ידוע", null, "שגיאה באימות החתימה: " + e.getMessage());
        }
    }

   //קריאת תוכן החתימה מהקובץ
    private byte[] readContents(File file, PDSignature signature) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return signature.getContents(fis);
        }
    }


    private byte[] readSignedContent(File file, PDSignature signature) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return signature.getSignedContent(fis);
        }
    }

    //אימות החתימה
    private boolean verifyEd25519Signature(SignerInformation signer, X509Certificate cert, byte[] signedData) {
        try {
            byte[] signatureBytes = signer.getSignature();
            Ed25519 ed25519 = new Ed25519();
            return ed25519.verify(signedData, signatureBytes, cert.getPublicKey());
        } catch (Exception e) {
            return false;
        }
    }

    //חילוץ התעודה ושם החותם
    private X509Certificate extractCertificate(CMSSignedData cms, SignerInformation signer) throws Exception {
        Store<X509CertificateHolder> certs = cms.getCertificates();
        Collection<X509CertificateHolder> matches = certs.getMatches(signer.getSID());

        if (matches.isEmpty()) {
            throw new Exception("Certificate not found");
        }
        X509CertificateHolder certHolder = matches.iterator().next();

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
    }

    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

    //מחלץ את שם הCA
    private String extractCommonName(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        Matcher m = CN_PATTERN.matcher(dn);
        if (m.find()) {
            return m.group(1).trim();
        }
        return dn;
    }

    //מוודא שספק BC קיים
    private void ensureBcProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }


    public enum Status { VALID, INVALID, NO_SIGNATURE, ERROR }

    public record VerificationOutcome(Status status, String generalMessage, List<SignerCheck> signerChecks) {}

    public record SignerCheck(String signerName, LocalDateTime signDate, boolean fullyValid, String reason) {
        public static SignerCheck valid(String signerName, LocalDateTime signDate) {
            return new SignerCheck(signerName, signDate, true, null);
        }

        public static SignerCheck invalid(String signerName, LocalDateTime signDate, String reason) {
            return new SignerCheck(signerName, signDate, false, reason);
        }
    }
}