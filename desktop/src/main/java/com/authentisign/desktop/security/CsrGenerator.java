package com.authentisign.desktop.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class CsrGenerator {

    public String generateCSRPem(PublicKey publicKey, PrivateKey privateKey, String commonName, String signatureAlgorithm) throws Exception {

        //יצירת פרטי זהות של המבקש
        X500Name subject = new  X500Name("CN=" + commonName + ", OU=DigiSign, O=Software Engineering, C=IL");

        //מחברת בין פרטי הזהות למפתח הציבורי
        JcaPKCS10CertificationRequestBuilder csrBulider =
                new JcaPKCS10CertificationRequestBuilder(subject, publicKey);

        //יצירת חתימה על הבקשה
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm)
                .setProvider(new BouncyCastleProvider())
                .build(privateKey);

        //בניית האובייקט הבינארי של csr
        PKCS10CertificationRequest csr = csrBulider.build(signer);
        return convertToPem(csr);

    }

    //PemWriter את האובייקט StringWriter ולהפוך לקובץ טקסט בתקן PEM
    //ה csr הוא בינארי וצריך להפוך אותו לPEM
    private String convertToPem(PKCS10CertificationRequest csr) throws IOException {

        //יוצרת קובץ בזיכרון RAM
        StringWriter sw = new StringWriter();
        //יצירת אובייקט שיכתוב לPEM
        try(PemWriter pemWriter = new PemWriter(sw)) {
            //csr.getEncoded() -  חזיר את הבתים הבינאריים של הCSR
            //אורז את הCSR הבינארי
            PemObject pemObject = new  PemObject("CERTIFICATE REQUEST", csr.getEncoded());
            //לוקח את הבתים מקודד אותם לBASE64 ומוסיפה כותרות
            pemWriter.writeObject(pemObject);
        }

        return sw.toString();
    }
}
