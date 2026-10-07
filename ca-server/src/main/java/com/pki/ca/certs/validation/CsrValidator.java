package com.pki.ca.certs.validation;

import com.pki.ca.entities.Signer;
import com.pki.ca.repositories.SignerRepo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class CsrValidator {

    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BC_PROVIDER);
        }
    }


    @Autowired(required = false)
    private SignerRepo signerRepo;

    //ממירה את הCSR לפורמט PEM
    public PKCS10CertificationRequest parseCsr(String csrPem) throws Exception{
        //לוקח את הבקשה, הופך לזרם ומקודד לפורמט PEM (הפיכה לזרם היא על מנת לקרוא בצורה יעילה תו אחרי תו)
        try(PEMParser pemParser = new PEMParser(new StringReader(csrPem))) {
            Object obj = pemParser.readObject();
            if(obj instanceof PKCS10CertificationRequest){
                return (PKCS10CertificationRequest) obj;
            }
        }
        throw new Exception("System error");
    }

    //שליפת המפתח הציבורי
    public String getPublicKeyFromCsr(String csrPem) throws  Exception{
        PKCS10CertificationRequest csr = parseCsr(csrPem);
        SubjectPublicKeyInfo pkInfo = csr.getSubjectPublicKeyInfo();
        return Base64.getEncoder().encodeToString(pkInfo.getEncoded());
    }

    //אימות בקשת CSR
    public void validate(PKCS10CertificationRequest csr) throws Exception {

        boolean valid = csr.isSignatureValid(
                new JcaContentVerifierProviderBuilder()
                        .setProvider(BC_PROVIDER)
                        .build(csr.getSubjectPublicKeyInfo())
        );

        if (!valid) {
            throw new IllegalArgumentException("Error");
        }
    }

   //בודק את האם לחותם יש חשבון CA
    public Signer findAndVerifySigner(String email) {
        if (signerRepo == null) {
            throw new IllegalStateException("SignerRepo is not available (called outside Spring context)");
        }
        return signerRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found in CA database: " + email));
    }

    //חילוץ המפתח הציבורי מהCSR
    public PublicKey extractPublicKeyFromCsr(PKCS10CertificationRequest csr) throws Exception {
        SubjectPublicKeyInfo pkInfo = csr.getSubjectPublicKeyInfo();

        byte[] encoded = pkInfo.getEncoded();

        //יצירת אובייקט PublicKey
        KeyFactory kf = KeyFactory.getInstance("Ed25519", "BC");
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }
}