package com.authentisign.desktop.security.KeyStore;

import com.authentisign.desktop.security.strategies.SigningStrategy;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;

//תעודה עצמית
public class CertificateUtils {

    public static X509Certificate generateSelfSignedCertificate(KeyPair pair, SigningStrategy algorithm,
                                                                String commonName, String email) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date expiryDate = new Date(now + 3600 * 1000);   //שעה

        String cn = (commonName == null || commonName.isBlank()) ? "Authentication Certificate" : commonName;
        StringBuilder dn = new StringBuilder("CN=").append(cn);
        if (email != null && !email.isBlank()) {
            dn.append(", E=").append(email);
        }

        dn.append(", OU=DigiSign, O=Software Engineering, C=IL");
        X500Name dnName = new X500Name(dn.toString());
        BigInteger certSerialNumber = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, expiryDate, dnName, pair.getPublic());

        //חתימה על התעוה
        ContentSigner contentSigner = new JcaContentSignerBuilder(algorithm.getAlgorithmName()).build(pair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(contentSigner));
        }
}